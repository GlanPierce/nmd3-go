package com.example.ninjaattack.logic;

import com.example.ninjaattack.model.domain.*;
import com.example.ninjaattack.model.dto.MoveRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure Java class containing all game rules and logic.
 * Stateless and thread-safe (assuming Game object is confined or synchronized
 * externally).
 */
public class GameEngine {

    public void startGame(Game game) {
        if (game.getPhase() != GamePhase.PRE_GAME) {
            return;
        }

        game.resetForAmbushPhase();
        // Timers are managed by the Service, but we set the initial state here
    }

    public void placeAmbush(Game game, MoveRequest move) {
        if (game.getPhase() != GamePhase.AMBUSH)
            throw new IllegalStateException("Not in AMBUSH phase");

        String playerId = move.getPlayerId();
        if (playerId.equals("p1")) {
            if (game.getP1AmbushesPlacedThisRound() >= 2)
                throw new IllegalStateException("P1 Already placed 2 ambushes");
        } else {
            if (game.getP2AmbushesPlacedThisRound() >= 2)
                throw new IllegalStateException("P2 Already placed 2 ambushes");
        }

        Square square = game.getBoard().getSquare(move.getR(), move.getC());

        if (square.getOwnerId() != null)
            throw new IllegalStateException("Cannot place ambush on occupied square");

        if (playerId.equals("p1")) {
            if (square.isP1Ambush())
                throw new IllegalStateException("You already have an ambush here");
            square.setP1Ambush(true);
            game.setP1AmbushesPlacedThisRound(game.getP1AmbushesPlacedThisRound() + 1);
        } else {
            if (square.isP2Ambush())
                throw new IllegalStateException("You already have an ambush here");
            square.setP2Ambush(true);
            game.setP2AmbushesPlacedThisRound(game.getP2AmbushesPlacedThisRound() + 1);
        }

        if (game.getP1AmbushesPlacedThisRound() == 2 && game.getP2AmbushesPlacedThisRound() == 2) {
            transitionToPlacement(game);
        }
    }

    public void placePiece(Game game, MoveRequest move) {
        if (game.getPhase() == GamePhase.PLACEMENT) {
            handlePlacementPhase(game, move);
        } else if (game.getPhase() == GamePhase.EXTRA_ROUNDS) {
            handleExtraRoundsPhase(game, move);
        } else {
            throw new IllegalStateException("Not in a placement phase");
        }
    }

    public void handleTimeout(Game game, String timedOutPlayerId) {
        if (game.getPhase() == GamePhase.GAME_OVER)
            return;

        if (game.getPhase() == GamePhase.AMBUSH) {
            int remaining = 2 - (timedOutPlayerId.equals("p1") ? game.getP1AmbushesPlacedThisRound()
                    : game.getP2AmbushesPlacedThisRound());
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

    // --- Private Logic Methods ---

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

        // Timer management is handled by Service based on state change
    }

    private void handlePlacementPhase(Game game, MoveRequest move) {
        if (!game.getCurrentTurnPlayerId().equals(move.getPlayerId())) {
            throw new IllegalStateException("Not your turn");
        }

        Square square = game.getBoard().getSquare(move.getR(), move.getC());
        if (square.getOwnerId() != null)
            throw new IllegalStateException("Square already occupied");

        if (square.hasAmbush()) {
            // Logic: If I step on my own ambush (regardless of whether opponent has one), I
            // get the turn.
            // If I step on ONLY opponent's ambush, opponent gets the turn.

            if (move.getPlayerId().equals("p1")) {
                if (square.isP1Ambush()) {
                    game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
                } else if (square.isP2Ambush()) {
                    game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
                }
            } else { // p2 moving
                if (square.isP2Ambush()) {
                    game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
                } else if (square.isP1Ambush()) {
                    game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
                }
            }

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
            } else {
                transitionToNextRound(game);
            }
        }
    }

    private void transitionToNextRound(Game game) {
        game.setCurrentRound(game.getCurrentRound() + 1);
        if (game.getCurrentRound() > 4) {
            transitionToExtraRounds(game);
        } else {
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
    }

    private void handleExtraRoundsPhase(Game game, MoveRequest move) {
        Player mover = game.getPlayer(move.getPlayerId());
        if (!game.getCurrentTurnPlayerId().equals(move.getPlayerId()) || mover.getExtraTurns() <= 0) {
            throw new IllegalStateException("Not your turn or no extra turns left");
        }
        Square square = game.getBoard().getSquare(move.getR(), move.getC());
        if (square.getOwnerId() != null)
            throw new IllegalStateException("Square already occupied");

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
        }
    }

    public void endGame(Game game) {
        game.setPhase(GamePhase.GAME_OVER);

        int p1MaxConnection = calculateMaxConnection(game.getBoard(), "p1");
        int p2MaxConnection = calculateMaxConnection(game.getBoard(), "p2");
        int p1Pieces = countPieces(game.getBoard(), "p1");
        int p2Pieces = countPieces(game.getBoard(), "p2");

        GameResult result = new GameResult();
        result.setP1MaxConnection(p1MaxConnection);
        result.setP2MaxConnection(p2MaxConnection);
        result.setP1PieceCount(p1Pieces);
        result.setP2PieceCount(p2Pieces);

        if (p1MaxConnection > p2MaxConnection)
            result.setWinnerId("p1");
        else if (p2MaxConnection > p1MaxConnection)
            result.setWinnerId("p2");
        else {
            if (p1Pieces > p2Pieces)
                result.setWinnerId("p1");
            else if (p2Pieces > p1Pieces)
                result.setWinnerId("p2");
            else
                result.setWinnerId("DRAW");
        }

        game.setResult(result);
    }

    private void performRandomAmbush(Game game, String playerId, int count) {
        List<Point> spots = getValidAmbushSpots(game.getBoard(), playerId);
        Collections.shuffle(spots);
        int placed = 0;
        for (Point spot : spots) {
            if (placed >= count)
                break;
            Square s = game.getBoard().getSquare(spot.r(), spot.c());
            if (playerId.equals("p1"))
                s.setP1Ambush(true);
            else
                s.setP2Ambush(true);
            placed++;
        }
        if (playerId.equals("p1"))
            game.setP1AmbushesPlacedThisRound(game.getP1AmbushesPlacedThisRound() + placed);
        else
            game.setP2AmbushesPlacedThisRound(game.getP2AmbushesPlacedThisRound() + placed);
    }

    private void performRandomPlacement(Game game, String playerId) {
        List<Point> spots = getValidPlacementSpots(game.getBoard());
        if (spots.isEmpty()) {
            endGame(game);
            return;
        }

        Collections.shuffle(spots);
        Point spot = spots.get(0);

        Square square = game.getBoard().getSquare(spot.r(), spot.c());
        if (square.hasAmbush()) {
            // Logic: If I step on my own ambush (regardless of whether opponent has one), I
            // get the turn.
            // If I step on ONLY opponent's ambush, opponent gets the turn.

            if (playerId.equals("p1")) {
                if (square.isP1Ambush()) {
                    game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
                } else if (square.isP2Ambush()) {
                    game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
                }
            } else { // p2 moving
                if (square.isP2Ambush()) {
                    game.getPlayer("p2").setExtraTurns(game.getPlayer("p2").getExtraTurns() + 1);
                } else if (square.isP1Ambush()) {
                    game.getPlayer("p1").setExtraTurns(game.getPlayer("p1").getExtraTurns() + 1);
                }
            }

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
            } else {
                transitionToNextRound(game);
            }
        }
    }

    private void performRandomExtraPlacement(Game game, String playerId) {
        List<Point> spots = getValidPlacementSpots(game.getBoard());
        if (spots.isEmpty()) {
            endGame(game);
            return;
        }

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
        }
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
                if (playerId.equals("p1") && s.isP1Ambush())
                    continue;
                if (playerId.equals("p2") && s.isP2Ambush())
                    continue;

                spots.add(new Point(r, c));
            }
        }
        return spots;
    }

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
        if (r < 0 || r >= 6 || c < 0 || c >= 6 || visited[r][c]
                || !playerId.equals(board.getSquare(r, c).getOwnerId())) {
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
}
