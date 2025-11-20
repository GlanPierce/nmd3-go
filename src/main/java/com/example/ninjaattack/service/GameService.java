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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    // [NEW] Save game state to DB
    private void saveGameState(Game game) {
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

    // --- 核心：计划任务时钟 ---
    @Scheduled(fixedRate = 1000)
    public void checkGameTimeouts() {
        long now = System.currentTimeMillis();
        for (String gameId : activeGames.keySet()) {
            Game game = activeGames.get(gameId);
            if (game == null)
                continue;

            synchronized (game) {
                boolean stateChanged = false;
                // 检查 30 秒的“匹配确认”超时
                if (game.getPhase() == GamePhase.PRE_GAME && now > game.getConfirmationDeadline()) {
                    System.out.println("游戏 " + gameId + " 匹配确认超时。");
                    handleMatchTimeout(game);
                    stateChanged = true; // Though handleMatchTimeout cleans up, so maybe not needed
                }
                // 检查 15 秒的“游戏回合”超时
                else if (game.getPhase() != GamePhase.PRE_GAME && game.getPhase() != GamePhase.GAME_OVER) {
                    String timedOutPlayerId = null;
                    if (now > game.getP1ActionDeadline()) {
                        timedOutPlayerId = "p1";
                    } else if (now > game.getP2ActionDeadline()) {
                        timedOutPlayerId = "p2";
                    }

                    if (timedOutPlayerId != null) {
                        gameEngine.handleTimeout(game, timedOutPlayerId);
                        updateTimersAfterMove(game);
                        stateChanged = true;

                        if (game.getPhase() != GamePhase.GAME_OVER) {
                            broadcastGameState(game.getGameId());
                        } else {
                            handleGameOver(game);
                        }
                    }
                }

                if (stateChanged && game.getPhase() != GamePhase.GAME_OVER) {
                    saveGameState(game);
                }
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

    public Game createGame(String p1Username, String p2Username) {
        Game game = new Game(p1Username, p2Username);
        game.setConfirmationDeadline(System.currentTimeMillis() + 30000L);

        activeGames.put(game.getGameId(), game);
        readyPlayersByGame.put(game.getGameId(), new HashSet<>());

        saveGameState(game); // [NEW] Save initial state

        return game;
    }

    public void playerReady(String gameId, String playerId) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        synchronized (game) {
            if (game.getPhase() != GamePhase.PRE_GAME)
                return;

            Set<String> readyPlayers = readyPlayersByGame.get(gameId);
            if (readyPlayers == null)
                return;

            readyPlayers.add(playerId);
            System.out.println("玩家 " + playerId + " 已准备 (游戏: " + gameId + ")");

            if (readyPlayers.contains("p1") && readyPlayers.contains("p2")) {
                System.out.println("双方准备就绪, 游戏 " + gameId + " 开始!");
                game.setConfirmationDeadline(Long.MAX_VALUE);
                startGame(game);
            } else {
                broadcastGameState(game.getGameId(), GamePhase.PRE_GAME, "玩家 " + playerId + " 已准备!");
            }
        }
    }

    private void startGame(Game game) {
        gameEngine.startGame(game);

        game.startTimer("p1", 15);
        game.startTimer("p2", 15);

        saveGameState(game); // [NEW] Save started state

        broadcastGameState(game.getGameId());
    }

    public void placeAmbush(String gameId, MoveRequest move) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        synchronized (game) {
            gameEngine.placeAmbush(game, move);

            if (move.getPlayerId().equals("p1") && game.getP1AmbushesPlacedThisRound() == 2)
                game.disarmTimer("p1");
            if (move.getPlayerId().equals("p2") && game.getP2AmbushesPlacedThisRound() == 2)
                game.disarmTimer("p2");

            if (game.getPhase() == GamePhase.PLACEMENT) {
                updateTimersAfterMove(game);
            }

            saveGameState(game); // [NEW] Save after move

            broadcastGameState(gameId);
        }
    }

    public void placePiece(String gameId, MoveRequest move) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        synchronized (game) {
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
        game.disarmTimer("p1");
        game.disarmTimer("p2");

        if (game.getPhase() == GamePhase.AMBUSH) {
            if (game.getP1AmbushesPlacedThisRound() < 2)
                game.startTimer("p1", 15);
            if (game.getP2AmbushesPlacedThisRound() < 2)
                game.startTimer("p2", 15);
        } else if (game.getPhase() == GamePhase.PLACEMENT || game.getPhase() == GamePhase.EXTRA_ROUNDS) {
            game.startTimer(game.getCurrentTurnPlayerId(), 15);
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