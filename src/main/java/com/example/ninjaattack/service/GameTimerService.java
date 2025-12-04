package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.Game;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;

@Service
public class GameTimerService {

    private final TaskScheduler taskScheduler;

    public GameTimerService(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public void scheduleTurnTimer(Game game, String playerId, int seconds, Runnable onTimeout) {
        // Cancel existing timer first to avoid race conditions
        cancelTurnTimer(game);

        long delayMs = seconds * 1000L;
        Date startTime = new Date(System.currentTimeMillis() + delayMs);

        // Schedule new timer
        ScheduledFuture<?> future = taskScheduler.schedule(onTimeout, startTime);
        game.setTurnTimer(future);

        // Update model for frontend display
        game.startTimer(playerId, seconds);
    }

    public void scheduleAmbushTimer(Game game, int seconds, Runnable onTimeout) {
        cancelTurnTimer(game);

        long delayMs = seconds * 1000L;
        Date startTime = new Date(System.currentTimeMillis() + delayMs);

        ScheduledFuture<?> future = taskScheduler.schedule(onTimeout, startTime);
        game.setTurnTimer(future);

        // Set deadlines for BOTH players
        game.startTimer("p1", seconds);
        game.startTimer("p2", seconds);
    }

    public void scheduleMatchTimer(Game game, int seconds, Runnable onTimeout) {
        cancelMatchTimer(game);

        Date startTime = new Date(System.currentTimeMillis() + seconds * 1000L);
        ScheduledFuture<?> future = taskScheduler.schedule(onTimeout, startTime);
        game.setMatchTimer(future);
    }

    public void cancelTurnTimer(Game game) {
        // Disarm logical timers first
        game.disarmTimer("p1");
        game.disarmTimer("p2");

        // Then cancel the actual scheduled task
        ScheduledFuture<?> timer = game.getTurnTimer();
        if (timer != null && !timer.isDone()) {
            timer.cancel(false);
        }
        game.setTurnTimer(null);
    }

    public void cancelMatchTimer(Game game) {
        ScheduledFuture<?> timer = game.getMatchTimer();
        if (timer != null && !timer.isDone()) {
            timer.cancel(false);
        }
        game.setMatchTimer(null);
    }
}
