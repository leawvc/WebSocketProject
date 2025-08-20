package com.websokcetproject.repository;

import com.websokcetproject.model.UserLeaveTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserLeaveTimeRepository extends JpaRepository<UserLeaveTime, Long> {
    
    @Query("SELECT ult FROM UserLeaveTime ult WHERE ult.userId = :userId AND ult.roomId = :roomId ORDER BY ult.leftAt DESC")
    Optional<UserLeaveTime> findLatestByUserIdAndRoomId(@Param("userId") String userId, @Param("roomId") String roomId);
    
    @Query("SELECT ult FROM UserLeaveTime ult WHERE ult.userId = :userId AND ult.roomId = :roomId AND ult.leftAt > :since")
    Optional<UserLeaveTime> findByUserIdAndRoomIdAfterTime(@Param("userId") String userId, @Param("roomId") String roomId, @Param("since") LocalDateTime since);
    
    @Query("DELETE FROM UserLeaveTime ult WHERE ult.userId = :userId AND ult.roomId = :roomId")
    void deleteByUserIdAndRoomId(@Param("userId") String userId, @Param("roomId") String roomId);
    
    @Query("SELECT ult FROM UserLeaveTime ult WHERE ult.userId = :userId")
    List<UserLeaveTime> findByUserId(@Param("userId") String userId);
} 