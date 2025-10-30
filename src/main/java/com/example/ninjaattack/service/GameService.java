package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.*;
import com.example.ninjaattack.model.dto.GameStateDTO;
import com.example.ninjaattack.model.dto.MoveRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private final Map<String, Game> activeGames = new ConcurrentHashMap<>();
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;

    public GameService(UserService userService, SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler) {
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
    }

    // --- Core: Scheduled Clock Task ---
    /**
     * Every second, check all active games for timeouts.
     */
    @Scheduled(fixedRate = 1000)
    public void checkGameTimeouts() {
        long now = System.currentTimeMillis();
        for (String gameId : activeGames.keySet()) {
            Game game = activeGames.get(gameId);
            if (game == null || game.getPhase() == GamePhase.GAME_OVER) continue;

            String timedOutPlayerId = null;
            if (now > game.getP1ActionDeadline()) {
                timedOutPlayerId = "p1";
            } else if (now > game.getP2ActionDeadline()) {
                timedOutPlayerId = "p2";
            }

            if (timedOutPlayerId != null) {
                // Handle the timeout
                handleTimeout(game, timedOutPlayerId);
                // Immediately broadcast the updated state after the timeout action
                if (game.getPhase() != GamePhase.GAME_OVER) {
                    broadcastGameState(game.getGameId());
                }
            }
        }
    }

    /**
     * Broadcasts the current game state to all subscribed clients for a specific game.
     */
    public void broadcastGameState(String gameId) {
        Game game = findGame(gameId);
        if (game == null) return; // Game might have ended and been removed

        GameStateDTO dto = mapToDTO(game);

        // Send to the topic /topic/game/{gameId}
        messagingTemplate.convertAndSend("/topic/game/" + gameId, dto);
    }

    // --- Core Game APIs ---

    /**
     * Creates a new game. This is the only REST API endpoint.
     * It puts the game in a PRE_GAME state and schedules the actual start.
     */
    public synchronized GameStateDTO createGame(String p1Username, String p2Username) {
        Game game = new Game(p1Username, p2Username); // Game starts in PRE_GAME phase
        activeGames.put(game.getGameId(), game);

        // Schedule a task to start the game after a 3-second delay
        taskScheduler.schedule(
                () -> startGame(game.getGameId()),
                Instant.now().plusSeconds(3)
        );

        // Immediately return the PRE_GAME state to the client
        return mapToDTO(game);
    }

    /**
     * Called by the TaskScheduler 3 seconds after creation.
     * Transitions the game to the AMBUSH phase and starts the timers.
     */
    public synchronized void startGame(String gameId) {
        Game game = findGame(gameId);
        if (game == null || game.getPhase() != GamePhase.PRE_GAME) {
            return; // Game already started or no longer exists
        }

        // Transition to the Ambush phase
        game.resetForAmbushPhase();

        // Start the timers for the Ambush phase
        game.startTimer("p1", 15);
        game.startTimer("p2", 15);

        // Broadcast that the AMBUSH phase has begun
        broadcastGameState(gameId);
    }

    /**
     * Handles an ambush placement request from a client via WebSocket.
     */
    public synchronized void placeAmbush(String gameId, MoveRequest move) {
        Game game = findGame(gameId);
        if (game.getPhase() != GamePhase.AMBUSH) throw new IllegalStateException("Not in AMBUSH phase");

        String playerId = move.getPlayerId();
        if (playerId.equals("p1")) {
            if (game.getP1AmbushesPlacedThisRound() >= 2) throw new IllegalStateException("P1 Already placed 2 ambushes");
        } else {
            if (game.getP2AmbushesPlacedThisRound() >= 2) throw new IllegalStateException("P2 Already placed 2 ambushes");
        }

        Square square = game.getBoard().getSquare(move.getR(), move.getC());
        if (square.getOwnerId() != null) throw new IllegalStateException("Cannot place ambush on occupied square");

        if (playerId.equals("p1")) {
            if (square.isP2Ambush()) throw new IllegalStateException("Cannot place ambush on an opponent's ambush");
            if (square.isP1Ambush()) throw new IllegalStateException("Cannot place ambush on your own ambush");
            square.setP1Ambush(true);
            game.setP1AmbushesPlacedThisRound(game.getP1AmbushesPlacedThisRound() + 1);
            if (game.getP1AmbushesPlacedThisRound() == 2) game.disarmTimer("p1");
        } else {
            if (square.isP1Ambush()) throw new IllegalStateException("Cannot place ambush on an opponent's ambush");
            if (square.isP2Ambush()) throw new IllegalStateException("Cannot place ambush on your own ambush");
            square.setP2Ambush(true);
            game.setP2AmbushesPlacedThisRound(game.getP2AmbushesPlacedThisRound() + 1);
            if (game.getP2AmbushesPlacedThisRound() == 2) game.disarmTimer("p2");
        }

        if (game.getP1AmbushesPlacedThisRound() == 2 && game.getP2AmbushesPlacedThisRound() == 2) {
            transitionToPlacement(game);
        }

        broadcastGameState(gameId);
    }

    /**
     * Handles a piece placement request from a client via WebSocket.
     */
    public synchronized void placePiece(String gameId, MoveRequest move) {
        Game game = findGame(gameId);

        if (game.getPhase() == GamePhase.PLACEMENT) {
            handlePlacementPhase(game, move);
        } else if (game.getPhase() == GamePhase.EXTRA_ROUNDS) {
            handleExtraRoundsPhase(game, move);
        } else {
            throw new IllegalStateException("Not in a placement phase");
        }

        // Broadcast state unless the game just ended (endGame handles its own broadcast)
        if (game.getPhase() != GamePhase.GAME_OVER) {
            broadcastGameState(gameId);
        }
    }

    // --- Timeout and Random Move Logic ---

    private synchronized void handleTimeout(Game game, String timedOutPlayerId) {
        long now = System.currentTimeMillis();
        if (timedOutPlayerId.equals("p1") && now < game.getP1ActionDeadline()) return;
        if (timedOutPlayerId.equals("p2") && now < game.getP2ActionDeadline()) return;
        if (game.getPhase() == GamePhase.GAME_OVER) return;

        System.out.println("Handling timeout for player " + timedOutPlayerId + " in game " + game.getGameId());

        game.disarmTimer(timedOutPlayerId);

        if (game.getPhase() == GamePhase.AMBUSH) {
            int remaining = 2 - (timedOutPlayerId.equals("p1") ? game.getP1AmbushesPlacedThisRound() : game.getP2AmbushesPlacedThisRound());
            if (remaining > 0) {
                performRandomAmbush(game, timedOutPlayerId, remaining);
            }
            if (game.getP1AmbushesPlacedThisRound() == 2 && game.getP2AmbushesPlacedThisRound() == 2) {
                transitionToPlacement(game);
            }
        } else if (game.getPhase() == GamePhase.PLACEMENT) {
            if (timedOutPlayerId.equals(game.getCurrentTurnPlayerId())) {
                performRandomPlacement(game, timedOutPlayerId);
            }
        } else if (game.getPhase() == GamePhase.EXTRA_ROUNDS) {
            if (timedOutPlayerId.equals(game.getCurrentTurnPlayerId())) {
                performRandomExtraPlacement(game, timedOutPlayerId);
            }
        }
    }

    private void performRandomAmbush(Game game, String playerId, int count) {
        List<Point> spots = getValidAmbushSpots(game.getBoard(), playerId);
        Collections.shuffle(spots);
        int placed = 0;
        for (Point spot : spots) {
            if (placed >= count) break;
            Square s = game.getBoard().getSquare(spot.r(), spot.c());
            if (playerId.equals("p1")) s.setP1Ambush(true);
            else s.setP2Ambush(true);
            placed++;
        }
        if (playerId.equals("p1")) game.setP1AmbushesPlacedThisRound(game.getP1AmbushesPlacedThisRound() + placed);
        else game.setP2AmbushesPlacedThisRound(game.getP2AmbushesPlacedThisRound() + placed);

        if (playerId.equals("p1") && game.getP1AmbushesPlacedThisRound() == 2) game.disarmTimer("p1");
        if (playerId.equals("p2") && game.getP2AmbushesPlacedThisRound() == 2) game.disarmTimer("p2");
    }

    private void performRandomPlacement(Game game, String playerId) {
        List<Point> spots = getValidPlacementSpots(game.getBoard());
        if (spots.isEmpty()) { endGame(game); return; }
        Point spot = spots.get(0);

        Square square = game.getBoard().getSquare(spot.r(), spot.c());
        if (square.hasAmbush()) {
            if (square.isP1Ambush()) game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
            if (square.isP2Ambush()) game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
            square.clearAmbushes();
        } else {
            square.setOwnerId(playerId);
        }

        game.setPlacementsMadeThisTurn(game.getPlacementsMadeThisTurn() + 1);
        if (game.getPlacementsMadeThisTurn() == 3) {
            if (game.getCurrentTurnPlayerId().equals(game.getPlacementRoundStarter())) {
                game.setCurrentTurnPlayerId(game.getOpponentId(game.getPlacementRoundStarter()));
                game.setPlacementsMadeThisTurn(0);
                game.startTimer(game.getCurrentTurnPlayerId(), 15);
                game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
            } else {
                transitionToNextRound(game);
            }
        } else {
            game.startTimer(game.getCurrentTurnPlayerId(), 15);
        }
    }

    private void performRandomExtraPlacement(Game game, String playerId) {
        List<Point> spots = getValidPlacementSpots(game.getBoard());
        if (spots.isEmpty()) { endGame(game); return; }
        Point spot = spots.get(0);

        Square square = game.getBoard().getSquare(spot.r(), spot.c());
        if (square.hasAmbush()) {
            square.clearAmbushes();
        } else {
            square.setOwnerId(playerId);
        }

        game.getPlayer(playerId).setExtraTurns(game.getPlayer(playerId).getExtraTurns() - 1);

        Player mover = game.getPlayer(playerId);
        Player opponent = game.getPlayer(game.getOpponentId(playerId));
        if (opponent.getExtraTurns() > 0) {
            game.setCurrentTurnPlayerId(opponent.getId());
        } else if (mover.getExtraTurns() > 0) {
            game.setCurrentTurnPlayerId(mover.getId());
        } else {
            endGame(game);
            return;
        }

        game.startTimer(game.getCurrentTurnPlayerId(), 15);
        game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
    }

    private List<Point> getValidPlacementSpots(Board board) {
        List<Point> spots = new ArrayList<>();
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                if (board.getSquare(r, c).getOwnerId() == null) {
                    spots.add(new Point(r, c));
                }
            }
        }
        return spots;
    }

    private List<Point> getValidAmbushSpots(Board board, String playerId) {
        List<Point> spots = new ArrayList<>();
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                Square s = board.getSquare(r, c);
                if (s.getOwnerId() != null || (playerId.equals("p1") && s.isP2Ambush()) || (playerId.equals("p2") && s.isP1Ambush()) || (playerId.equals("p1") && s.isP1Ambush()) || (playerId.equals("p2") && s.isP2Ambush())) {
                    continue;
                }
                spots.add(new Point(r, c));
            }
        }
        return spots;
    }


    // --- Internal Game Logic and State Transitions ---

    private Game findGame(String gameId) {
        Game game = activeGames.get(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }
        return game;
    }

    private void transitionToPlacement(Game game) {
        game.setPhase(GamePhase.PLACEMENT);
        game.setPlacementsMadeThisTurn(0);

        int round = game.getCurrentRound();
        if (round == 1 || round == 4) {
            game.setPlacementRoundStarter(game.getFirstMovePlayerId());
        } else {
            game.setPlacementRoundStarter(game.getOpponentId(game.getFirstMovePlayerId()));
        }
        game.setCurrentTurnPlayerId(game.getPlacementRoundStarter());

        game.startTimer(game.getCurrentTurnPlayerId(), 15);
        game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
    }

    private void handlePlacementPhase(Game game, MoveRequest move) {
        if (!game.getCurrentTurnPlayerId().equals(move.getPlayerId())) {
            throw new IllegalStateException("Not your turn");
        }
        Square square = game.getBoard().getSquare(move.getR(), move.getC());
        if (square.getOwnerId() != null) throw new IllegalStateException("Square already occupied");

        if (square.hasAmbush()) {
            if (square.isP1Ambush()) game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
            if (square.isP2Ambush()) game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
            square.clearAmbushes();
        } else {
            square.setOwnerId(move.getPlayerId());
        }

        game.setPlacementsMadeThisTurn(game.getPlacementsMadeThisTurn() + 1);

        if (game.getPlacementsMadeThisTurn() == 3) {
            if (game.getCurrentTurnPlayerId().equals(game.getPlacementRoundStarter())) {
                game.setCurrentTurnPlayerId(game.getOpponentId(game.getPlacementRoundStarter()));
                game.setPlacementsMadeThisTurn(0);
                game.startTimer(game.getCurrentTurnPlayerId(), 15);
                game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
            } else {
                transitionToNextRound(game);
            }
        } else {
            game.startTimer(game.getCurrentTurnPlayerId(), 15);
        }
    }

    private void transitionToNextRound(Game game) {
        game.setCurrentRound(game.getCurrentRound() + 1);
        if (game.getCurrentRound() > 4) {
            transitionToExtraRounds(game);
        } else {
            game.resetForAmbushPhase();
            game.startTimer("p1", 15);
            game.startTimer("p2", 15);
        }
    }

    private void transitionToExtraRounds(Game game) {
        game.setPhase(GamePhase.EXTRA_ROUNDS);

        if (game.getP1().getExtraTurns() == 0 && game.getP2().getExtraTurns() == 0) {
            endGame(game);
            return;
        }

        if (game.getP1().getExtraTurns() > game.getP2().getExtraTurns()) {
            game.setCurrentTurnPlayerId("p1");
        } else if (game.getP2().getExtraTurns() > game.getP1().getExtraTurns()) {
            game.setCurrentTurnPlayerId("p2");
        } else {
            game.setCurrentTurnPlayerId(game.getFirstMovePlayerId());
        }

        game.startTimer(game.getCurrentTurnPlayerId(), 15);
        game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
    }

    private void handleExtraRoundsPhase(Game game, MoveRequest move) {
        Player mover = game.getPlayer(move.getPlayerId());
        if (!game.getCurrentTurnPlayerId().equals(move.getPlayerId()) || mover.getExtraTurns() <= 0) {
            throw new IllegalStateException("Not your turn or no extra turns left");
        }
        Square square = game.getBoard().getSquare(move.getR(), move.getC());
        if (square.getOwnerId() != null) throw new IllegalStateException("Square already occupied");

        if (square.hasAmbush()) {
            square.clearAmbushes();
        } else {
            square.setOwnerId(move.getPlayerId());
        }
        mover.setExtraTurns(mover.getExtraTurns() - 1);

        Player opponent = game.getPlayer(game.getOpponentId(move.getPlayerId()));
        if (opponent.getExtraTurns() > 0) {
            game.setCurrentTurnPlayerId(opponent.getId());
        } else if (mover.getExtraTurns() > 0) {
            game.setCurrentTurnPlayerId(mover.getId());
        } else {
            endGame(game);
            return;
        }

        game.startTimer(game.getCurrentTurnPlayerId(), 15);
        game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
    }

    private void endGame(Game game) {
        game.setPhase(GamePhase.GAME_OVER);
        game.disarmTimer("p1");
        game.disarmTimer("p2");

        int p1MaxConnection = calculateMaxConnection(game.getBoard(), "p1");
        int p2MaxConnection = calculateMaxConnection(game.getBoard(), "p2");
        int p1Pieces = countPieces(game.getBoard(), "p1");
        int p2Pieces = countPieces(game.getBoard(), "p2");

        GameResult result = new GameResult();
        result.setP1MaxConnection(p1MaxConnection);
        result.setP2MaxConnection(p2MaxConnection);
        result.setP1PieceCount(p1Pieces);
        result.setP2PieceCount(p2Pieces);

        if (p1MaxConnection > p2MaxConnection) result.setWinnerId("p1");
        else if (p2MaxConnection > p1MaxConnection) result.setWinnerId("p2");
        else {
            if (p1Pieces > p2Pieces) result.setWinnerId("p1");
            else if (p2Pieces > p1Pieces) result.setWinnerId("p2");
            else result.setWinnerId("DRAW");
        }

        game.setResult(result);

        if ("p1".equals(result.getWinnerId())) {
            userService.applyGameResult(game.getP1().getUsername(), game.getP2().getUsername());
        } else if ("p2".equals(result.getWinnerId())) {
            userService.applyGameResult(game.getP2().getUsername(), game.getP1().getUsername());
        }

        broadcastGameState(game.getGameId());
    }

    // --- Scoring and Helper Methods ---
    private int calculateMaxConnection(Board board, String playerId) {
        int max = 0;
        boolean[][] visited = new boolean[6][6];
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                if (playerId.equals(board.getSquare(r, c).getOwnerId()) && !visited[r][c]) {
                    int size = dfs(board, visited, r, c, playerId);
                    max = Math.max(max, size);
                }
            }
        }
        return max;
    }

    private int dfs(Board board, boolean[][] visited, int r, int c, String playerId) {
        if (r < 0 || r >= 6 || c < 0 || c >= 6 || visited[r][c] || !playerId.equals(board.getSquare(r, c).getOwnerId())) {
            return 0;
        }
        visited[r][c] = true;
        int count = 1;
        count += dfs(board, visited, r + 1, c, playerId);
        count += dfs(board, visited, r - 1, c, playerId);
        count += dfs(board, visited, r, c + 1, playerId);
        count += dfs(board, visited, r, c - 1, playerId);
        return count;
    }

    private int countPieces(Board board, String playerId) {
        int count = 0;
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                if (playerId.equals(board.getSquare(r, c).getOwnerId())) {
                    count++;
                }
            }
        }
        return count;
    }


    // --- DTO Mapping ---
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
        dto.setP1TimeLeft(game.getP1ActionDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getP1ActionDeadline() - now));
        dto.setP2TimeLeft(game.getP2ActionDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getP2ActionDeadline() - now));

        return dto;
    }

    private String generateStatusMessage(Game game) {
        Player p1 = game.getP1();
        Player p2 = game.getP2();
        switch (game.getPhase()) {
            case PRE_GAME:
                return "游戏即将开始... 准备！";
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
                if (game.getCurrentTurnPlayerId() == null) return "额外轮次...准备中";
                Player extraTurnPlayer = game.getPlayer(game.getCurrentTurnPlayerId());
                return String.format("额外轮次 (15秒). 轮到 %s (%s). 剩余次数: P1[%d], P2[%d]",
                        extraTurnPlayer.getId(), extraTurnPlayer.getUsername(),
                        p1.getExtraTurns(), p2.getExtraTurns());
            case GAME_OVER:
                GameResult res = game.getResult();
                if ("DRAW".equals(res.getWinnerId())) return "游戏结束: 平局!";
                Player winner = game.getPlayer(res.getWinnerId());
                return String.format("游戏结束! 胜利者: %s (%s). [P1: %d连/%d子, P2: %d连/%d子]",
                        winner.getId(), winner.getUsername(), res.getP1MaxConnection(), res.getP1PieceCount(),
                        res.getP2MaxConnection(), res.getP2PieceCount());
            default:
                return "等待中...";
        }
    }
}