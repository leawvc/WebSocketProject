package com.websokcetproject.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "friendships")
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String requesterId; // 친구 요청한 사용자
    
    @Column(nullable = false)
    private String addresseeId; // 친구 요청 받은 사용자
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status; // PENDING, ACCEPTED, REJECTED
    
    @Column
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime updatedAt;
    
    public enum FriendshipStatus {
        PENDING,    // 대기 중
        ACCEPTED,   // 수락됨
        REJECTED    // 거절됨
    }
    
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
    
    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }
    
    public String getAddresseeId() { return addresseeId; }
    public void setAddresseeId(String addresseeId) { this.addresseeId = addresseeId; }
    
    public FriendshipStatus getStatus() { return status; }
    public void setStatus(FriendshipStatus status) { this.status = status; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
} 