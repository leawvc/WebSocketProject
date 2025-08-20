package com.websokcetproject.controller;

import com.websokcetproject.model.ChatMessage;
import com.websokcetproject.model.ChatRoom;
import com.websokcetproject.model.User;
import com.websokcetproject.model.UserLeaveTime;
import com.websokcetproject.repository.ChatMessageRepository;
import com.websokcetproject.repository.ChatRoomRepository;
import com.websokcetproject.repository.UserRepository;
import com.websokcetproject.repository.UserLeaveTimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {
    
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @Autowired
    private UserLeaveTimeRepository userLeaveTimeRepository;
    
    // 채팅방 목록 조회
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> getRooms(@RequestParam String userId) {
        List<ChatRoom> rooms = chatRoomRepository.findRoomsByParticipant(userId);
        
        // 나간 채팅방 중에 새 메시지가 있는 방들 찾기
        List<ChatRoom> roomsWithNewMessages = findRoomsWithNewMessages(userId);
        
        // 중복 제거하면서 새 메시지가 있는 방들을 앞에 추가
        Set<String> existingRoomIds = rooms.stream()
            .map(ChatRoom::getRoomId)
            .collect(java.util.stream.Collectors.toSet());
        
        for (ChatRoom room : roomsWithNewMessages) {
            if (!existingRoomIds.contains(room.getRoomId())) {
                rooms.add(0, room); // 맨 앞에 추가
                existingRoomIds.add(room.getRoomId());
            }
        }
        
        return ResponseEntity.ok(rooms);
    }
    
    // 나간 채팅방 중에 새 메시지가 있는 방들 찾기
    private List<ChatRoom> findRoomsWithNewMessages(String userId) {
        List<ChatRoom> roomsWithNewMessages = new ArrayList<>();
        
        try {
            // 사용자가 나간 모든 채팅방 찾기
            List<UserLeaveTime> leaveTimes = userLeaveTimeRepository.findByUserId(userId);
            
            for (UserLeaveTime leaveTime : leaveTimes) {
                String roomId = leaveTime.getRoomId();
                LocalDateTime leftAt = leaveTime.getLeftAt();
                
                // 나간 시점 이후의 메시지가 있는지 확인
                List<ChatMessage> newMessages = chatMessageRepository.findMessagesAfterTime(roomId, leftAt);
                
                if (!newMessages.isEmpty()) {
                    // 채팅방 정보 조회
                    Optional<ChatRoom> roomOpt = chatRoomRepository.findByRoomId(roomId);
                    if (roomOpt.isPresent()) {
                        ChatRoom room = roomOpt.get();
                        
                        // 자동 재참가 로직 제거 - 사용자가 명시적으로 재참가할 때만 추가
                        // room.getParticipants().add(userId);
                        // chatRoomRepository.save(room);
                        
                        // 나간 시점 기록은 유지 (사용자가 직접 재참가할 때까지)
                        // userLeaveTimeRepository.delete(leaveTime);
                        
                        roomsWithNewMessages.add(room);
                        
                        System.out.println("User " + userId + " has new messages in room " + roomId + " (not auto-rejoining)");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in findRoomsWithNewMessages: " + e.getMessage());
        }
        
        return roomsWithNewMessages;
    }
    
    // 채팅방 생성
    @PostMapping("/rooms")
    public ResponseEntity<ChatRoom> createRoom(@RequestBody Map<String, Object> request) {
        String roomName = (String) request.get("roomName");
        @SuppressWarnings("unchecked")
        List<String> participants = (List<String>) request.get("participants");
        
        ChatRoom room = new ChatRoom();
        room.setRoomId(UUID.randomUUID().toString());
        room.setRoomName(roomName);
        room.setParticipants(new HashSet<>(participants));
        
        ChatRoom savedRoom = chatRoomRepository.save(room);
        return ResponseEntity.ok(savedRoom);
    }
    
    // 친구와의 1:1 채팅방 생성 또는 기존 방 찾기
    @PostMapping("/rooms/direct")
    @Transactional
    public ResponseEntity<ChatRoom> createDirectRoom(@RequestBody Map<String, String> request) {
        String userId1 = request.get("userId1");
        String userId2 = request.get("userId2");
        
        // 기존 1:1 채팅방이 있는지 확인
        List<ChatRoom> existingRooms = chatRoomRepository.findRoomsByParticipant(userId1);
        for (ChatRoom room : existingRooms) {
            if (room.getParticipants().size() == 2 && 
                room.getParticipants().contains(userId1) && 
                room.getParticipants().contains(userId2)) {
                return ResponseEntity.ok(room);
            }
        }
        
        // 새 1:1 채팅방 생성
        Optional<User> user1 = userRepository.findByUserId(userId1);
        Optional<User> user2 = userRepository.findByUserId(userId2);
        
        if (user1.isEmpty() || user2.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        ChatRoom room = new ChatRoom();
        room.setRoomId(UUID.randomUUID().toString());
        // 사용자 ID 기반으로 고유한 방 이름 생성 (닉네임 중복 방지)
        room.setRoomName(userId1 + " & " + userId2);
        room.setParticipants(new HashSet<>(Arrays.asList(userId1, userId2)));
        
        ChatRoom savedRoom = chatRoomRepository.save(room);
        return ResponseEntity.ok(savedRoom);
    }
    
    // 채팅방 재참가 (나간 사용자가 메시지를 받을 때 자동 호출)
    @PostMapping("/rooms/{roomId}/rejoin")
    public ResponseEntity<ChatRoom> rejoinRoom(@PathVariable String roomId, @RequestParam String userId) {
        Optional<ChatRoom> roomOpt = chatRoomRepository.findByRoomId(roomId);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ChatRoom room = roomOpt.get();
        
        // 이미 참가 중인지 확인
        if (room.getParticipants().contains(userId)) {
            return ResponseEntity.ok(room);
        }
        
        // 참가자 목록에 다시 추가
        room.getParticipants().add(userId);
        ChatRoom savedRoom = chatRoomRepository.save(room);
        
        return ResponseEntity.ok(savedRoom);
    }
    
    // 채팅방 나가기
    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomId, @RequestParam String userId) {
        Optional<ChatRoom> roomOpt = chatRoomRepository.findByRoomId(roomId);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ChatRoom room = roomOpt.get();
        if (!room.getParticipants().contains(userId)) {
            return ResponseEntity.badRequest().body("채팅방에 참가하지 않은 사용자입니다.");
        }
        
        // 사용자가 나간 시점 기록
        UserLeaveTime leaveTime = new UserLeaveTime();
        leaveTime.setUserId(userId);
        leaveTime.setRoomId(roomId);
        leaveTime.setLeftAt(LocalDateTime.now());
        userLeaveTimeRepository.save(leaveTime);
        
        // 참가자 목록에서 제거
        room.getParticipants().remove(userId);
        
        // 마지막 참가자가 나가면 채팅방 삭제
        if (room.getParticipants().isEmpty()) {
            chatRoomRepository.delete(room);
            return ResponseEntity.ok("채팅방이 삭제되었습니다.");
        }
        
        chatRoomRepository.save(room);
        return ResponseEntity.ok("채팅방을 나갔습니다.");
    }
    
    // 채팅방 메시지 조회 (나간 시점 이후의 메시지만)
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Page<ChatMessage>> getMessages(
            @PathVariable String roomId,
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        // 사용자가 나간 시점 조회
        Optional<UserLeaveTime> leaveTimeOpt = userLeaveTimeRepository.findLatestByUserIdAndRoomId(userId, roomId);
        LocalDateTime sinceTime = leaveTimeOpt.map(UserLeaveTime::getLeftAt).orElse(LocalDateTime.MIN);
        
        Pageable pageable = PageRequest.of(page, size);
        
        // 나간 시점 이후의 메시지만 조회
        if (sinceTime.equals(LocalDateTime.MIN)) {
            // 나간 기록이 없으면 모든 메시지 조회
            Page<ChatMessage> messages = chatMessageRepository.findMessagesByRoomId(roomId, pageable);
            return ResponseEntity.ok(messages);
        } else {
            // 나간 시점 이후의 메시지만 조회
            List<ChatMessage> messages = chatMessageRepository.findMessagesFromTime(roomId, sinceTime);
            
            // 페이징 처리
            int start = page * size;
            int end = Math.min(start + size, messages.size());
            
            if (start >= messages.size()) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
            
            List<ChatMessage> pagedMessages = messages.subList(start, end);
            
            // Page 객체 생성
            Page<ChatMessage> messagePage = new org.springframework.data.domain.PageImpl<>(
                pagedMessages, 
                pageable, 
                messages.size()
            );
            
            return ResponseEntity.ok(messagePage);
        }
    }
    
    // 사용자 조회
    @GetMapping("/users/{userId}")
    public ResponseEntity<User> getUser(@PathVariable String userId) {
        Optional<User> user = userRepository.findByUserId(userId);
        return user.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    // 채팅방 검색
    @GetMapping("/rooms/search")
    public ResponseEntity<List<ChatRoom>> searchRooms(@RequestParam String keyword) {
        List<ChatRoom> rooms = chatRoomRepository.searchRooms(keyword);
        return ResponseEntity.ok(rooms);
    }
    
    // 읽지 않은 메시지 수 조회
    @GetMapping("/rooms/{roomId}/unread")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @PathVariable String roomId,
            @RequestParam String userId) {
        
        long count = chatMessageRepository.countUnreadMessages(roomId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", count);
        return ResponseEntity.ok(response);
    }
    
    // 메시지 읽음 처리
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String roomId,
            @RequestParam String userId) {
        
        List<ChatMessage> unreadMessages = chatMessageRepository.findUnreadMessages(roomId, userId);
        for (ChatMessage message : unreadMessages) {
            message.setRead(true);
            message.setReadAt(LocalDateTime.now());
            chatMessageRepository.save(message);
        }
        
        return ResponseEntity.ok().build();
    }
    
    // 모든 사용자 목록 조회
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }
    
    // 사용자 생성 (로그인)
    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String username = request.get("username");
        
        if (userId == null || username == null) {
            return ResponseEntity.badRequest().build();
        }
        
        // 기존 사용자가 있는지 확인
        Optional<User> existingUser = userRepository.findByUserId(userId);
        if (existingUser.isPresent()) {
            // 기존 사용자 정보 업데이트
            User user = existingUser.get();
            user.setUsername(username);
            user.setStatus("online");
            user.setLastSeen(LocalDateTime.now());
            User savedUser = userRepository.save(user);
            return ResponseEntity.ok(savedUser);
        }
        
        // 새 사용자 생성
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setStatus("online");
        user.setLastSeen(LocalDateTime.now());
        
        User savedUser = userRepository.save(user);
        return ResponseEntity.ok(savedUser);
    }
} 