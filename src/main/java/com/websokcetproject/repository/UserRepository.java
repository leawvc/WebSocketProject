package com.websokcetproject.repository;

import com.websokcetproject.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUserId(String userId);
    
    Optional<User> findByUsername(String username);
    
    List<User> findAllByUsername(String username);
    
    boolean existsByUserId(String userId);
    
    boolean existsByUsername(String username);
    
    // 친구 목록 조회를 위한 메서드
    @Query("SELECT u FROM User u WHERE u.userId IN :userIds")
    List<User> findByUserIdIn(@Param("userIds") List<String> userIds);
    
    // 사용자 검색을 위한 메서드
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> findByUsernameContainingIgnoreCase(@Param("keyword") String keyword);
} 