package com.websokcetproject.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.websokcetproject.model.ChatMessage;
import com.websokcetproject.model.ChatRoom;
import com.websokcetproject.model.User;
import com.websokcetproject.repository.ChatMessageRepository;
import com.websokcetproject.repository.ChatRoomRepository;
import com.websokcetproject.repository.UserRepository;
import com.websokcetproject.repository.UserLeaveTimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final UserLeaveTimeRepository userLeaveTimeRepository;
    
    // 스레드 안전성을 위한 동기화 객체들
    private final Object sessionsLock = new Object();
    private final Object userSessionsLock = new Object();
    private final Object userRoomSessionsLock = new Object();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 생성자 주입
    public ChatWebSocketHandler(ChatRoomRepository chatRoomRepository, 
                               ChatMessageRepository chatMessageRepository,
                               UserRepository userRepository,
                               UserLeaveTimeRepository userLeaveTimeRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.userLeaveTimeRepository = userLeaveTimeRepository;
        
        // ObjectMapper 설정
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    // 사용자별 WebSocket 세션 관리 (채팅방별로 관리)
    private final Map<String, Map<String, Set<WebSocketSession>>> userRoomSessions = new ConcurrentHashMap<>();
    
    // 사용자별 전체 세션 관리 (여러 브라우저/세션 지원)
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    
    // 세션별 사용자 정보
    private final Map<WebSocketSession, UserInfo> sessionUsers = new ConcurrentHashMap<>();
    
    // 고유 세션 ID 관리
    private final Map<String, String> sessionIdToUserId = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userIdToSessionIds = new ConcurrentHashMap<>();
    
    // 타이핑 상태 관리
    private final Map<String, Set<String>> typingUsers = new ConcurrentHashMap<>();
    
    private static class UserInfo {
        String userId;
        String roomId;
        String sessionId; // 고유 세션 ID 추가
        
        UserInfo(String userId, String roomId, String sessionId) {
            this.userId = userId;
            this.roomId = roomId;
            this.sessionId = sessionId;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserId(session);
        String roomId = getRoomId(session);
        
        if (userId == null || roomId == null) {
            System.err.println("❌ WebSocket 연결 실패: userId 또는 roomId가 null");
            session.close();
            return;
        }
        
        // 고유한 세션 ID 생성
        String uniqueSessionId = UUID.randomUUID().toString();
        
        synchronized (sessionsLock) {
            // 세션 정보 저장
            sessionUsers.put(session, new UserInfo(userId, roomId, uniqueSessionId));
            sessionIdToUserId.put(uniqueSessionId, userId);
            
            // 사용자별 세션 ID 관리
            userIdToSessionIds.computeIfAbsent(userId, k -> new HashSet<>()).add(uniqueSessionId);
        }
        
        synchronized (userSessionsLock) {
            // 사용자별 세션 관리
            userSessions.computeIfAbsent(userId, k -> new HashSet<>()).add(session);
        }
        
        synchronized (userRoomSessionsLock) {
            // 사용자별 채팅방별 세션 관리
            userRoomSessions.computeIfAbsent(userId, k -> new HashMap<>())
                          .computeIfAbsent(roomId, k -> new HashSet<>())
                          .add(session);
        }
        
        System.out.println("✅ WebSocket 연결 성공: " + userId + " (채팅방: " + roomId + ", 세션: " + uniqueSessionId + ")");
        System.out.println("현재 연결된 사용자 수: " + userSessions.size());
        System.out.println("현재 연결된 세션 수: " + sessionUsers.size());
        
        // 사용자 상태를 온라인으로 변경
        updateUserStatus(userId, "online");
        
        // 해당 채팅방의 읽지 않은 메시지 수 전송
        sendAllUnreadCounts(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UserInfo userInfo = sessionUsers.get(session);
        if (userInfo == null) {
            System.err.println("❌ 사용자 정보를 찾을 수 없습니다.");
            return;
        }
        
        String userId = userInfo.userId;
        String roomId = userInfo.roomId;
        String sessionId = userInfo.sessionId;
        
        System.out.println("🔔 === WebSocket 메시지 수신 === 🔔");
        System.out.println("📨 Received message from " + userId + " in room " + roomId);
        System.out.println("🆔 Session ID: " + sessionId);
        System.out.println("📝 Message payload: " + message.getPayload());
        System.out.println("🧵 Current Thread: " + Thread.currentThread().getName());
        
        try {
            Map<String, Object> messageData = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) messageData.get("type");
            
            System.out.println("📋 메시지 타입: " + type);
            System.out.println("👤 사용자 ID: " + userId);
            System.out.println("🚪 채팅방 ID: " + roomId);
            
            switch (type) {
                case "message":
                    System.out.println("💬 메시지 타입 처리 시작: " + type);
                    String messageRoomId = (String) messageData.get("roomId");
                    System.out.println("📨 메시지의 roomId: " + messageRoomId + ", 사용자의 roomId: " + roomId);
                    handleChatMessage(messageRoomId, userId, messageData);
                    System.out.println("✅ 메시지 타입 처리 완료: " + type);
                    break;
                case "join_room":
                    System.out.println("🚪 채팅방 입장 타입 처리 시작: " + type);
                    handleJoinRoom(userId, messageData);
                    System.out.println("✅ 채팅방 입장 타입 처리 완료: " + type);
                    break;
                case "typing":
                    System.out.println("⌨️ 타이핑 타입 처리 시작: " + type);
                    String typingRoomId = (String) messageData.get("roomId");
                    handleTyping(typingRoomId, userId, (Boolean) messageData.get("isTyping"));
                    System.out.println("✅ 타이핑 타입 처리 완료: " + type);
                    break;
                case "read":
                    System.out.println("👁️ 읽음 확인 타입 처리 시작: " + type);
                    String readRoomId = (String) messageData.get("roomId");
                    handleReadReceipt(readRoomId, userId);
                    System.out.println("✅ 읽음 확인 타입 처리 완료: " + type);
                    break;
                case "emoji":
                    System.out.println("😊 이모티콘 타입 처리 시작: " + type);
                    String emojiRoomId = (String) messageData.get("roomId");
                    handleEmojiMessage(emojiRoomId, userId, messageData);
                    System.out.println("✅ 이모티콘 타입 처리 완료: " + type);
                    break;
                default:
                    // 알 수 없는 타입의 메시지는 무시
                    System.out.println("❓ Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("❌ Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UserInfo userInfo = sessionUsers.get(session);
        if (userInfo == null) {
            return;
        }
        
        String userId = userInfo.userId;
        String uniqueSessionId = userInfo.sessionId;
        
        System.out.println("=== WebSocket 연결 종료 ===");
        System.out.println("UserId: " + userId);
        System.out.println("Unique Session ID: " + uniqueSessionId);
        
        synchronized (sessionsLock) {
            // 세션 정보 정리
            sessionUsers.remove(session);
            
            // 고유 세션 ID 매핑 정리
            sessionIdToUserId.remove(uniqueSessionId);
            Set<String> userSessionIds = userIdToSessionIds.get(userId);
            if (userSessionIds != null) {
                userSessionIds.remove(uniqueSessionId);
                if (userSessionIds.isEmpty()) {
                    userIdToSessionIds.remove(userId);
                }
            }
        }
        
        synchronized (userSessionsLock) {
            // 사용자별 전체 세션에서 제거
            Set<WebSocketSession> userSessionsSet = userSessions.get(userId);
            if (userSessionsSet != null) {
                userSessionsSet.remove(session);
                if (userSessionsSet.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        
        synchronized (userRoomSessionsLock) {
            // 사용자 세션 제거
            Map<String, Set<WebSocketSession>> roomSessions = userRoomSessions.get(userId);
            if (roomSessions != null) {
                Set<WebSocketSession> currentRoomSessions = roomSessions.get(userInfo.roomId);
                if (currentRoomSessions != null) {
                    currentRoomSessions.remove(session);
                    if (currentRoomSessions.isEmpty()) {
                        roomSessions.remove(userInfo.roomId);
                    }
                }
            }
        }
        
        // 사용자 상태를 오프라인으로 변경 (모든 세션이 종료된 경우에만)
        if (!userIdToSessionIds.containsKey(userId)) {
            updateUserStatus(userId, "offline");
        }
        
        System.out.println("=== WebSocket 연결 종료 완료 ===");
    }
    
    // 채팅방 입장 처리
    private void handleJoinRoom(String userId, Map<String, Object> messageData) throws Exception {
        String roomId = (String) messageData.get("roomId");
        
        System.out.println("사용자 " + userId + "가 채팅방 " + roomId + "에 입장");
        
        // 채팅방 참가자에게 입장 알림
        sendSystemMessage(roomId, userId + "님이 입장하셨습니다.", "system");
        
        // 채팅방 참가자 목록 업데이트
        broadcastRoomParticipants(roomId);
    }
    
    // 채팅방 참가자 목록 브로드캐스트
    private void broadcastRoomParticipants(String roomId) {
        try {
            // 채팅방 정보 조회
            Optional<ChatRoom> roomOpt = chatRoomRepository.findByRoomId(roomId);
            if (roomOpt.isEmpty()) {
                return;
            }
            
            ChatRoom room = roomOpt.get();
            
            // 실제 참가자 목록 사용 (lazy loading 방지)
            Set<String> participants = new HashSet<>();
            try {
                // Hibernate.initialize()를 사용하여 컬렉션을 명시적으로 로드
                org.hibernate.Hibernate.initialize(room.getParticipants());
                participants.addAll(room.getParticipants());
            } catch (Exception e) {
                System.err.println("❌ Error loading participants: " + e.getMessage());
                // 대안: 채팅방 이름에서 참가자 추출 (사용자 ID 기반)
                String roomName = room.getRoomName();
                if (roomName.contains(" & ")) {
                    String[] userIds = roomName.split(" & ");
                    for (String userId : userIds) {
                        participants.add(userId.trim());
                    }
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("type", "participants");
            response.put("roomId", roomId);
            response.put("participants", new ArrayList<>(participants));
            
            String message = objectMapper.writeValueAsString(response);
            
            // 무한 루프 방지를 위해 직접 메시지 전송
            System.out.println("채팅방 " + roomId + " 참가자들에게 참가자 목록 전송: " + participants);
            for (String participantId : participants) {
                Map<String, Set<WebSocketSession>> roomSessions = userRoomSessions.get(participantId);
                if (roomSessions != null) {
                    Set<WebSocketSession> sessions = roomSessions.get(roomId);
                    if (sessions != null) {
                        for (WebSocketSession session : sessions) {
                            if (session != null && session.isOpen()) {
                                try {
                                    session.sendMessage(new TextMessage(message));
                                    System.out.println("✅ 참가자 목록 전송 완료: " + participantId);
                                } catch (Exception e) {
                                    System.err.println("❌ 참가자 목록 전송 실패: " + participantId + " - " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
            
            System.out.println("채팅방 " + roomId + " 참가자 목록 업데이트: " + participants);
        } catch (Exception e) {
            System.err.println("참가자 목록 브로드캐스트 오류: " + e.getMessage());
        }
    }
    
    private void handleChatMessage(String roomId, String userId, Map<String, Object> messageData) throws Exception {
        String content = (String) messageData.get("content");
        String messageType = (String) messageData.getOrDefault("messageType", "text");
        String fileUrl = (String) messageData.get("fileUrl");
        
        System.out.println("=== 메시지 처리 시작 ===");
        System.out.println("RoomId: " + roomId + ", UserId: " + userId + ", Content: " + content);
        
        // 메시지 저장
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRoomId(roomId);
        chatMessage.setSenderId(userId);
        chatMessage.setSenderName(getUsername(userId));
        chatMessage.setContent(content);
        chatMessage.setMessageType(messageType);
        chatMessage.setFileUrl(fileUrl);
        chatMessage.setSentAt(LocalDateTime.now());
        
        chatMessageRepository.save(chatMessage);
        System.out.println("메시지 저장 완료: " + chatMessage.getId());
        
        // 채팅방 마지막 메시지 업데이트
        updateChatRoomLastMessage(roomId, content, userId);
        
        // 메시지 전송
        Map<String, Object> response = new HashMap<>();
        response.put("type", "message");
        response.put("messageId", chatMessage.getId());
        response.put("senderId", userId);
        response.put("senderName", getUsername(userId));
        response.put("content", content);
        response.put("messageType", messageType);
        response.put("fileUrl", fileUrl);
        response.put("sentAt", chatMessage.getSentAt());
        response.put("roomId", roomId);
        
        // 채팅방의 모든 참가자에게 메시지 전송
        broadcastToRoomParticipants(roomId, objectMapper.writeValueAsString(response));
        System.out.println("채팅방 브로드캐스트 완료");
        
        // 실시간 알림 전송 (카카오톡 스타일)
        System.out.println("실시간 알림 전송 시작...");
        try {
            sendRealTimeNotifications(roomId, userId, chatMessage);
            System.out.println("실시간 알림 전송 완료");
        } catch (Exception e) {
            System.err.println("실시간 알림 전송 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 타이핑 상태 제거
        removeTypingUser(roomId, userId);
        
        System.out.println("=== 메시지 처리 완료 ===");
    }
    
    // 채팅방의 모든 참가자에게 메시지 전송
    private void broadcastToRoomParticipants(String roomId, String message) {
        try {
            // 채팅방 정보 조회
            Optional<ChatRoom> roomOpt = chatRoomRepository.findByRoomId(roomId);
            if (roomOpt.isEmpty()) {
                System.err.println("채팅방을 찾을 수 없음: " + roomId);
                return;
            }
            
            ChatRoom room = roomOpt.get();
            
            // 실제 참가자 목록 사용 (lazy loading 방지)
            Set<String> participants = new HashSet<>();
            try {
                // Hibernate.initialize()를 사용하여 컬렉션을 명시적으로 로드
                org.hibernate.Hibernate.initialize(room.getParticipants());
                participants.addAll(room.getParticipants());
            } catch (Exception e) {
                System.err.println("❌ Error loading participants: " + e.getMessage());
                // 대안: 채팅방 이름에서 참가자 추출 (사용자 ID 기반)
                String roomName = room.getRoomName();
                if (roomName.contains(" & ")) {
                    String[] userIds = roomName.split(" & ");
                    for (String userId : userIds) {
                        participants.add(userId.trim());
                    }
                }
            }
            
            System.out.println("채팅방 " + roomId + " 참가자들에게 메시지 전송: " + participants);
            
            synchronized (userRoomSessionsLock) {
                System.out.println("현재 연결된 사용자 세션들: " + userRoomSessions.keySet());
                
                // 모든 참가자에게 메시지 전송 (채팅방별로 정확히)
                for (String participantId : participants) {
                    try {
                        Map<String, Set<WebSocketSession>> roomSessions = userRoomSessions.get(participantId);
                        Set<String> userSessionIds = userIdToSessionIds.get(participantId);
                        System.out.println("참가자 " + participantId + "의 세션 수: " + (roomSessions != null ? roomSessions.size() : 0) + ", 고유 세션 ID 수: " + (userSessionIds != null ? userSessionIds.size() : 0));
                        
                        if (roomSessions != null) {
                            Set<WebSocketSession> sessions = roomSessions.get(roomId);
                            if (sessions != null && !sessions.isEmpty()) {
                                for (WebSocketSession session : sessions) {
                                    if (session != null && session.isOpen()) {
                                        try {
                                            session.sendMessage(new TextMessage(message));
                                            System.out.println("✅ 메시지 전송 완료: " + participantId + " (채팅방: " + roomId + ")");
                                        } catch (Exception e) {
                                            System.err.println("❌ 메시지 전송 실패: " + participantId + " - " + e.getMessage());
                                            // 세션이 닫혔을 수 있으므로 정리
                                            sessions.remove(session);
                                        }
                                    } else {
                                        // 닫힌 세션 제거
                                        sessions.remove(session);
                                    }
                                }
                            } else {
                                System.out.println("⏭️ 사용자 " + participantId + "가 채팅방 " + roomId + "에 연결되지 않음");
                            }
                        } else {
                            System.out.println("⏭️ 사용자 " + participantId + "의 세션 정보 없음");
                        }
                    } catch (Exception e) {
                        System.err.println("❌ 참가자 " + participantId + " 처리 중 오류: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            System.out.println("=== 채팅방 브로드캐스트 완료 ===");
            
        } catch (Exception e) {
            System.err.println("❌ 브로드캐스트 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 실시간 알림 전송 (카카오톡 스타일)
    @Transactional
    private void sendRealTimeNotifications(String roomId, String senderId, ChatMessage message) {
        try {
            // 채팅방 정보 조회 (트랜잭션 내에서)
            Optional<ChatRoom> roomOpt = chatRoomRepository.findByRoomId(roomId);
            if (!roomOpt.isPresent()) {
                System.err.println("❌ Room not found: " + roomId);
                return;
            }
            
            ChatRoom room = roomOpt.get();
            
            // 참가자 목록 수집 (트랜잭션 내에서)
            Set<String> participants = new HashSet<>();
            
            // 실제 참가자 목록 사용 (lazy loading 방지)
            try {
                // Hibernate.initialize()를 사용하여 컬렉션을 명시적으로 로드
                org.hibernate.Hibernate.initialize(room.getParticipants());
                participants.addAll(room.getParticipants());
            } catch (Exception e) {
                System.err.println("❌ Error loading participants: " + e.getMessage());
                // 대안: 채팅방 이름에서 참가자 추출 (사용자 ID 기반)
                String roomName = room.getRoomName();
                if (roomName.contains(" & ")) {
                    String[] userIds = roomName.split(" & ");
                    for (String userId : userIds) {
                        participants.add(userId.trim());
                    }
                }
            }
            
            System.out.println("Room " + roomId + " participants: " + participants);
            
            // 현재 WebSocket에 연결된 사용자들 (이제 모든 사용자가 연결되어 있음)
            Set<String> connectedUsers = new HashSet<>();
            for (String participantId : participants) {
                Map<String, Set<WebSocketSession>> roomSessions = userRoomSessions.get(participantId);
                if (roomSessions != null && !roomSessions.isEmpty()) {
                    Set<WebSocketSession> sessions = roomSessions.get(roomId);
                    if (sessions != null) {
                        for (WebSocketSession session : sessions) {
                            if (session != null && session.isOpen()) {
                                connectedUsers.add(participantId);
                                break; // 하나라도 연결되어 있으면 연결된 것으로 간주
                            }
                        }
                    }
                }
            }
            
            System.out.println("Connected users: " + connectedUsers);
            System.out.println("Total user sessions: " + userRoomSessions.size());
            System.out.println("User sessions keys: " + userRoomSessions.keySet());
            
            // 채팅방의 모든 참가자에게 알림 및 업데이트 전송
            for (String participantId : participants) {
                if (!participantId.equals(senderId)) {
                    Map<String, Set<WebSocketSession>> roomSessions = userRoomSessions.get(participantId);
                    System.out.println("Checking participant: " + participantId + ", Sessions: " + (roomSessions != null ? roomSessions.size() : 0));
                    
                    if (roomSessions != null && !roomSessions.isEmpty()) {
                        for (WebSocketSession userSession : roomSessions.get(roomId)) {
                            if (userSession != null && userSession.isOpen()) {
                                // 실시간 알림 메시지 (현재 채팅방에 있지 않은 사용자에게만)
                                if (!connectedUsers.contains(participantId)) {
                                    try {
                                        Map<String, Object> notification = new HashMap<>();
                                        notification.put("type", "notification");
                                        notification.put("roomId", roomId);
                                        notification.put("roomName", room.getRoomName());
                                        notification.put("senderName", message.getSenderName());
                                        notification.put("content", message.getContent());
                                        notification.put("sentAt", message.getSentAt());
                                        notification.put("unreadCount", 1);
                                        
                                        String notificationJson = objectMapper.writeValueAsString(notification);
                                        userSession.sendMessage(new TextMessage(notificationJson));
                                        
                                        System.out.println("✅ Real-time notification sent to " + participantId + " for room " + roomId);
                                        System.out.println("Notification content: " + notificationJson);
                                        
                                    } catch (Exception e) {
                                        System.err.println("❌ Error sending notification to " + participantId + ": " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                } else {
                                    System.out.println("⏭️ Skipping notification for " + participantId + " (already in room)");
                                }
                                
                                // 모든 참가자에게 채팅방 목록 갱신 이벤트 전송
                                try {
                                    Map<String, Object> roomUpdate = new HashMap<>();
                                    roomUpdate.put("type", "roomUpdate");
                                    roomUpdate.put("action", "messageReceived");
                                    roomUpdate.put("roomId", roomId);
                                    roomUpdate.put("roomName", room.getRoomName());
                                    
                                    String roomUpdateJson = objectMapper.writeValueAsString(roomUpdate);
                                    userSession.sendMessage(new TextMessage(roomUpdateJson));
                                    
                                    System.out.println("✅ Room update event sent to " + participantId);
                                    
                                } catch (Exception e) {
                                    System.err.println("❌ Error sending room update to " + participantId + ": " + e.getMessage());
                                }
                            } else {
                                System.out.println("⏭️ Skipping notification for " + participantId + " (no active session)");
                            }
                        }
                    } else {
                        System.out.println("⏭️ Skipping notification for " + participantId + " (no active session)");
                    }
                }
            }
            
            System.out.println("=== 실시간 알림 전송 완료 ===");
            
        } catch (Exception e) {
            System.err.println("❌ Error in sendRealTimeNotifications: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleTyping(String roomId, String userId, boolean isTyping) throws Exception {
        if (isTyping) {
            typingUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        } else {
            removeTypingUser(roomId, userId);
        }
        
        // 타이핑 상태 브로드캐스트
        Map<String, Object> response = new HashMap<>();
        response.put("type", "typing");
        response.put("userId", userId);
        response.put("username", getUsername(userId));
        response.put("isTyping", isTyping);
        
        broadcastToRoomParticipants(roomId, objectMapper.writeValueAsString(response));
    }
    
    private void handleReadReceipt(String roomId, String userId) throws Exception {
        // 읽지 않은 메시지들을 읽음 처리
        List<ChatMessage> unreadMessages = chatMessageRepository.findUnreadMessages(roomId, userId);
        for (ChatMessage message : unreadMessages) {
            message.setRead(true);
            message.setReadAt(LocalDateTime.now());
            chatMessageRepository.save(message);
        }
        
        // 읽음 확인 브로드캐스트
        Map<String, Object> response = new HashMap<>();
        response.put("type", "read");
        response.put("userId", userId);
        response.put("roomId", roomId);
        
        broadcastToRoomParticipants(roomId, objectMapper.writeValueAsString(response));
    }
    
    private void handleEmojiMessage(String roomId, String userId, Map<String, Object> messageData) throws Exception {
        String emoji = (String) messageData.get("emoji");
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "emoji");
        response.put("senderId", userId);
        response.put("senderName", getUsername(userId));
        response.put("emoji", emoji);
        response.put("sentAt", LocalDateTime.now());
        
        broadcastToRoomParticipants(roomId, objectMapper.writeValueAsString(response));
    }
    
    private void handleSimpleTextMessage(String roomId, String userId, String content) throws Exception {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("content", content);
        messageData.put("messageType", "text");
        handleChatMessage(roomId, userId, messageData);
    }
    
    private void sendSystemMessage(String roomId, String content, String type) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("content", content);
            response.put("sentAt", LocalDateTime.now());
            
            broadcastToRoomParticipants(roomId, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void sendAllUnreadCounts(String userId) {
        try {
            List<ChatRoom> rooms = chatRoomRepository.findAll();
            for (ChatRoom room : rooms) {
                long unreadCount = chatMessageRepository.countUnreadMessages(room.getRoomId(), userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("type", "unreadCount");
                response.put("count", unreadCount);
                response.put("roomId", room.getRoomId());
                
                Map<String, Set<WebSocketSession>> roomSessions = userRoomSessions.get(userId);
                if (roomSessions != null) {
                    Set<WebSocketSession> sessions = roomSessions.get(room.getRoomId());
                    if (sessions != null) {
                        for (WebSocketSession session : sessions) {
                            if (session != null && session.isOpen()) {
                                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void removeTypingUser(String roomId, String userId) {
        Set<String> typing = typingUsers.get(roomId);
        if (typing != null) {
            typing.remove(userId);
        }
    }
    
    private void updateUserStatus(String userId, String status) {
        try {
            Optional<User> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setStatus(status);
                if ("offline".equals(status)) {
                    user.setLastSeen(LocalDateTime.now());
                }
                userRepository.save(user);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void updateChatRoomLastMessage(String roomId, String lastMessage, String senderId) {
        try {
            Optional<ChatRoom> roomOpt = chatRoomRepository.findByRoomId(roomId);
            if (roomOpt.isPresent()) {
                ChatRoom room = roomOpt.get();
                room.setLastMessage(lastMessage);
                room.setLastMessageTime(LocalDateTime.now());
                room.setLastMessageSender(getUsername(senderId));
                chatRoomRepository.save(room);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String getUsername(String userId) {
        try {
            Optional<User> userOpt = userRepository.findByUserId(userId);
            return userOpt.map(User::getUsername).orElse(userId);
        } catch (Exception e) {
            return userId;
        }
    }
    
    private String getUserId(WebSocketSession session) {
        try {
            String uri = session.getUri().toString();
            System.out.println("🔍 WebSocket URI: " + uri);
            
            if (uri.contains("?")) {
                String[] params = uri.split("\\?")[1].split("&");
                for (String param : params) {
                    if (param.startsWith("userId=")) {
                        String userId = param.split("=")[1];
                        // URL 디코딩
                        try {
                            userId = java.net.URLDecoder.decode(userId, "UTF-8");
                        } catch (Exception e) {
                            System.err.println("URL 디코딩 실패: " + e.getMessage());
                        }
                        System.out.println("✅ WebSocket에서 사용자 ID 추출: " + userId);
                        return userId;
                    }
                }
            }
            System.err.println("❌ WebSocket URL에서 userId 파라미터를 찾을 수 없음: " + uri);
            return "anonymous";
        } catch (Exception e) {
            System.err.println("❌ getUserId 오류: " + e.getMessage());
            return "anonymous";
        }
    }
    
    private String getRoomId(WebSocketSession session) {
        try {
            String uri = session.getUri().toString();
            System.out.println("🔍 WebSocket URI for roomId: " + uri);
            
            if (uri.contains("?")) {
                String[] params = uri.split("\\?")[1].split("&");
                for (String param : params) {
                    if (param.startsWith("roomId=")) {
                        String roomId = param.split("=")[1];
                        // URL 디코딩
                        try {
                            roomId = java.net.URLDecoder.decode(roomId, "UTF-8");
                        } catch (Exception e) {
                            System.err.println("URL 디코딩 실패: " + e.getMessage());
                        }
                        System.out.println("✅ WebSocket에서 roomId 추출: " + roomId);
                        return roomId;
                    }
                }
            }
            System.err.println("❌ WebSocket URL에서 roomId 파라미터를 찾을 수 없음: " + uri);
            return null;
        } catch (Exception e) {
            System.err.println("❌ getRoomId 오류: " + e.getMessage());
            return null;
        }
    }
}
