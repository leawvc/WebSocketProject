package com.websokcetproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "chat_rooms")
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String roomId;
    
    @Column(nullable = false)
    private String roomName;
    
    @Column(length = 1000)
    private String lastMessage;
    
    @Column
    private LocalDateTime lastMessageTime;
    
    @Column
    private String lastMessageSender;
    
    @ElementCollection
    @CollectionTable(name = "chat_room_participants", 
                     joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "user_id")
    private Set<String> participants = new HashSet<>();
    
    @Column
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    
    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(LocalDateTime lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    
    public String getLastMessageSender() { return lastMessageSender; }
    public void setLastMessageSender(String lastMessageSender) { this.lastMessageSender = lastMessageSender; }
    
    public Set<String> getParticipants() { return participants; }
    public void setParticipants(Set<String> participants) { this.participants = participants; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
