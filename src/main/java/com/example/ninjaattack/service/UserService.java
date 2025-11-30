package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.User;
import com.example.ninjaattack.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EloRatingService eloRatingService;

    public UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder,
            EloRatingService eloRatingService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eloRatingService = eloRatingService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("未找到用户: " + username));
    }

    public User register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("用户名已存在");
        }
        String encodedPassword = passwordEncoder.encode(password);
        User newUser = new User(username, encodedPassword, 1200);
        return userRepository.save(newUser);
    }

    @Transactional(readOnly = true)
    public List<User> getLeaderboard() {
        return userRepository.findAllByOrderByScoreDesc();
    }

    /**
     * Process game result using optimized Elo rating system.
     * Updates scores and gamesPlayed for both users.
     */
    public void processGameResult(com.example.ninjaattack.model.domain.Game game) {
        String p1Username = game.getP1().getUsername();
        String p2Username = game.getP2().getUsername();

        User p1 = userRepository.findByUsername(p1Username)
                .orElseThrow(() -> new UsernameNotFoundException("Player 1 not found: " + p1Username));
        User p2 = userRepository.findByUsername(p2Username)
                .orElseThrow(() -> new UsernameNotFoundException("Player 2 not found: " + p2Username));

        // Determine actual scores
        double p1ActualScore = eloRatingService.getActualScore("p1", game.getResult());
        double p2ActualScore = eloRatingService.getActualScore("p2", game.getResult());

        // Determine first mover
        boolean p1IsFirst = "p1".equals(game.getFirstMovePlayerId());

        // Calculate new ratings
        int p1NewRating = eloRatingService.calculateNewRating(p1.getScore(), p2.getScore(), p1ActualScore, p1IsFirst,
                p1.getGamesPlayed());
        int p2NewRating = eloRatingService.calculateNewRating(p2.getScore(), p1.getScore(), p2ActualScore, !p1IsFirst,
                p2.getGamesPlayed());

        // Update users
        p1.setScore(p1NewRating);
        p1.setGamesPlayed(p1.getGamesPlayed() + 1);

        p2.setScore(p2NewRating);
        p2.setGamesPlayed(p2.getGamesPlayed() + 1);

        userRepository.save(p1);
        userRepository.save(p2);
    }
}