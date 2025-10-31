package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.*;
import com.example.ninjaattack.model.dto.GameStateDTO;
import com.example.ninjaattack.model.dto.MoveRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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

    private final Map<String, Set<String>> readyPlayersByGame = new ConcurrentHashMap<>();

    public GameService(UserService userService, SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler) {
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
    }

    // --- 核心：计划任务时钟 (不变) ---
    @Scheduled(fixedRate = 1000)
    public void checkGameTimeouts() {
        long now = System.currentTimeMillis();
        for (String gameId : activeGames.keySet()) {
            Game game = activeGames.get(gameId);
            if (game == null) continue;

            // 检查 30 秒的“匹配确认”超时
            if (game.getPhase() == GamePhase.PRE_GAME && now > game.getConfirmationDeadline()) {
                System.out.println("游戏 " + gameId + " 匹配确认超时。");
                handleMatchTimeout(game);
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
                    handleTimeout(game, timedOutPlayerId);
                    if (game.getPhase() != GamePhase.GAME_OVER) {
                        broadcastGameState(game.getGameId());
                    }
                }
            }
        }
    }

    /**
     * (新增) 处理 30 秒匹配确认超时
     */
    private synchronized void handleMatchTimeout(Game game) {
        // 广播取消消息
        broadcastGameState(game.getGameId(), GamePhase.MATCH_CANCELLED, "有玩家未能在30秒内确认准备。");
        // 清理游戏
        cleanupGame(game.getGameId());
    }

    /**
     * (新增) 广播一个特定的状态 (用于 PRE_GAME 和 MATCH_CANCELLED)
     */
    public void broadcastGameState(String gameId, GamePhase phase, String message) {
        Game game = findGame(gameId);
        if (game == null) return;

        GameStateDTO dto = new GameStateDTO();
        dto.setGameId(gameId);
        dto.setPhase(phase);
        dto.setStatusMessage(message);

        // (新增) 关键修复：发送 P1 和 P2 的用户名
        dto.setP1Username(game.getP1().getUsername());
        dto.setP2Username(game.getP2().getUsername());

        messagingTemplate.convertAndSend("/topic/game/" + gameId, dto);
    }

    /**
     * (不变) 广播当前游戏状态
     */
    public void broadcastGameState(String gameId) {
        Game game = findGame(gameId);
        if (game == null) return;

        GameStateDTO dto = mapToDTO(game);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, dto);
    }

    /**
     * (新增) 封装的游戏清理逻辑
     */
    private void cleanupGame(String gameId) {
        activeGames.remove(gameId);
        readyPlayersByGame.remove(gameId);
    }

    // --- 核心游戏 API ---

    /**
     * (不变) createGame 现在设置 30 秒确认计时器
     */
    public synchronized Game createGame(String p1Username, String p2Username) {
        Game game = new Game(p1Username, p2Username); // 处于 PRE_GAME

        game.setConfirmationDeadline(System.currentTimeMillis() + 30000L);

        activeGames.put(game.getGameId(), game);
        readyPlayersByGame.put(game.getGameId(), new HashSet<>());

        return game; // 返回 Game 对象
    }

    /**
     * (修改) 玩家点击“准备”后调用
     */
    public synchronized void playerReady(String gameId, String playerId) {
        Game game = findGame(gameId);
        if (game == null || game.getPhase() != GamePhase.PRE_GAME) {
            return;
        }

        Set<String> readyPlayers = readyPlayersByGame.get(gameId);
        if (readyPlayers == null) return;

        readyPlayers.add(playerId);
        System.out.println("玩家 " + playerId + " 已准备 (游戏: " + gameId + ")");

        // (修改) 使用新的广播方法，发送当前玩家的准备状态
        // 关键：发送的消息体是 "玩家 p1 已准备!"，前端负责将 p1 替换成用户名
        broadcastGameState(game.getGameId(), GamePhase.PRE_GAME, "玩家 " + playerId + " 已准备!");

        if (readyPlayers.contains("p1") && readyPlayers.contains("p2")) {
            System.out.println("双方准备就绪, 游戏 " + gameId + " 开始!");

            game.setConfirmationDeadline(Long.MAX_VALUE);

            startGame(gameId);
        }
    }


    /**
     * (不变) 游戏正式开始
     */
    public synchronized void startGame(String gameId) {
        Game game = findGame(gameId);
        if (game == null || game.getPhase() != GamePhase.PRE_GAME) {
            return;
        }

        game.resetForAmbushPhase();
        game.startTimer("p1", 15);
        game.startTimer("p2", 15);

        broadcastGameState(gameId);
    }


    /**
     * (不变) 处理伏兵放置 (WebSocket)
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
            square.setP1Ambush(true);
            game.setP1AmbushesPlacedThisRound(game.getP1AmbushesPlacedThisRound() + 1);
            if (game.getP1AmbushesPlacedThisRound() == 2) game.disarmTimer("p1");
        } else {
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
     * (不变) 处理落子 (WebSocket)
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

        if (game.getPhase() != GamePhase.GAME_OVER) {
            broadcastGameState(gameId);
        }
    }

    // --- 超时和随机移动逻辑 (不变) ---

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

        Collections.shuffle(spots);
        Point spot = spots.get(0);

        Square square = game.getBoard().getSquare(spot.r(), spot.c());
        if (square.hasAmbush()) {
            if (square.isP1Ambush()) game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
            if (square.isP2Ambush()) game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
            square.clearAmbushes();
            square.setOwnerId(null);
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

        Collections.shuffle(spots);
        Point spot = spots.get(0);

        Square square = game.getBoard().getSquare(spot.r(), spot.c());
        if (square.hasAmbush()) {
            square.clearAmbushes();
            square.setOwnerId(null);
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
                if (s.getOwnerId() != null) {
                    continue;
                }
                spots.add(new Point(r, c));
            }
        }
        return spots;
    }


    // --- 内部游戏逻辑和状态转换 (不变) ---

    private Game findGame(String gameId) {
        Game game = activeGames.get(gameId);
        if (game == null) {
            return null;
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
            square.setOwnerId(null);
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
            square.setOwnerId(null);
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

        cleanupGame(game.getGameId());
    }

    // --- 计分和辅助方法 (不变) ---
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


    // --- DTO 映射 (不变) ---
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
        // (修改) 检查游戏阶段，只在 PRE_GAME 阶段发送确认倒计时
        if (game.getPhase() == GamePhase.PRE_GAME) {
            long timeLeft = game.getConfirmationDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getConfirmationDeadline() - now);
            dto.setP1TimeLeft(timeLeft); // 双方共享30秒倒计时
            dto.setP2TimeLeft(timeLeft);
        } else {
            dto.setP1TimeLeft(game.getP1ActionDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getP1ActionDeadline() - now));
            dto.setP2TimeLeft(game.getP2ActionDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getP2ActionDeadline() - now));
        }

        return dto;
    }

    // --- 状态信息 (不变) ---
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
            case MATCH_CANCELLED:
                return "匹配已取消。";
            default:
                return "等待中...";
        }
    }
}