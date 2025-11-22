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

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GameServiceAmbushTest {

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
    @Mock
    private ScheduledFuture<?> mockFuture;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        gameService = new GameService(userService, messagingTemplate, taskScheduler, gameRepository, objectMapper);
        doNothing().when(messagingTemplate).convertAndSend(anyString(), (Object) any());
        doReturn(mockFuture).when(taskScheduler).schedule(any(Runnable.class), any(java.util.Date.class));
    }

    @Test
    void testAmbushTimerPersistsUntilBothDone() {
        // 1. Setup Game in Ambush Phase
        Game game = gameService.createGame("User1", "User2");
        String gameId = game.getGameId();
        gameService.playerReady(gameId, "p1", "User1");
        gameService.playerReady(gameId, "p2", "User2");

        assertEquals(GamePhase.AMBUSH, game.getPhase());

        // Verify timer started (called at least once)
        verify(taskScheduler, atLeast(1)).schedule(any(Runnable.class), any(java.util.Date.class));

        // Reset mock to clear any previous cancellations (e.g. match timer
        // cancellation)
        reset(mockFuture);

        // 2. P1 places 2 ambushes (Done)
        gameService.placeAmbush(gameId, createMove("p1", 0, 0), "User1");
        gameService.placeAmbush(gameId, createMove("p1", 0, 1), "User1");

        // Timer should NOT be cancelled yet because P2 is not done
        verify(mockFuture, never()).cancel(anyBoolean());

        // 3. P2 places 1 ambush (Not Done)
        gameService.placeAmbush(gameId, createMove("p2", 5, 5), "User2");
        verify(mockFuture, never()).cancel(anyBoolean());

        // 4. P2 places 2nd ambush (Done)
        gameService.placeAmbush(gameId, createMove("p2", 5, 4), "User2");

        // NOW timer should be cancelled (at least once)
        verify(mockFuture, atLeast(1)).cancel(false);

        assertEquals(GamePhase.PLACEMENT, game.getPhase());
    }

    private MoveRequest createMove(String playerId, int r, int c) {
        MoveRequest m = new MoveRequest();
        m.setPlayerId(playerId);
        m.setR(r);
        m.setC(c);
        return m;
    }
}
