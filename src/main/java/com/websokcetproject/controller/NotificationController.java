package com.websokcetproject.controller;

import com.websokcetproject.model.ChatMessage;
import com.websokcetproject.model.User;
import com.websokcetproject.repository.ChatMessageRepository;
import com.websokcetproject.repository.ChatRoomRepository;
import com.websokcetproject.repository.UserRepository;
import com.websokcetproject.repository.UserLeaveTimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserLeaveTimeRepository userLeaveTimeRepository;
    
    // 사용자의 모든 읽지 않은 메시지 수 조회
    @GetMapping("/unread-count/{userId}")
    public ResponseEntity<Map<String, Object>> getUnreadCount(@PathVariable String userId) {
        try {
            // 사용자가 참가 중인 모든 채팅방
            List<String> roomIds = chatRoomRepository.findRoomIdsByParticipant(userId);
            
            // 각 채팅방별 읽지 않은 메시지 수
            Map<String, Long> roomUnreadCounts = new HashMap<>();
            long totalUnreadCount = 0;
            
            for (String roomId : roomIds) {
                long unreadCount = chatMessageRepository.countUnreadMessages(roomId, userId);
                if (unreadCount > 0) {
                    roomUnreadCounts.put(roomId, unreadCount);
                    totalUnreadCount += unreadCount;
                }
            }
            
            // 나간 채팅방 중에 새 메시지가 있는 방들
            List<Map<String, Object>> leftRoomsWithMessages = findLeftRoomsWithMessages(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalUnreadCount", totalUnreadCount);
            response.put("roomUnreadCounts", roomUnreadCounts);
            response.put("leftRoomsWithMessages", leftRoomsWithMessages);
            response.put("hasNewMessagesInLeftRooms", !leftRoomsWithMessages.isEmpty());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error getting unread count: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // 나간 채팅방 중에 새 메시지가 있는 방들 조회
    @GetMapping("/left-rooms/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getLeftRoomsWithMessages(@PathVariable String userId) {
        List<Map<String, Object>> leftRooms = findLeftRoomsWithMessages(userId);
        return ResponseEntity.ok(leftRooms);
    }
    
    // 나간 채팅방 중에 새 메시지가 있는 방들 찾기 (내부 메서드)
    private List<Map<String, Object>> findLeftRoomsWithMessages(String userId) {
        List<Map<String, Object>> leftRooms = new ArrayList<>();
        
        try {
            // 사용자가 나간 모든 채팅방
            List<com.websokcetproject.model.UserLeaveTime> leaveTimes = userLeaveTimeRepository.findByUserId(userId);
            
            for (com.websokcetproject.model.UserLeaveTime leaveTime : leaveTimes) {
                String roomId = leaveTime.getRoomId();
                LocalDateTime leftAt = leaveTime.getLeftAt();
                
                // 나간 시점 이후의 메시지가 있는지 확인
                List<ChatMessage> newMessages = chatMessageRepository.findMessagesAfterTime(roomId, leftAt);
                
                if (!newMessages.isEmpty()) {
                    // 채팅방 정보 조회
                    Optional<com.websokcetproject.model.ChatRoom> roomOpt = chatRoomRepository.findByRoomId(roomId);
                    if (roomOpt.isPresent()) {
                        com.websokcetproject.model.ChatRoom room = roomOpt.get();
                        
                        // 마지막 메시지 정보
                        ChatMessage lastMessage = newMessages.get(newMessages.size() - 1);
                        
                        Map<String, Object> roomInfo = new HashMap<>();
                        roomInfo.put("roomId", roomId);
                        roomInfo.put("roomName", room.getRoomName());
                        roomInfo.put("newMessageCount", newMessages.size());
                        roomInfo.put("lastMessage", lastMessage.getContent());
                        roomInfo.put("lastMessageSender", lastMessage.getSenderName());
                        roomInfo.put("lastMessageTime", lastMessage.getSentAt());
                        roomInfo.put("leftAt", leftAt);
                        
                        leftRooms.add(roomInfo);
                    }
                }
            }
            
            // 최신 메시지 순으로 정렬
            leftRooms.sort((a, b) -> {
                LocalDateTime timeA = (LocalDateTime) a.get("lastMessageTime");
                LocalDateTime timeB = (LocalDateTime) b.get("lastMessageTime");
                return timeB.compareTo(timeA);
            });
            
        } catch (Exception e) {
            System.err.println("Error in findLeftRoomsWithMessages: " + e.getMessage());
        }
        
        return leftRooms;
    }
    
    // 특정 채팅방의 읽지 않은 메시지 수 조회
    @GetMapping("/rooms/{roomId}/unread-count/{userId}")
    public ResponseEntity<Map<String, Object>> getRoomUnreadCount(
            @PathVariable String roomId, 
            @PathVariable String userId) {
        
        try {
            long unreadCount = chatMessageRepository.countUnreadMessages(roomId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("roomId", roomId);
            response.put("unreadCount", unreadCount);
            response.put("hasUnread", unreadCount > 0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error getting room unread count: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // 모든 메시지를 읽음 처리
    @PostMapping("/mark-all-read/{userId}")
    public ResponseEntity<?> markAllAsRead(@PathVariable String userId) {
        try {
            // 사용자가 참가 중인 모든 채팅방
            List<String> roomIds = chatRoomRepository.findRoomIdsByParticipant(userId);
            
            for (String roomId : roomIds) {
                List<ChatMessage> unreadMessages = chatMessageRepository.findUnreadMessages(roomId, userId);
                for (ChatMessage message : unreadMessages) {
                    message.setRead(true);
                    message.setReadAt(LocalDateTime.now());
                    chatMessageRepository.save(message);
                }
            }
            
            return ResponseEntity.ok("모든 메시지를 읽음 처리했습니다.");
            
        } catch (Exception e) {
            System.err.println("Error marking all as read: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
} 