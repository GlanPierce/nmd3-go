package com.example.ninjaattack.service;

import com.example.ninjaattack.logic.GameEngine;
import com.example.ninjaattack.model.domain.*;
import com.example.ninjaattack.model.dto.GameStateDTO;
import com.example.ninjaattack.model.dto.MoveRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final GameEngine gameEngine;

    // New Services
    private final GameTimerService gameTimerService;
    private final GamePersistenceService gamePersistenceService;

    private final Map<String, Set<String>> readyPlayersByGame = new ConcurrentHashMap<>();

    public GameService(UserService userService,
            SimpMessagingTemplate messagingTemplate,
            GameTimerService gameTimerService,
            GamePersistenceService gamePersistenceService) {
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.gameTimerService = gameTimerService;
        this.gamePersistenceService = gamePersistenceService;
        this.gameEngine = new GameEngine();
    }

    @PostConstruct
    public void loadActiveGames() {
        List<Game> games = gamePersistenceService.loadActiveGames();
        for (Game game : games) {
            activeGames.put(game.getGameId(), game);

            // Restore timers
            long now = System.currentTimeMillis();
            if (game.getPhase() == GamePhase.AMBUSH) {
                long deadline = Math.max(game.getP1ActionDeadline(), game.getP2ActionDeadline());
                long delay = Math.max(0, deadline - now);
                if (delay > 0) {
                    gameTimerService.scheduleAmbushTimer(game, (int) (delay / 1000),
                            () -> handleAmbushTimeoutTask(game.getGameId()));
                } else {
                    handleAmbushTimeoutTask(game.getGameId());
                }
            } else if (game.getPhase() == GamePhase.PLACEMENT || game.getPhase() == GamePhase.EXTRA_ROUNDS) {
                long deadline = "p1".equals(game.getCurrentTurnPlayerId()) ? game.getP1ActionDeadline()
                        : game.getP2ActionDeadline();
                long delay = Math.max(0, deadline - now);
                if (delay > 0) {
                    String playerId = game.getCurrentTurnPlayerId();
                    gameTimerService.scheduleTurnTimer(game, playerId, (int) (delay / 1000),
                            () -> handleTimeoutTask(game.getGameId(), playerId));
                } else {
                    handleTimeoutTask(game.getGameId(), game.getCurrentTurnPlayerId());
                }
            }
            System.out.println("Loaded game " + game.getGameId() + " from DB and restored timers.");
        }
    }

    // The task that runs when timeout occurs (Normal phases)
    private void handleTimeoutTask(String gameId, String playerId) {
        Game game = activeGames.get(gameId);
        if (game == null)
            return;

        synchronized (game) {
            if (game.getPhase() == GamePhase.GAME_OVER)
                return;

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
                gamePersistenceService.saveGame(game);
            }
        }
    }

    // The task that runs when timeout occurs (Ambush phase)
    private void handleAmbushTimeoutTask(String gameId) {
        Game game = activeGames.get(gameId);
        if (game == null)
            return;

        synchronized (game) {
            if (game.getPhase() != GamePhase.AMBUSH)
                return;

            long now = System.currentTimeMillis();
            if (now >= game.getP1ActionDeadline() && game.getP1AmbushesPlacedThisRound() < 2) {
                gameEngine.handleTimeout(game, "p1");
            }
            if (now >= game.getP2ActionDeadline() && game.getP2AmbushesPlacedThisRound() < 2) {
                gameEngine.handleTimeout(game, "p2");
            }

            updateTimersAfterMove(game);
            gamePersistenceService.saveGame(game);
            broadcastGameState(gameId);
        }
    }

    private void handleMatchTimeout(Game game) {
        broadcastGameState(game.getGameId(), GamePhase.MATCH_CANCELLED, "有玩家未能在30秒内确认准备。");
        gamePersistenceService.updateGameStatus(game.getGameId(), "CANCELLED");
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

    public GameStateDTO findActiveGameByUsername(String username) {
        for (Game game : activeGames.values()) {
            if (game.getPhase() == GamePhase.GAME_OVER || game.getPhase() == GamePhase.MATCH_CANCELLED) {
                continue;
            }
            if (username.equals(game.getP1().getUsername()) || username.equals(game.getP2().getUsername())) {
                synchronized (game) {
                    return mapToDTO(game);
                }
            }
        }
        return null;
    }

    // --- Core Game API ---

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

        gamePersistenceService.saveGame(game);

        // Schedule match confirmation timeout
        gameTimerService.scheduleMatchTimer(game, 30, () -> {
            Game g = activeGames.get(game.getGameId());
            if (g != null && g.getPhase() == GamePhase.PRE_GAME) {
                handleMatchTimeout(g);
            }
        });

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
                gameTimerService.cancelMatchTimer(game);
                startGame(game);
            } else {
                broadcastGameState(game.getGameId(), GamePhase.PRE_GAME, "玩家 " + playerId + " 已准备!");
            }
        }
    }

    private void startGame(Game game) {
        gameEngine.startGame(game);
        gameTimerService.scheduleAmbushTimer(game, 15, () -> handleAmbushTimeoutTask(game.getGameId()));
        gamePersistenceService.saveGame(game);
        broadcastGameState(game.getGameId());
    }

    public void placeAmbush(String gameId, MoveRequest move, String username) {
        Game game = findGame(gameId);
        if (game == null)
            return;

        synchronized (game) {
            validatePlayerIdentity(game, move.getPlayerId(), username);
            gameEngine.placeAmbush(game, move);

            boolean p1Done = game.getP1AmbushesPlacedThisRound() == 2;
            boolean p2Done = game.getP2AmbushesPlacedThisRound() == 2;

            if (p1Done && p2Done) {
                gameTimerService.cancelTurnTimer(game);
            }

            if (game.getPhase() == GamePhase.PLACEMENT) {
                updateTimersAfterMove(game);
            }

            gamePersistenceService.saveGame(game);
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
                gamePersistenceService.saveGame(game);
                broadcastGameState(gameId);
            }
        }
    }

    private void updateTimersAfterMove(Game game) {
        if (game.getPhase() == GamePhase.AMBUSH) {
            gameTimerService.scheduleAmbushTimer(game, 15, () -> handleAmbushTimeoutTask(game.getGameId()));
        } else if (game.getPhase() == GamePhase.PLACEMENT || game.getPhase() == GamePhase.EXTRA_ROUNDS) {
            String playerId = game.getCurrentTurnPlayerId();
            gameTimerService.scheduleTurnTimer(game, playerId, 15, () -> handleTimeoutTask(game.getGameId(), playerId));
        }
    }

    private void handleGameOver(Game game) {
        GameResult result = game.getResult();
        if (result == null)
            return;

        gameTimerService.cancelTurnTimer(game);

        if ("p1".equals(result.getWinnerId())) {
            userService.applyGameResult(game.getP1().getUsername(), game.getP2().getUsername());
        } else if ("p2".equals(result.getWinnerId())) {
            userService.applyGameResult(game.getP2().getUsername(), game.getP1().getUsername());
        }

        gamePersistenceService.saveGame(game);
        broadcastGameState(game.getGameId());
        cleanupGame(game.getGameId());
    }

    private Game findGame(String gameId) {
        Game game = activeGames.get(gameId);
        if (game != null)
            return game;

        game = gamePersistenceService.loadGame(gameId);
        if (game != null) {
            activeGames.put(gameId, game);
        }
        return game;
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