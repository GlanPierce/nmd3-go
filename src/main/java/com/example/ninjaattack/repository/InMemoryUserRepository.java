package com.example.ninjaattack.repository;

import com.example.ninjaattack.model.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> userStore = new ConcurrentHashMap<>();

    // 预设一些用户
    public InMemoryUserRepository() {
        userStore.put("NinjaA", new User("NinjaA", 100));
        userStore.put("ShadowB", new User("ShadowB", 80));
    }

    @Override
    public User save(User user) {
        userStore.put(user.getUsername(), user);
        return user;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userStore.get(username));
    }

    @Override
    public List<User> findAllOrderByScoreDesc() {
        return userStore.values().stream()
                .sorted(Comparator.comparingInt(User::getScore).reversed())
                .collect(Collectors.toList());
    }
}