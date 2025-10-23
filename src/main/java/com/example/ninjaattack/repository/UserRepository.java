package com.example.ninjaattack.repository;

import com.example.ninjaattack.model.domain.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findByUsername(String username);
    List<User> findAllOrderByScoreDesc();
}