package com.websokcetproject.repository;

import com.websokcetproject.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.roomId = :roomId ORDER BY cm.sentAt DESC")
    Page<ChatMessage> findMessagesByRoomId(@Param("roomId") String roomId, Pageable pageable);
    
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.roomId = :roomId AND cm.sentAt > :since ORDER BY cm.sentAt ASC")
    List<ChatMessage> findNewMessages(@Param("roomId") String roomId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.roomId = :roomId AND cm.senderId != :userId AND cm.isRead = false")
    long countUnreadMessages(@Param("roomId") String roomId, @Param("userId") String userId);
    
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.roomId = :roomId AND cm.senderId != :userId AND cm.isRead = false")
    List<ChatMessage> findUnreadMessages(@Param("roomId") String roomId, @Param("userId") String userId);
    
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.roomId = :roomId AND cm.sentAt > :since ORDER BY cm.sentAt ASC")
    List<ChatMessage> findMessagesAfterTime(@Param("roomId") String roomId, @Param("since") LocalDateTime since);
    
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.roomId = :roomId AND cm.sentAt >= :since ORDER BY cm.sentAt ASC")
    List<ChatMessage> findMessagesFromTime(@Param("roomId") String roomId, @Param("since") LocalDateTime since);
} 