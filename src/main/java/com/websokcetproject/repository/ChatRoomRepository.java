package com.websokcetproject.repository;

import com.websokcetproject.model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    
    Optional<ChatRoom> findByRoomId(String roomId);
    
    @Query("SELECT cr FROM ChatRoom cr WHERE :userId MEMBER OF cr.participants ORDER BY cr.updatedAt DESC")
    List<ChatRoom> findRoomsByParticipant(@Param("userId") String userId);
    
    boolean existsByRoomId(String roomId);
    
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.roomName LIKE %:keyword% OR cr.lastMessage LIKE %:keyword%")
    List<ChatRoom> searchRooms(@Param("keyword") String keyword);
    
    @Query("SELECT cr.roomId FROM ChatRoom cr WHERE :userId MEMBER OF cr.participants")
    List<String> findRoomIdsByParticipant(@Param("userId") String userId);
}
