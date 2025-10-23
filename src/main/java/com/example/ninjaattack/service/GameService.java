package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.*;
import com.example.ninjaattack.model.dto.GameStateDTO;
import com.example.ninjaattack.model.dto.MoveRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    // 使用 Map 存储活跃的游戏, 替代数据库
    private final Map<String, Game> activeGames = new ConcurrentHashMap<>();
    private final UserService userService;

    public GameService(UserService userService) {
        this.userService = userService;
    }

    public GameStateDTO createGame(String p1Username, String p2Username) {
        Game game = new Game(p1Username, p2Username);
        activeGames.put(game.getGameId(), game);
        return mapToDTO(game);
    }

    public GameStateDTO getGameState(String gameId) {
        Game game = findGame(gameId);
        return mapToDTO(game);
    }

    public GameStateDTO placeAmbush(String gameId, MoveRequest move) {
        Game game = findGame(gameId);
        if (game.getPhase() != GamePhase.AMBUSH) {
            throw new IllegalStateException("Not in AMBUSH phase");
        }

        String playerId = move.getPlayerId();

        // 1. 检查是否已放满
        if (playerId.equals("p1")) {
            if (game.getP1AmbushesPlacedThisRound() >= 2) {
                throw new IllegalStateException("P1 Already placed 2 ambushes");
            }
        } else {
            if (game.getP2AmbushesPlacedThisRound() >= 2) {
                throw new IllegalStateException("P2 Already placed 2 ambushes");
            }
        }

        Square square = game.getBoard().getSquare(move.getR(), move.getC());
        if (square.getOwnerId() != null) {
            throw new IllegalStateException("Cannot place ambush on occupied square");
        }

        // 2. 立即应用伏兵到 Board
        if (playerId.equals("p1")) {
            // (新规则) 检查自己是否踩到对方伏兵
            if (square.isP2Ambush()) {
                throw new IllegalStateException("Cannot place ambush on an opponent's ambush");
            }
            square.setP1Ambush(true);
            game.setP1AmbushesPlacedThisRound(game.getP1AmbushesPlacedThisRound() + 1);
        } else {
            // (新规则) 检查自己是否踩到对方伏兵
            if (square.isP1Ambush()) {
                throw new IllegalStateException("Cannot place ambush on an opponent's ambush");
            }
            square.setP2Ambush(true);
            game.setP2AmbushesPlacedThisRound(game.getP2AmbushesPlacedThisRound() + 1);
        }

        // 3. 检查是否双方都完成了伏兵设置
        if (game.getP1AmbushesPlacedThisRound() == 2 &&
                game.getP2AmbushesPlacedThisRound() == 2) {

            // 伏兵已在 Board 上, 直接转换到落子阶段
            transitionToPlacement(game);
        }

        // 4. 返回包含更新后 Board 的 DTO (这样前端就能立刻渲染)
        return mapToDTO(game);
    }

    public GameStateDTO placePiece(String gameId, MoveRequest move) {
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

        // 根据规则决定谁先落子
        int round = game.getCurrentRound();
        if (round == 1 || round == 4) {
            game.setPlacementRoundStarter(game.getFirstMovePlayerId());
        } else { // R2, R3
            game.setPlacementRoundStarter(game.getOpponentId(game.getFirstMovePlayerId()));
        }
        game.setCurrentTurnPlayerId(game.getPlacementRoundStarter());
    }

    // --- (新规则) 此方法已更新 ---
    private GameStateDTO handlePlacementPhase(Game game, MoveRequest move) {
        if (!game.getCurrentTurnPlayerId().equals(move.getPlayerId())) {
            throw new IllegalStateException("Not your turn");
        }

        Square square = game.getBoard().getSquare(move.getR(), move.getC());
        if (square.getOwnerId() != null) {
            throw new IllegalStateException("Square already occupied");
        }

        // (新规则) 检查是否触发任何伏兵 (包括自己的)
        if (square.hasAmbush()) {

            // 规则: 伏兵的*主人*获得额外机会
            if (square.isP1Ambush()) {
                game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
            }
            if (square.isP2Ambush()) {
                game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
            }

            // 1. 伏兵和落子均失效
            square.clearAmbushes();
            square.setOwnerId(null); // 确保落子失败

        } else {
            // 正常落子
            square.setOwnerId(move.getPlayerId());
        }

        // --- 轮转逻辑 (不变) ---
        game.setPlacementsMadeThisTurn(game.getPlacementsMadeThisTurn() + 1);

        if (game.getPlacementsMadeThisTurn() == 3) {
            // 当前玩家的3次落子结束
            if (game.getCurrentTurnPlayerId().equals(game.getPlacementRoundStarter())) {
                // 如果是先手刚下完, 轮到后手
                game.setCurrentTurnPlayerId(game.getOpponentId(game.getPlacementRoundStarter()));
                game.setPlacementsMadeThisTurn(0);
            } else {
                // 如果是后手刚下完, 本轮常规轮次结束
                transitionToNextRound(game);
            }
        }

        return mapToDTO(game);
    }

    private void transitionToNextRound(Game game) {
        game.setCurrentRound(game.getCurrentRound() + 1);
        if (game.getCurrentRound() > 4) {
            // --- 进入额外轮次 ---
            transitionToExtraRounds(game);
        } else {
            // --- 进入下一轮的伏兵阶段 ---
            game.resetForAmbushPhase();
        }
    }

    private void transitionToExtraRounds(Game game) {
        game.setPhase(GamePhase.EXTRA_ROUNDS);

        // 检查是否有人有额外次数
        if (game.getP1().getExtraTurns() == 0 && game.getP2().getExtraTurns() == 0) {
            endGame(game); // 没有额外轮次, 直接结束
            return;
        }

        // 额外落子次数更多的忍者获得先手
        if (game.getP1().getExtraTurns() > game.getP2().getExtraTurns()) {
            game.setCurrentTurnPlayerId("p1");
        } else if (game.getP2().getExtraTurns() > game.getP1().getExtraTurns()) {
            game.setCurrentTurnPlayerId("p2");
        } else {
            // 次数一样多, 规则未定, 我们用 R1/R4 的先手
            game.setCurrentTurnPlayerId(game.getFirstMovePlayerId());
        }
    }

    // --- (新规则) 此方法已更新 ---
    private GameStateDTO handleExtraRoundsPhase(Game game, MoveRequest move) {
        Player mover = game.getPlayer(move.getPlayerId());
        if (!game.getCurrentTurnPlayerId().equals(move.getPlayerId()) || mover.getExtraTurns() <= 0) {
            throw new IllegalStateException("Not your turn or no extra turns left");
        }

        Square square = game.getBoard().getSquare(move.getR(), move.getC());
        if (square.getOwnerId() != null) {
            throw new IllegalStateException("Square already occupied");
        }

        // (新规则) 检查是否触发任何伏兵 (包括自己的)
        // 规则: 仍然触发, 但不给额外次数
        if (square.hasAmbush()) {
            square.clearAmbushes();
            square.setOwnerId(null);
        } else {
            square.setOwnerId(move.getPlayerId());
        }

        // 消耗一次额外机会
        mover.setExtraTurns(mover.getExtraTurns() - 1);

        // --- 额外轮次轮转逻辑 (不变) ---
        Player opponent = game.getPlayer(game.getOpponentId(move.getPlayerId()));
        if (opponent.getExtraTurns() > 0) {
            game.setCurrentTurnPlayerId(opponent.getId());
        } else if (mover.getExtraTurns() > 0) {
            // 对手没了, 自己还有, 继续自己
            game.setCurrentTurnPlayerId(mover.getId());
        } else {
            // 双方都用完了
            endGame(game);
        }

        return mapToDTO(game);
    }

    private void endGame(Game game) {
        game.setPhase(GamePhase.GAME_OVER);

        // 1. 计算最大连接数
        int p1MaxConnection = calculateMaxConnection(game.getBoard(), "p1");
        int p2MaxConnection = calculateMaxConnection(game.getBoard(), "p2");

        // 2. 计算棋盘落子数
        int p1Pieces = countPieces(game.getBoard(), "p1");
        int p2Pieces = countPieces(game.getBoard(), "p2");

        GameResult result = new GameResult();
        result.setP1MaxConnection(p1MaxConnection);
        result.setP2MaxConnection(p2MaxConnection);
        result.setP1PieceCount(p1Pieces);
        result.setP2PieceCount(p2Pieces);

        // 胜负判定
        if (p1MaxConnection > p2MaxConnection) {
            result.setWinnerId("p1");
        } else if (p2MaxConnection > p1MaxConnection) {
            result.setWinnerId("p2");
        } else {
            // 最大连接数相同, 比较落子数
            if (p1Pieces > p2Pieces) {
                result.setWinnerId("p1");
            } else if (p2Pieces > p1Pieces) {
                result.setWinnerId("p2");
            } else {
                result.setWinnerId("DRAW"); // 平局
            }
        }

        game.setResult(result);

        // 积分结算
        if ("p1".equals(result.getWinnerId())) {
            userService.applyGameResult(game.getP1().getUsername(), game.getP2().getUsername());
        } else if ("p2".equals(result.getWinnerId())) {
            userService.applyGameResult(game.getP2().getUsername(), game.getP1().getUsername());
        }
    }

    // --- 计分辅助方法 (不变) ---

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

    // --- DTO 映射 (不变, 伏兵逻辑已修正) ---
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

        // 生成状态信息
        dto.setStatusMessage(generateStatusMessage(game));
        return dto;
    }

    private String generateStatusMessage(Game game) {
        Player p1 = game.getP1();
        Player p2 = game.getP2();
        switch (game.getPhase()) {
            case AMBUSH:
                return String.format("第 %d 轮 - 伏兵阶段. P1 (%s) 已设置 %d/2, P2 (%s) 已设置 %d/2.",
                        game.getCurrentRound(), p1.getUsername(),
                        game.getP1AmbushesPlacedThisRound(),
                        p2.getUsername(),
                        game.getP2AmbushesPlacedThisRound());
            case PLACEMENT:
                Player turnPlayer = game.getPlayer(game.getCurrentTurnPlayerId());
                return String.format("第 %d 轮 - 落子阶段. (新规则) 轮到 %s (%s) 落子 (%d/3).",
                        game.getCurrentRound(), turnPlayer.getId(), turnPlayer.getUsername(),
                        game.getPlacementsMadeThisTurn() + 1);
            case EXTRA_ROUNDS:
                if (game.getCurrentTurnPlayerId() == null) return "额外轮次...准备中";
                Player extraTurnPlayer = game.getPlayer(game.getCurrentTurnPlayerId());
                return String.format("额外轮次. 轮到 %s (%s). 剩余次数: P1[%d], P2[%d]",
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