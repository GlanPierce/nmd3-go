package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.User;
import com.example.ninjaattack.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getLeaderboard() {
        return userRepository.findAllOrderByScoreDesc();
    }

    public void applyGameResult(String winnerUsername, String loserUsername) {
        User winner = userRepository.findByUsername(winnerUsername)
                .orElse(new User(winnerUsername, 0));
        User loser = userRepository.findByUsername(loserUsername)
                .orElse(new User(loserUsername, 0));

        winner.setScore(winner.getScore() + 3);
        loser.setScore(Math.max(0, loser.getScore() - 2)); // 假设分数不为负

        userRepository.save(winner);
        userRepository.save(loser);
    }
}