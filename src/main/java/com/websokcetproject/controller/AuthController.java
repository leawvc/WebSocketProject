package com.websokcetproject.controller;

import com.websokcetproject.model.User;
import com.websokcetproject.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    
    @Autowired
    private UserRepository userRepository;
    
    // 회원가입
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request, HttpSession session) {
        String userId = request.get("userId");
        String username = request.get("username");
        
        // 입력 검증
        if (userId == null || userId.trim().isEmpty() || username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("사용자 ID와 이름을 모두 입력해주세요.");
        }
        
        // 이미 존재하는 사용자인지 확인
        if (userRepository.existsByUserId(userId)) {
            return ResponseEntity.badRequest().body("이미 존재하는 사용자 ID입니다.");
        }
        
        // 새 사용자 생성
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setStatus("online");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        
        // 세션에 사용자 정보 저장
        session.setAttribute("userId", savedUser.getUserId());
        session.setAttribute("username", savedUser.getUsername());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "회원가입이 완료되었습니다.");
        response.put("user", savedUser);
        
        return ResponseEntity.ok(response);
    }
    
    // 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request, HttpSession session) {
        String userId = request.get("userId");
        String username = request.get("username");
        
        // 입력 검증
        if (userId == null || userId.trim().isEmpty() || username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("사용자 ID와 이름을 모두 입력해주세요.");
        }
        
        // 사용자 존재 여부 확인
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("존재하지 않는 사용자입니다. 회원가입을 먼저 해주세요.");
        }
        
        User user = userOpt.get();
        
        // 사용자명 일치 확인
        if (!user.getUsername().equals(username)) {
            return ResponseEntity.badRequest().body("사용자명이 일치하지 않습니다.");
        }
        
        // 온라인 상태로 업데이트
        user.setStatus("online");
        user.setLastSeen(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        // 세션에 사용자 정보 저장
        session.setAttribute("userId", user.getUserId());
        session.setAttribute("username", user.getUsername());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "로그인되었습니다.");
        response.put("user", user);
        
        return ResponseEntity.ok(response);
    }
    
    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestBody(required = false) Map<String, String> request, HttpSession session) {
        // 세션에서 사용자 정보 가져오기
        String userId = (String) session.getAttribute("userId");
        
        if (userId != null) {
            // 사용자 상태를 offline으로 업데이트
            Optional<User> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setStatus("offline");
                user.setLastSeen(LocalDateTime.now());
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.save(user);
            }
            
            // 세션 무효화 (이미 무효화된 경우 예외 처리)
            try {
                session.invalidate();
            } catch (IllegalStateException e) {
                // 세션이 이미 무효화된 경우 무시
                System.out.println("Session already invalidated: " + e.getMessage());
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "로그아웃되었습니다.");
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/cleanup-duplicates")
    public ResponseEntity<Map<String, Object>> cleanupDuplicateUsers() {
        Map<String, Object> response = new HashMap<>();
        try {
            // 모든 사용자 조회
            List<User> allUsers = userRepository.findAll();
            Map<String, List<User>> usersByUsername = new HashMap<>();
            
            // 사용자명별로 그룹화
            for (User user : allUsers) {
                usersByUsername.computeIfAbsent(user.getUsername(), k -> new ArrayList<>()).add(user);
            }
            
            int deletedCount = 0;
            
            // 중복된 사용자명 처리
            for (Map.Entry<String, List<User>> entry : usersByUsername.entrySet()) {
                String username = entry.getKey();
                List<User> users = entry.getValue();
                
                if (users.size() > 1) {
                    // 첫 번째 사용자만 유지하고 나머지는 삭제
                    for (int i = 1; i < users.size(); i++) {
                        userRepository.delete(users.get(i));
                        deletedCount++;
                    }
                    System.out.println("Deleted " + (users.size() - 1) + " duplicate users for username: " + username);
                }
            }
            
            response.put("success", true);
            response.put("message", "중복 사용자 정리 완료. " + deletedCount + "개 사용자 삭제됨.");
            response.put("deletedCount", deletedCount);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "중복 사용자 정리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(response);
    }
    
    // 현재 로그인 상태 확인
    @GetMapping("/status")
    public ResponseEntity<?> checkStatus(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");
        
        if (userId == null) {
            return ResponseEntity.ok(Map.of("loggedIn", false));
        }
        
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            session.invalidate();
            return ResponseEntity.ok(Map.of("loggedIn", false));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("loggedIn", true);
        response.put("user", userOpt.get());
        
        return ResponseEntity.ok(response);
    }
} 