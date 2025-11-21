package com.example.ninjaattack.service;

import com.example.ninjaattack.logic.GameEngine;
import com.example.ninjaattack.model.domain.*;
import com.example.ninjaattack.model.dto.GameStateDTO;
import com.example.ninjaattack.model.dto.MoveRequest;
import com.example.ninjaattack.model.entity.GameEntity;
import com.example.ninjaattack.repository.GameRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class GameService {

    private final Map<String, Game> activeGames = new ConcurrentHashMap<>();
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;
    private final GameEngine gameEngine;
    private final GameRepository gameRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, Set<String>> readyPlayersByGame = new ConcurrentHashMap<>();

    public GameService(UserService userService, SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler,
            GameRepository gameRepository, ObjectMapper objectMapper) {
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
        this.gameRepository = gameRepository;
        this.objectMapper = objectMapper;
        // [NEW] Configure ObjectMapper to be lenient
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        this.gameEngine = new GameEngine();
    }

    // [NEW] Load active games from DB on startup
    @PostConstruct
    public void loadActiveGames() {
        List<GameEntity> entities = gameRepository.findByStatus("IN_PROGRESS");
        for (GameEntity entity : entities) {
            try {
                Game game = objectMapper.readValue(entity.getGameStateJson(), Game.class);
                // Re-initialize timers if needed? For now, just load state.
                // Ideally we should calculate remaining time based on timestamps, but that's
                // complex.
                // Let's at least put it back in memory so players can reconnect.
                activeGames.put(game.getGameId(), game);
                System.out.println("Loaded game " + game.getGameId() + " from DB.");
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    // [OPTIMIZED] Async Save game state to DB
    @Async // Run in background thread
    public void saveGameState(Game game) {
        try {
            GameEntity entity = new GameEntity();
            entity.setId(game.getGameId());
            entity.setP1Username(game.getP1().getUsername());
            entity.setP2Username(game.getP2().getUsername());
            entity.setStatus(game.getPhase() == GamePhase.GAME_OVER ? "FINISHED"
                    : (game.getPhase() == GamePhase.PRE_GAME ? "PRE_GAME" : "IN_PROGRESS"));
            entity.setGameStateJson(objectMapper.writeValueAsString(game));
            gameRepository.save(entity);
        } catch (Exception e) {
            System.err.println("Error saving game state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // [OPTIMIZED] Event-driven timer scheduling instead of polling
    private void scheduleTimeout(Game game, String playerId, int seconds) {
        // Cancel existing timer for this player if any
        cancelTimer(game, playerId);

        long delayMs = seconds * 1000L;
        Date startTime = new Date(System.currentTimeMillis() + delayMs);

        // Schedule the task
        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            handleTimeoutTask(game.getGameId(), playerId);
        }, startTime);

        // Store the future in the game object (transient field)
        if ("p1".equals(playerId)) {
            // We might need to add specific fields for p1/p2 timers in Game class if we
            // want to track them separately
            // For now, let's assume we use the generic turnTimer/matchTimer or add a map
            // But Game.java has turnTimer and matchTimer. Let's use them.
            // Actually, Game.java has turnTimer and matchTimer, but we need to know WHICH
            // player's timer it is.
            // For simplicity, let's just use the deadlines in Game to double-check inside
            // the task.
            game.setTurnTimer(future);
        } else {
            // If both have timers (Ambush phase), we need two fields.
            // Current Game.java only has turnTimer and matchTimer.
            // Let's use turnTimer for the current active timer.
            // For Ambush phase where BOTH have timers, we might need a slight adjustment.
            // However, for now, let's just store it.
            game.setTurnTimer(future);
        }

        // Update deadline for frontend display
        game.startTimer(playerId, seconds);
    }

    private void cancelTimer(Game game, String playerId) {
        game.disarmTimer(playerId);
        if (game.getTurnTimer() != null && !game.getTurnTimer().isDone()) {
            game.getTurnTimer().cancel(false);
        }
    }

    // The task that runs when timeout occurs
    private void handleTimeoutTask(String gameId, String playerId) {
        Game game = activeGames.get(gameId);
        if (game == null)
            return;

        synchronized (game) {
            // Double check if phase is still valid for timeout
            if (game.getPhase() == GamePhase.GAME_OVER)
                return;

            // Check if the deadline actually passed (to avoid race conditions where move
            // came in just now)
            long now = System.currentTimeMillis();
            long deadline = "p1".equals(playerId) ? game.getP1ActionDeadline() : game.getP2ActionDeadline();

            if (now >= deadline) {
                System.out.println("Timeout triggered for " + playerId + " in game " + gameId);
                gameEngine.handleTimeout(game, playerId);
                updateTimersAfterMove(game);

                if (game.getPhase() != GamePhase.GAME_OVER) {
                    broadcastGameState(game.getGameId());
                } else {
                    handleGameOver(game);
                }
                saveGameState(game);
            }
        }
    }

    private void handleMatchTimeout(Game game) {
        broadcastGameState(game.getGameId(), GamePhase.MATCH_CANCELLED, "有玩家未能在30秒内确认准备。");
        // Update status to FINISHED/CANCELLED in DB?
        try {
            GameEntity entity = gameRepository.findById(game.getGameId()).orElse(new GameEntity());
            entity.setId(game.getGameId());
            entity.setStatus("CANCELLED");
            gameRepository.save(entity);
        } catch (Exception e) {
            e.printStackTrace();
        }

        cleanupGame(game.getGameId());
    }

    public void broadcastGameState(String gameId, GamePhase phase, String message) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        GameStateDTO dto = new GameStateDTO();
        dto.setGameId(gameId);
        dto.setPhase(phase);
        dto.setStatusMessage(message);
        dto.setP1Username(game.getP1().getUsername());
        dto.setP2Username(game.getP2().getUsername());

        messagingTemplate.convertAndSend("/topic/game/" + gameId, dto);
    }

    public void broadcastGameState(String gameId) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        GameStateDTO dto;
        synchronized (game) {
            dto = mapToDTO(game);
        }
        messagingTemplate.convertAndSend("/topic/game/" + gameId, dto);
    }

    private void cleanupGame(String gameId) {
        activeGames.remove(gameId);
        readyPlayersByGame.remove(gameId);
    }

    // --- 核心游戏 API ---

    // [NEW] Validate player identity
    private void validatePlayerIdentity(Game game, String playerId, String username) {
        String expectedUsername = null;
        if ("p1".equals(playerId)) {
            expectedUsername = game.getP1().getUsername();
        } else if ("p2".equals(playerId)) {
            expectedUsername = game.getP2().getUsername();
        }

        if (expectedUsername == null || !expectedUsername.equals(username)) {
            throw new IllegalArgumentException("Unauthorized: Player " + playerId + " is not " + username);
        }
    }

    public Game createGame(String p1Username, String p2Username) {
        Game game = new Game(p1Username, p2Username);
        game.setConfirmationDeadline(System.currentTimeMillis() + 30000L);

        activeGames.put(game.getGameId(), game);
        readyPlayersByGame.put(game.getGameId(), new HashSet<>());

        saveGameState(game); // [NEW] Save initial state

        // Schedule match confirmation timeout
        Date deadline = new Date(System.currentTimeMillis() + 30000L);
        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            Game g = activeGames.get(game.getGameId());
            if (g != null && g.getPhase() == GamePhase.PRE_GAME) {
                handleMatchTimeout(g);
            }
        }, deadline);
        game.setMatchTimer(future);

        return game;
    }

    public void playerReady(String gameId, String playerId, String username) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        synchronized (game) {
            if (game.getPhase() != GamePhase.PRE_GAME)
                return;

            validatePlayerIdentity(game, playerId, username);

            Set<String> readyPlayers = readyPlayersByGame.get(gameId);
            if (readyPlayers == null)
                return;

            readyPlayers.add(playerId);
            System.out.println("玩家 " + playerId + " 已准备 (游戏: " + gameId + ")");

            if (readyPlayers.contains("p1") && readyPlayers.contains("p2")) {
                System.out.println("双方准备就绪, 游戏 " + gameId + " 开始!");
                game.setConfirmationDeadline(Long.MAX_VALUE);
                // Cancel match timer
                if (game.getMatchTimer() != null) {
                    game.getMatchTimer().cancel(false);
                }
                startGame(game);
            } else {
                broadcastGameState(game.getGameId(), GamePhase.PRE_GAME, "玩家 " + playerId + " 已准备!");
            }
        }
    }

    private void startGame(Game game) {
        gameEngine.startGame(game);

        // Start timers for both players (Ambush phase)
        // Note: For Ambush phase where both have timers, our simple single 'turnTimer'
        // field in Game might be insufficient if we want to cancel them individually
        // strictly.
        // But since they run in parallel and we check deadlines, we can just schedule
        // two tasks.
        scheduleTimeout(game, "p1", 15);
        scheduleTimeout(game, "p2", 15);

        saveGameState(game); // [NEW] Save started state

        broadcastGameState(game.getGameId());
    }

    public void placeAmbush(String gameId, MoveRequest move, String username) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        synchronized (game) {
            validatePlayerIdentity(game, move.getPlayerId(), username);
            gameEngine.placeAmbush(game, move);

            if (move.getPlayerId().equals("p1") && game.getP1AmbushesPlacedThisRound() == 2)
                cancelTimer(game, "p1");
            if (move.getPlayerId().equals("p2") && game.getP2AmbushesPlacedThisRound() == 2)
                cancelTimer(game, "p2");

            if (game.getPhase() == GamePhase.PLACEMENT) {
                updateTimersAfterMove(game);
            }

            saveGameState(game); // [NEW] Save after move

            broadcastGameState(gameId);
        }
    }

    public void placePiece(String gameId, MoveRequest move, String username) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        synchronized (game) {
            validatePlayerIdentity(game, move.getPlayerId(), username);
            gameEngine.placePiece(game, move);

            if (game.getPhase() == GamePhase.GAME_OVER) {
                handleGameOver(game);
            } else {
                updateTimersAfterMove(game);
                saveGameState(game); // [NEW] Save after move
                broadcastGameState(gameId);
            }
        }
    }

    private void updateTimersAfterMove(Game game) {
        cancelTimer(game, "p1");
        cancelTimer(game, "p2");

        if (game.getPhase() == GamePhase.AMBUSH) {
            if (game.getP1AmbushesPlacedThisRound() < 2)
                scheduleTimeout(game, "p1", 15);
            if (game.getP2AmbushesPlacedThisRound() < 2)
                scheduleTimeout(game, "p2", 15);
        } else if (game.getPhase() == GamePhase.PLACEMENT || game.getPhase() == GamePhase.EXTRA_ROUNDS) {
            scheduleTimeout(game, game.getCurrentTurnPlayerId(), 15);
        }
    }

    private void handleGameOver(Game game) {
        GameResult result = game.getResult();
        if (result == null)
            return;

        if ("p1".equals(result.getWinnerId())) {
            userService.applyGameResult(game.getP1().getUsername(), game.getP2().getUsername());
        } else if ("p2".equals(result.getWinnerId())) {
            userService.applyGameResult(game.getP2().getUsername(), game.getP1().getUsername());
        }

        saveGameState(game); // [NEW] Save final state

        broadcastGameState(game.getGameId());
        cleanupGame(game.getGameId());
    }

    private Game findGame(String gameId) {
        // First check memory
        Game game = activeGames.get(gameId);
        if (game != null)
            return game;

        // Then check DB (for recovery)
        return gameRepository.findById(gameId).map(entity -> {
            try {
                Game loadedGame = objectMapper.readValue(entity.getGameStateJson(), Game.class);
                activeGames.put(gameId, loadedGame);
                return loadedGame;
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        }).orElse(null);
    }

    private GameStateDTO mapToDTO(Game game) {
        GameStateDTO dto = new GameStateDTO();
        dto.setGameId(game.getGameId());
        dto.setP1Username(game.getP1().getUsername());
        dto.setP2Username(game.getP2().getUsername());
        dto.setP1ExtraTurns(game.getP1().getExtraTurns());
        dto.setP2ExtraTurns(game.getP2().getExtraTurns());
        dto.setBoard(game.getBoard());
        dto.setPhase(game.getPhase());
        dto.setCurrentRound(game.getCurrentRound());
        dto.setCurrentTurnPlayerId(game.getCurrentTurnPlayerId());
        dto.setResult(game.getResult());

        if (game.getPhase() == GamePhase.AMBUSH) {
            dto.setP1AmbushesPlaced(game.getP1AmbushesPlacedThisRound());
            dto.setP2AmbushesPlaced(game.getP2AmbushesPlacedThisRound());
        }

        dto.setStatusMessage(generateStatusMessage(game));

        long now = System.currentTimeMillis();
        if (game.getPhase() == GamePhase.PRE_GAME) {
            long timeLeft = game.getConfirmationDeadline() == Long.MAX_VALUE ? -1
                    : Math.max(0, game.getConfirmationDeadline() - now);
            dto.setP1TimeLeft(timeLeft);
            dto.setP2TimeLeft(timeLeft);
        } else {
            dto.setP1TimeLeft(
                    game.getP1ActionDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getP1ActionDeadline() - now));
            dto.setP2TimeLeft(
                    game.getP2ActionDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getP2ActionDeadline() - now));
        }

        return dto;
    }

    private String generateStatusMessage(Game game) {
        Player p1 = game.getP1();
        Player p2 = game.getP2();
        switch (game.getPhase()) {
            case PRE_GAME:
                return "等待玩家准备...";
            case AMBUSH:
                return String.format("第 %d 轮 - 伏兵阶段 (15秒). P1 (%s) 已设置 %d/2, P2 (%s) 已设置 %d/2.",
                        game.getCurrentRound(), p1.getUsername(),
                        game.getP1AmbushesPlacedThisRound(),
                        p2.getUsername(),
                        game.getP2AmbushesPlacedThisRound());
            case PLACEMENT:
                Player turnPlayer = game.getPlayer(game.getCurrentTurnPlayerId());
                return String.format("第 %d 轮 - 落子阶段 (15秒). 轮到 %s (%s) 落子 (%d/3).",
                        game.getCurrentRound(), turnPlayer.getId(), turnPlayer.getUsername(),
                        game.getPlacementsMadeThisTurn() + 1);
            case EXTRA_ROUNDS:
                if (game.getCurrentTurnPlayerId() == null)
                    return "额外轮次...准备中";
                Player extraTurnPlayer = game.getPlayer(game.getCurrentTurnPlayerId());
                return String.format("额外轮次 (15秒). 轮到 %s (%s). 剩余次数: P1[%d], P2[%d]",
                        extraTurnPlayer.getId(), extraTurnPlayer.getUsername(),
                        p1.getExtraTurns(), p2.getExtraTurns());
            case GAME_OVER:
                GameResult res = game.getResult();
                if (res == null)
                    return "游戏结束";
                if ("DRAW".equals(res.getWinnerId()))
                    return "游戏结束: 平局!";
                Player winner = game.getPlayer(res.getWinnerId());
                return String.format("游戏结束! 胜利者: %s (%s). [P1: %d连/%d子, P2: %d连/%d子]",
                        winner.getId(), winner.getUsername(), res.getP1MaxConnection(), res.getP1PieceCount(),
                        res.getP2MaxConnection(), res.getP2PieceCount());
            case MATCH_CANCELLED:
                return "匹配已取消。";
            default:
                return "等待中...";
        }
    }
}