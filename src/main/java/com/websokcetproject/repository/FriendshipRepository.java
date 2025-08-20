package com.websokcetproject.repository;

import com.websokcetproject.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    
    // 사용자의 모든 친구 관계 조회 (수락된 것만)
    @Query("SELECT f FROM Friendship f WHERE (f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findAcceptedFriendshipsByUserId(@Param("userId") String userId);
    
    // 사용자의 친구 ID 목록 조회
    @Query("SELECT CASE WHEN f.requesterId = :userId THEN f.addresseeId ELSE f.requesterId END FROM Friendship f WHERE (f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = 'ACCEPTED'")
    List<String> findFriendIdsByUserId(@Param("userId") String userId);
    
    // 두 사용자 간의 친구 관계 조회
    @Query("SELECT f FROM Friendship f WHERE (f.requesterId = :userId1 AND f.addresseeId = :userId2) OR (f.requesterId = :userId2 AND f.addresseeId = :userId1)")
    Optional<Friendship> findFriendshipBetweenUsers(@Param("userId1") String userId1, @Param("userId2") String userId2);
    
    // 사용자의 대기 중인 친구 요청 조회
    @Query("SELECT f FROM Friendship f WHERE f.addresseeId = :userId AND f.status = 'PENDING'")
    List<Friendship> findPendingRequestsByUserId(@Param("userId") String userId);
    
    // 사용자가 보낸 친구 요청 조회
    @Query("SELECT f FROM Friendship f WHERE f.requesterId = :userId AND f.status = 'PENDING'")
    List<Friendship> findSentRequestsByUserId(@Param("userId") String userId);
} 