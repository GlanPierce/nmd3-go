package com.example.ninjaattack.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Game {

    // [NEW] Schema version for future migrations
    private int schemaVersion = 1;

    private String gameId;
    private Player p1;
    private Player p2;
    private Board board;
    private GamePhase phase;
    private int currentRound; // 1-4
    private String firstMovePlayerId; // 随机决定的先手 (R1, R4)
    private String currentTurnPlayerId; // 当前轮到谁
    private GameResult result;

    private int p1AmbushesPlacedThisRound = 0;
    private int p2AmbushesPlacedThisRound = 0;

    private String placementRoundStarter;
    private int placementsMadeThisTurn;

    // [NEW] Move History
    private java.util.List<MoveRecord> history = new java.util.ArrayList<>();

    // Timers (Not serializable)
    @JsonIgnore
    private transient ScheduledFuture<?> turnTimer;
    @JsonIgnore
    private transient ScheduledFuture<?> matchTimer;

    // Deadlines (Serializable, used for state recovery)
    private long p1ActionDeadline = Long.MAX_VALUE;
    private long p2ActionDeadline = Long.MAX_VALUE;
    private long confirmationDeadline = Long.MAX_VALUE;

    public Game(String p1Username, String p2Username) {
        this.gameId = UUID.randomUUID().toString();
        this.p1 = new Player("p1", p1Username);
        this.p2 = new Player("p2", p2Username);
        this.board = new Board();
        this.phase = GamePhase.PRE_GAME;
        this.currentRound = 1;
        // Randomize first mover for the game (usually for R1, but logic might vary)
        this.firstMovePlayerId = Math.random() < 0.5 ? "p1" : "p2";
    }

    public void resetForAmbushPhase() {
        this.phase = GamePhase.AMBUSH;
        this.p1AmbushesPlacedThisRound = 0;
        this.p2AmbushesPlacedThisRound = 0;
        // (注意: 计时器在 GameService.startGame 中启动)
    }

    public String getOpponentId(String playerId) {
        return playerId.equals("p1") ? "p2" : "p1";
    }

    public Player getPlayer(String playerId) {
        return playerId.equals("p1") ? p1 : p2;
    }

    // --- 计时器辅助方法 ---
    public void startTimer(String playerId, int seconds) {
        long deadline = System.currentTimeMillis() + (seconds * 1000L);
        if (playerId.equals("p1")) {
            this.p1ActionDeadline = deadline;
        } else {
            this.p2ActionDeadline = deadline;
        }
    }

    public void disarmTimer(String playerId) {
        if (playerId.equals("p1")) {
            this.p1ActionDeadline = Long.MAX_VALUE;
        } else {
            this.p2ActionDeadline = Long.MAX_VALUE;
        }
    }
}