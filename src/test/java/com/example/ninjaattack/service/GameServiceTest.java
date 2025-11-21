package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.Game;
import com.example.ninjaattack.model.domain.GamePhase;
import com.example.ninjaattack.model.dto.MoveRequest;
import com.example.ninjaattack.repository.GameRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

class GameServiceTest {

    private GameService gameService;

    @Mock
    private UserService userService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private GameRepository gameRepository;
    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        gameService = new GameService(userService, messagingTemplate, taskScheduler, gameRepository, objectMapper);

        // Mock messaging to do nothing
        doNothing().when(messagingTemplate).convertAndSend(anyString(), (Object) any());
    }

    @Test
    void testFullGameFlow() {
        // 1. Create Game
        Game game = gameService.createGame("User1", "User2");
        assertNotNull(game);
        assertEquals(GamePhase.PRE_GAME, game.getPhase());
        String gameId = game.getGameId();

        // 2. Players Ready
        gameService.playerReady(gameId, "p1", "User1");
        gameService.playerReady(gameId, "p2", "User2");

        // Should transition to AMBUSH
        assertEquals(GamePhase.AMBUSH, game.getPhase());

        // 3. Ambush Phase
        gameService.placeAmbush(gameId, createMove("p1", 0, 0), "User1");
        gameService.placeAmbush(gameId, createMove("p1", 0, 1), "User1");
        gameService.placeAmbush(gameId, createMove("p2", 5, 5), "User2");
        gameService.placeAmbush(gameId, createMove("p2", 5, 4), "User2");

        // Should transition to PLACEMENT
        assertEquals(GamePhase.PLACEMENT, game.getPhase());

        // 4. Placement Phase (Simulate a few moves)
        String currentTurn = game.getCurrentTurnPlayerId();
        assertNotNull(currentTurn);

        // Find a valid empty spot (not an ambush spot to keep it simple)
        int r = 2, c = 2;
        String username = "p1".equals(currentTurn) ? "User1" : "User2";
        gameService.placePiece(gameId, createMove(currentTurn, r, c), username);

        assertEquals(currentTurn, game.getBoard().getSquare(r, c).getOwnerId());
        assertEquals(1, game.getPlacementsMadeThisTurn());
    }

    private MoveRequest createMove(String playerId, int r, int c) {
        MoveRequest m = new MoveRequest();
        m.setPlayerId(playerId);
        m.setR(r);
        m.setC(c);
        return m;
    }
}
