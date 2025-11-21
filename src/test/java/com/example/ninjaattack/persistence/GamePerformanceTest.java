package com.example.ninjaattack.persistence;

import com.example.ninjaattack.model.domain.Game;
import com.example.ninjaattack.model.domain.GamePhase;
import com.example.ninjaattack.model.domain.Player;
import com.example.ninjaattack.repository.GameRepository;
import com.example.ninjaattack.service.GameService;
import com.example.ninjaattack.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
})
public class GamePerformanceTest {

    @Autowired
    private GameService gameService;

    @MockBean
    private GameRepository gameRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private TaskScheduler taskScheduler;

    @MockBean
    private UserService userService;

    @Test
    public void testAsyncSaveGameState() {
        // Setup
        Game game = new Game("p1", "p2");
        game.setPhase(GamePhase.PLACEMENT);

        // Execute
        long startTime = System.currentTimeMillis();
        gameService.saveGameState(game);
        long endTime = System.currentTimeMillis();

        // Verify it returns immediately (should be very fast, < 10ms usually, but let's
        // say < 50ms to be safe)
        long duration = endTime - startTime;
        System.out.println("saveGameState took " + duration + "ms");
        assertTrue(duration < 100, "saveGameState should return immediately (async)");

        // Verify repository was called eventually (wait up to 1s)
        verify(gameRepository, timeout(1000).times(1)).save(any());
    }

    @Test
    public void testTimerScheduling() {
        // Setup
        Mockito.when(taskScheduler.schedule(any(Runnable.class), any(java.util.Date.class)))
                .thenReturn(Mockito.mock(ScheduledFuture.class));

        // Execute
        Game game = gameService.createGame("p1", "p2");

        // Verify taskScheduler was called to schedule match timeout
        verify(taskScheduler).schedule(any(Runnable.class), any(java.util.Date.class));
    }
}
