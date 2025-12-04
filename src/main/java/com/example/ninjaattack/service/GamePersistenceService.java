package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.Game;
import com.example.ninjaattack.model.domain.GamePhase;
import com.example.ninjaattack.model.entity.GameEntity;
import com.example.ninjaattack.repository.GameRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GamePersistenceService {

    private final GameRepository gameRepository;
    private final ObjectMapper objectMapper;

    public GamePersistenceService(GameRepository gameRepository, ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.objectMapper = objectMapper;
        // Configure ObjectMapper to be lenient
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
    }

    @Async
    public void saveGame(Game game) {
        try {
            GameEntity entity = gameRepository.findById(game.getGameId()).orElse(new GameEntity());

            if (entity.getId() == null) {
                entity.setId(game.getGameId());
            }

            entity.setP1Username(game.getP1().getUsername());
            entity.setP2Username(game.getP2().getUsername());
            entity.setStatus(game.getPhase() == GamePhase.GAME_OVER ? "FINISHED"
                    : (game.getPhase() == GamePhase.PRE_GAME ? "PRE_GAME" : "IN_PROGRESS"));

            // Populate statistics if game is over
            if (game.getPhase() == GamePhase.GAME_OVER && game.getResult() != null) {
                com.example.ninjaattack.model.domain.GameResult res = game.getResult();

                if ("DRAW".equals(res.getWinnerId())) {
                    entity.setWinnerUsername("DRAW");
                } else if ("p1".equals(res.getWinnerId())) {
                    entity.setWinnerUsername(game.getP1().getUsername());
                } else if ("p2".equals(res.getWinnerId())) {
                    entity.setWinnerUsername(game.getP2().getUsername());
                }

                entity.setP1Score(res.getP1PieceCount());
                entity.setP2Score(res.getP2PieceCount());
                entity.setTotalRounds(game.getCurrentRound());

                if (entity.getCreatedAt() != null) {
                    java.time.Duration duration = java.time.Duration.between(entity.getCreatedAt(),
                            java.time.LocalDateTime.now());
                    entity.setDurationSeconds(duration.getSeconds());
                }
            }

            entity.setGameStateJson(objectMapper.writeValueAsString(game));
            gameRepository.save(entity);
        } catch (Exception e) {
            System.err.println("Error saving game state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateGameStatus(String gameId, String status) {
        try {
            Optional<GameEntity> optionalEntity = gameRepository.findById(gameId);
            if (optionalEntity.isPresent()) {
                GameEntity entity = optionalEntity.get();
                entity.setStatus(status);
                gameRepository.save(entity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Game> loadActiveGames() {
        List<GameEntity> entities = gameRepository.findByStatus("IN_PROGRESS");
        List<Game> games = new ArrayList<>();
        for (GameEntity entity : entities) {
            try {
                Game game = objectMapper.readValue(entity.getGameStateJson(), Game.class);
                games.add(game);
            } catch (JsonProcessingException e) {
                System.err.println("Failed to parse game " + entity.getId());
                e.printStackTrace();
            }
        }
        return games;
    }

    public List<Game> loadFinishedGames() {
        List<GameEntity> entities = gameRepository.findByStatus("FINISHED");
        List<Game> games = new ArrayList<>();
        for (GameEntity entity : entities) {
            try {
                Game game = objectMapper.readValue(entity.getGameStateJson(), Game.class);
                games.add(game);
            } catch (JsonProcessingException e) {
                System.err.println("Failed to parse game " + entity.getId());
                e.printStackTrace();
            }
        }
        return games;
    }

    public Game loadGame(String gameId) {
        return gameRepository.findById(gameId).map(entity -> {
            try {
                return objectMapper.readValue(entity.getGameStateJson(), Game.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        }).orElse(null);
    }
}
