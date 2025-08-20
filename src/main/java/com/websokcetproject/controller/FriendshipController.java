package com.websokcetproject.controller;

import com.websokcetproject.model.Friendship;
import com.websokcetproject.model.User;
import com.websokcetproject.repository.FriendshipRepository;
import com.websokcetproject.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.HashMap;

@RestController
@RequestMapping("/api/friends")
@CrossOrigin(origins = "*")
public class FriendshipController {
    
    @Autowired
    private FriendshipRepository friendshipRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    // 친구 요청 보내기
    @PostMapping("/request")
    public ResponseEntity<?> sendFriendRequest(@RequestBody Map<String, String> request) {
        String requesterId = request.get("requesterId");
        String addresseeId = request.get("addresseeId");
        
        if (requesterId.equals(addresseeId)) {
            return ResponseEntity.badRequest().body("자기 자신에게 친구 요청을 보낼 수 없습니다.");
        }
        
        // 이미 친구 관계가 있는지 확인
        Optional<Friendship> existingFriendship = friendshipRepository.findFriendshipBetweenUsers(requesterId, addresseeId);
        if (existingFriendship.isPresent()) {
            Friendship friendship = existingFriendship.get();
            if (friendship.getStatus() == Friendship.FriendshipStatus.ACCEPTED) {
                return ResponseEntity.badRequest().body("이미 친구입니다.");
            } else if (friendship.getStatus() == Friendship.FriendshipStatus.PENDING) {
                return ResponseEntity.badRequest().body("이미 친구 요청을 보냈습니다.");
            }
        }
        
        // 친구 요청 생성
        Friendship friendship = new Friendship();
        friendship.setRequesterId(requesterId);
        friendship.setAddresseeId(addresseeId);
        friendship.setStatus(Friendship.FriendshipStatus.PENDING);
        
        friendshipRepository.save(friendship);
        return ResponseEntity.ok("친구 요청을 보냈습니다.");
    }
    
    // 친구 요청 수락
    @PostMapping("/accept")
    public ResponseEntity<?> acceptFriendRequest(@RequestBody Map<String, String> request) {
        String friendshipId = request.get("friendshipId");
        String userId = request.get("userId");
        
        Optional<Friendship> friendshipOpt = friendshipRepository.findById(Long.parseLong(friendshipId));
        if (friendshipOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Friendship friendship = friendshipOpt.get();
        if (!friendship.getAddresseeId().equals(userId)) {
            return ResponseEntity.badRequest().body("친구 요청을 수락할 권한이 없습니다.");
        }
        
        friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
        
        return ResponseEntity.ok("친구 요청을 수락했습니다.");
    }
    
    // 친구 요청 거절
    @PostMapping("/reject")
    public ResponseEntity<?> rejectFriendRequest(@RequestBody Map<String, String> request) {
        String friendshipId = request.get("friendshipId");
        String userId = request.get("userId");
        
        Optional<Friendship> friendshipOpt = friendshipRepository.findById(Long.parseLong(friendshipId));
        if (friendshipOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Friendship friendship = friendshipOpt.get();
        if (!friendship.getAddresseeId().equals(userId)) {
            return ResponseEntity.badRequest().body("친구 요청을 거절할 권한이 없습니다.");
        }
        
        friendship.setStatus(Friendship.FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
        
        return ResponseEntity.ok("친구 요청을 거절했습니다.");
    }
    
    // 친구 목록 조회
    @GetMapping("/list/{userId}")
    public ResponseEntity<List<User>> getFriendList(@PathVariable String userId) {
        List<String> friendIds = friendshipRepository.findFriendIdsByUserId(userId);
        List<User> friends = userRepository.findByUserIdIn(friendIds);
        return ResponseEntity.ok(friends);
    }
    
    // 대기 중인 친구 요청 조회
    @GetMapping("/pending/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getPendingRequests(@PathVariable String userId) {
        List<Friendship> pendingRequests = friendshipRepository.findPendingRequestsByUserId(userId);
        
        List<Map<String, Object>> requests = pendingRequests.stream()
            .map(friendship -> {
                Optional<User> requester = userRepository.findByUserId(friendship.getRequesterId());
                Map<String, Object> requestMap = new HashMap<>();
                requestMap.put("friendshipId", friendship.getId());
                requestMap.put("requesterId", friendship.getRequesterId());
                requestMap.put("requesterName", requester.map(User::getUsername).orElse("Unknown"));
                requestMap.put("createdAt", friendship.getCreatedAt());
                return requestMap;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(requests);
    }
    
    // 사용자 검색 (친구 추가용)
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String keyword, @RequestParam String currentUserId) {
        List<User> users = userRepository.findByUsernameContainingIgnoreCase(keyword);
        
        // 현재 사용자와 이미 친구인 사용자들을 제외
        List<String> friendIds = friendshipRepository.findFriendIdsByUserId(currentUserId);
        users = users.stream()
            .filter(user -> !user.getUserId().equals(currentUserId) && !friendIds.contains(user.getUserId()))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(users);
    }
} 