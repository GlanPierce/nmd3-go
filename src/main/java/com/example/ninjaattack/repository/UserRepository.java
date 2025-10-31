package com.example.ninjaattack.repository;

import com.example.ninjaattack.model.domain.User;
import org.springframework.data.jpa.repository.JpaRepository; // (修改)
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
// (修改) 继承 JpaRepository, 它提供了所有 CRUD 方法
// 我们使用 User 实体和 Long 类型的 ID (uid)
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Spring Data JPA 会自动根据方法名实现这个查询。
     * "SELECT * FROM users WHERE username = ?"
     */
    Optional<User> findByUsername(String username);

    /**
     * 自动实现排行榜查询
     * "SELECT * FROM users ORDER BY score DESC"
     */
    List<User> findAllByOrderByScoreDesc();
}