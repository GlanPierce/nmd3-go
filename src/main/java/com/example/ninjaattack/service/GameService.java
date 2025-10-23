package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.*;
import com.example.ninjaattack.model.dto.GameStateDTO;
import com.example.ninjaattack.model.dto.MoveRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private final Map<String, Game> activeGames = new ConcurrentHashMap<>();
    private final UserService userService;

    public GameService(UserService userService) {
        this.userService = userService;
    }

    // --- 核心：计划任务时钟 ---
    /**
     * 每秒钟检查一次所有活跃的游戏是否有超时。
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
                // 处理超时
                handleTimeout(game, timedOutPlayerId);
            }
        }
    }

    /**
     * 处理超时的核心逻辑。
     * 必须是 synchronized，以防止与玩家的正常操作冲突。
     */
    private synchronized void handleTimeout(Game game, String timedOutPlayerId) {
        // 再次检查，防止在进入此方法时玩家刚好完成了操作
        long now = System.currentTimeMillis();
        if (timedOutPlayerId.equals("p1") && now < game.getP1ActionDeadline()) return;
        if (timedOutPlayerId.equals("p2") && now < game.getP2ActionDeadline()) return;
        if (game.getPhase() == GamePhase.GAME_OVER) return;

        System.out.println("处理超时: 玩家 " + timedOutPlayerId + " 游戏 " + game.getGameId());

        // 立即解除计时器，防止重复触发
        game.disarmTimer(timedOutPlayerId);

        if (game.getPhase() == GamePhase.AMBUSH) {
            int remaining = 2 - (timedOutPlayerId.equals("p1") ? game.getP1AmbushesPlacedThisRound() : game.getP2AmbushesPlacedThisRound());
            if (remaining > 0) {
                // 规则：随机放置剩余的伏兵
                performRandomAmbush(game, timedOutPlayerId, remaining);
            }
            // 检查是否双方都完成了（一个手动，一个超时）
            if (game.getP1AmbushesPlacedThisRound() == 2 && game.getP2AmbushesPlacedThisRound() == 2) {
                transitionToPlacement(game);
            }
        } else if (game.getPhase() == GamePhase.PLACEMENT) {
            if (timedOutPlayerId.equals(game.getCurrentTurnPlayerId())) {
                // 规则：随机落子
                performRandomPlacement(game, timedOutPlayerId);
            }
        } else if (game.getPhase() == GamePhase.EXTRA_ROUNDS) {
            if (timedOutPlayerId.equals(game.getCurrentTurnPlayerId())) {
                // 规则：随机落子
                performRandomExtraPlacement(game, timedOutPlayerId);
            }
        }
    }

    // --- 随机移动的实现 ---

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

        // 完成后解除计时器
        if (playerId.equals("p1") && game.getP1AmbushesPlacedThisRound() == 2) game.disarmTimer("p1");
        if (playerId.equals("p2") && game.getP2AmbushesPlacedThisRound() == 2) game.disarmTimer("p2");
    }

    private void performRandomPlacement(Game game, String playerId) {
        List<Point> spots = getValidPlacementSpots(game.getBoard());
        if (spots.isEmpty()) { endGame(game); return; } // 棋盘满了
        Collections.shuffle(spots);
        Point spot = spots.get(0);

        // 模拟 handlePlacementPhase 的核心逻辑
        Square square = game.getBoard().getSquare(spot.r(), spot.c());
        if (square.hasAmbush()) {
            if (square.isP1Ambush()) game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
            if (square.isP2Ambush()) game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
            square.clearAmbushes();
            square.setOwnerId(null);
        } else {
            square.setOwnerId(playerId);
        }

        // 模拟回合转换逻辑
        game.setPlacementsMadeThisTurn(game.getPlacementsMadeThisTurn() + 1);
        if (game.getPlacementsMadeThisTurn() == 3) {
            if (game.getCurrentTurnPlayerId().equals(game.getPlacementRoundStarter())) {
                game.setCurrentTurnPlayerId(game.getOpponentId(game.getPlacementRoundStarter()));
                game.setPlacementsMadeThisTurn(0);
                // 启动下一位玩家的计时器
                game.startTimer(game.getCurrentTurnPlayerId(), 15);
                game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
            } else {
                transitionToNextRound(game); // 这个方法会处理下一阶段的计时器
                return; // 提前返回
            }
        } else {
            // 还在同一玩家的回合，重置计时器
            game.startTimer(game.getCurrentTurnPlayerId(), 15);
        }
    }

    private void performRandomExtraPlacement(Game game, String playerId) {
        List<Point> spots = getValidPlacementSpots(game.getBoard());
        if (spots.isEmpty()) { endGame(game); return; } // 棋盘满了
        Collections.shuffle(spots);
        Point spot = spots.get(0);

        // 模拟 handleExtraRoundsPhase 的核心逻辑
        Square square = game.getBoard().getSquare(spot.r(), spot.c());
        if (square.hasAmbush()) {
            square.clearAmbushes();
            square.setOwnerId(null);
        } else {
            square.setOwnerId(playerId);
        }

        game.getPlayer(playerId).setExtraTurns(game.getPlayer(playerId).getExtraTurns() - 1);

        // 模拟回合转换
        Player mover = game.getPlayer(playerId);
        Player opponent = game.getPlayer(game.getOpponentId(playerId));
        if (opponent.getExtraTurns() > 0) {
            game.setCurrentTurnPlayerId(opponent.getId());
        } else if (mover.getExtraTurns() > 0) {
            game.setCurrentTurnPlayerId(mover.getId());
        } else {
            endGame(game);
            return; // 游戏结束
        }

        // 为下一位玩家启动计时器
        game.startTimer(game.getCurrentTurnPlayerId(), 15);
        game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
    }

    // --- 随机移动的辅助方法 ---
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
                if (s.getOwnerId() != null) continue;

                // 不能在对方伏兵上设置伏兵
                if (playerId.equals("p1") && s.isP2Ambush()) continue;
                if (playerId.equals("p2") && s.isP1Ambush()) continue;

                // 不能在自己的伏兵上重复设置
                if (playerId.equals("p1") && s.isP1Ambush()) continue;
                if (playerId.equals("p2") && s.isP2Ambush()) continue;

                spots.add(new Point(r, c));
            }
        }
        return spots;
    }


    // --- 核心游戏 API (现在是 synchronized) ---

    public synchronized GameStateDTO createGame(String p1Username, String p2Username) {
        Game game = new Game(p1Username, p2Username);
        activeGames.put(game.getGameId(), game);
        return mapToDTO(game);
    }

    // getGameState 不需要 synchronized，因为它只是读取
    public GameStateDTO getGameState(String gameId) {
        Game game = findGame(gameId);
        return mapToDTO(game);
    }

    public synchronized GameStateDTO placeAmbush(String gameId, MoveRequest move) {
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
            if(game.getP1AmbushesPlacedThisRound() == 2) game.disarmTimer("p1");
        } else {
            if (square.isP1Ambush()) throw new IllegalStateException("Cannot place ambush on an opponent's ambush");
            if (square.isP2Ambush()) throw new IllegalStateException("Cannot place ambush on your own ambush");
            square.setP2Ambush(true);
            game.setP2AmbushesPlacedThisRound(game.getP2AmbushesPlacedThisRound() + 1);
            if(game.getP2AmbushesPlacedThisRound() == 2) game.disarmTimer("p2");
        }

        if (game.getP1AmbushesPlacedThisRound() == 2 &&
                game.getP2AmbushesPlacedThisRound() == 2) {
            transitionToPlacement(game);
        }

        return mapToDTO(game);
    }

    public synchronized GameStateDTO placePiece(String gameId, MoveRequest move) {
        Game game = findGame(gameId);

        if (game.getPhase() == GamePhase.PLACEMENT) {
            return handlePlacementPhase(game, move);
        } else if (game.getPhase() == GamePhase.EXTRA_ROUNDS) {
            return handleExtraRoundsPhase(game, move);
        } else {
            throw new IllegalStateException("Not in a placement phase");
        }
    }

    // --- 内部逻辑辅助方法 ---

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

        // 启动落子计时器
        game.startTimer(game.getCurrentTurnPlayerId(), 15);
        game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
    }

    private GameStateDTO handlePlacementPhase(Game game, MoveRequest move) {
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
                // 轮到后手
                game.setCurrentTurnPlayerId(game.getOpponentId(game.getPlacementRoundStarter()));
                game.setPlacementsMadeThisTurn(0);
                // 启动后手计时器
                game.startTimer(game.getCurrentTurnPlayerId(), 15);
                game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
            } else {
                // 本轮结束
                transitionToNextRound(game);
                return mapToDTO(game); // 提前返回
            }
        } else {
            // 还在同一玩家的回合，重置计时器
            game.startTimer(game.getCurrentTurnPlayerId(), 15);
        }

        return mapToDTO(game);
    }

    private void transitionToNextRound(Game game) {
        game.setCurrentRound(game.getCurrentRound() + 1);
        if (game.getCurrentRound() > 4) {
            transitionToExtraRounds(game);
        } else {
            // resetForAmbushPhase 会自动启动伏兵计时器
            game.resetForAmbushPhase();
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

        // 启动额外轮次计时器
        game.startTimer(game.getCurrentTurnPlayerId(), 15);
        game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));
    }

    private GameStateDTO handleExtraRoundsPhase(Game game, MoveRequest move) {
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
            return mapToDTO(game); // 游戏结束，提前返回
        }

        // 为下一位玩家启动计时器
        game.startTimer(game.getCurrentTurnPlayerId(), 15);
        game.disarmTimer(game.getOpponentId(game.getCurrentTurnPlayerId()));

        return mapToDTO(game);
    }

    private void endGame(Game game) {
        game.setPhase(GamePhase.GAME_OVER);
        // 游戏结束，解除所有计时器
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
    }

    // --- 计分辅助方法 ---
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
        // 规则: 仅横向或竖向
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


    // --- DTO 映射 ---
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

        // 填充剩余时间
        long now = System.currentTimeMillis();
        dto.setP1TimeLeft(game.getP1ActionDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getP1ActionDeadline() - now));
        dto.setP2TimeLeft(game.getP2ActionDeadline() == Long.MAX_VALUE ? -1 : Math.max(0, game.getP2ActionDeadline() - now));

        return dto;
    }

    // --- 状态信息 ---
    private String generateStatusMessage(Game game) {
        Player p1 = game.getP1();
        Player p2 = game.getP2();
        switch (game.getPhase()) {
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