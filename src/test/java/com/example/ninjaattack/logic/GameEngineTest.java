package com.example.ninjaattack.logic;

import com.example.ninjaattack.model.domain.Game;
import com.example.ninjaattack.model.domain.GamePhase;
import com.example.ninjaattack.model.domain.GameResult;
import com.example.ninjaattack.model.dto.MoveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {

    private GameEngine gameEngine;
    private Game game;

    @BeforeEach
    void setUp() {
        gameEngine = new GameEngine();
        game = new Game("Player1", "Player2");
        // Manually advance to AMBUSH phase for testing, skipping PRE_GAME
        game.setPhase(GamePhase.AMBUSH);
    }

    @Test
    void testAmbushPlacement() {
        // P1 places ambush
        MoveRequest move1 = new MoveRequest();
        move1.setPlayerId("p1");
        move1.setR(0);
        move1.setC(0);
        gameEngine.placeAmbush(game, move1);

        assertTrue(game.getBoard().getSquare(0, 0).isP1Ambush());
        assertEquals(1, game.getP1AmbushesPlacedThisRound());

        // P1 cannot place on same spot (though logic checks "occupied", ambushes don't
        // occupy ownerId,
        // but usually UI prevents clicking. Let's check if we can place another.)
        // Actually GameEngine checks: if (square.getOwnerId() != null). Ambushes don't
        // set ownerId.
        // But we should probably prevent placing ambush on top of another ambush?
        // The current logic doesn't explicitly forbid ambush on ambush, but let's
        // assume different spots.

        MoveRequest move2 = new MoveRequest();
        move2.setPlayerId("p1");
        move2.setR(0);
        move2.setC(1);
        gameEngine.placeAmbush(game, move2);

        assertEquals(2, game.getP1AmbushesPlacedThisRound());

        // P1 cannot place 3rd ambush
        MoveRequest move3 = new MoveRequest();
        move3.setPlayerId("p1");
        move3.setR(0);
        move3.setC(2);

        assertThrows(IllegalStateException.class, () -> gameEngine.placeAmbush(game, move3));
    }

    @Test
    void testPhaseTransitionToPlacement() {
        // Place 2 ambushes for P1
        gameEngine.placeAmbush(game, createMove("p1", 0, 0));
        gameEngine.placeAmbush(game, createMove("p1", 0, 1));

        // Place 2 ambushes for P2
        gameEngine.placeAmbush(game, createMove("p2", 5, 5));
        gameEngine.placeAmbush(game, createMove("p2", 5, 4));

        assertEquals(GamePhase.PLACEMENT, game.getPhase());
        assertNotNull(game.getCurrentTurnPlayerId());
    }

    @Test
    void testAmbushTrigger() {
        // Setup: P1 places ambush at (0,0), P2 places at (5,5)
        gameEngine.placeAmbush(game, createMove("p1", 0, 0));
        gameEngine.placeAmbush(game, createMove("p1", 0, 1));
        gameEngine.placeAmbush(game, createMove("p2", 5, 5));
        gameEngine.placeAmbush(game, createMove("p2", 5, 4));

        // Now in PLACEMENT phase
        // Assume P1 goes first (if not, force it)
        game.setCurrentTurnPlayerId("p1");

        // P1 places on (5,5) which is P2's ambush
        MoveRequest move = createMove("p1", 5, 5);
        gameEngine.placePiece(game, move);

        // Expect: Ambush cleared, no owner set, P2 gets extra turn
        assertNull(game.getBoard().getSquare(5, 5).getOwnerId());
        assertFalse(game.getBoard().getSquare(5, 5).hasAmbush());
        assertEquals(1, game.getPlayer("p2").getExtraTurns());
    }

    @Test
    void testWinCondition() {
        // Force game over state
        game.setPhase(GamePhase.GAME_OVER);

        // Setup board for P1 win (3 connected)
        game.getBoard().getSquare(0, 0).setOwnerId("p1");
        game.getBoard().getSquare(0, 1).setOwnerId("p1");
        game.getBoard().getSquare(0, 2).setOwnerId("p1");

        // Setup board for P2 (2 connected)
        game.getBoard().getSquare(1, 0).setOwnerId("p2");
        game.getBoard().getSquare(1, 1).setOwnerId("p2");

        gameEngine.endGame(game);

        GameResult result = game.getResult();
        assertEquals("p1", result.getWinnerId());
        assertEquals(3, result.getP1MaxConnection());
        assertEquals(2, result.getP2MaxConnection());
    }

    private MoveRequest createMove(String playerId, int r, int c) {
        MoveRequest m = new MoveRequest();
        m.setPlayerId(playerId);
        m.setR(r);
        m.setC(c);
        return m;
    }
}
