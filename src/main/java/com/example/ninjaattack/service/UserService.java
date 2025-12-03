package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.User;
import com.example.ninjaattack.model.dto.GameHistoryDTO;
import com.example.ninjaattack.model.entity.GameEntity;
import com.example.ninjaattack.repository.GameRepository;
import com.example.ninjaattack.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final PasswordEncoder passwordEncoder;
    private final EloRatingService eloRatingService;

    public UserService(UserRepository userRepository, GameRepository gameRepository,
            @Lazy PasswordEncoder passwordEncoder,
            EloRatingService eloRatingService) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
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

        // Save rating changes to GameResult
        int p1Change = p1NewRating - p1.getScore();
        int p2Change = p2NewRating - p2.getScore();
        game.getResult().setP1RatingChange(p1Change);
        game.getResult().setP2RatingChange(p2Change);

        // Update users
        p1.setScore(p1NewRating);
        p1.setGamesPlayed(p1.getGamesPlayed() + 1);

        p2.setScore(p2NewRating);
        p2.setGamesPlayed(p2.getGamesPlayed() + 1);

        userRepository.save(p1);
        userRepository.save(p2);
    }

    public void updateAvatar(String username, String avatarName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Simple validation to ensure it's one of the allowed avatars
        if (!avatarName.matches("avatar_[1-4]\\.svg")) {
            throw new IllegalArgumentException("Invalid avatar");
        }

        user.setAvatar(avatarName);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<GameHistoryDTO> getMatchHistory(String username) {
        List<GameEntity> games = gameRepository.findByP1UsernameOrP2UsernameOrderByCreatedAtDesc(username, username);
        System.out.println("DEBUG: Found " + games.size() + " games for user " + username);

        return games.stream()
                .limit(20)
                .map(game -> {
                    GameHistoryDTO dto = new GameHistoryDTO();
                    dto.setGameId(game.getId());

                    if (game.getCreatedAt() != null) {
                        dto.setTimestamp(game.getCreatedAt());
                    } else {
                        dto.setTimestamp(null);
                    }

                    boolean isP1 = username.equals(game.getP1Username());
                    dto.setOpponentName(isP1 ? game.getP2Username() : game.getP1Username());

                    if (game.getGameStateJson() == null || game.getGameStateJson().isEmpty()) {
                        dto.setResult("数据丢失");
                        return dto;
                    }

                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        mapper.configure(
                                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                false);

                        com.example.ninjaattack.model.domain.Game domainGame = mapper.readValue(game.getGameStateJson(),
                                com.example.ninjaattack.model.domain.Game.class);

                        if (domainGame != null && domainGame.getResult() != null) {
                            String winnerId = domainGame.getResult().getWinnerId();
                            if ("DRAW".equals(winnerId)) {
                                dto.setResult("平局");
                            } else {
                                boolean iWon = (isP1 && "p1".equals(winnerId)) || (!isP1 && "p2".equals(winnerId));
                                dto.setResult(iWon ? "胜利" : "失败");
                            }

                            // Set score change
                            if (isP1) {
                                dto.setScoreChange(domainGame.getResult().getP1RatingChange());
                            } else {
                                dto.setScoreChange(domainGame.getResult().getP2RatingChange());
                            }
                        } else {
                            dto.setResult("未知");
                        }
                    } catch (Exception e) {
                        System.err.println("DEBUG: Error parsing game " + game.getId() + ": " + e.getMessage());
                        if (game.getGameStateJson() != null) {
                            System.err.println("DEBUG: Failed JSON content (first 500 chars): " +
                                    game.getGameStateJson().substring(0,
                                            Math.min(game.getGameStateJson().length(), 500)));
                        }
                        dto.setResult("数据损坏");
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }
}