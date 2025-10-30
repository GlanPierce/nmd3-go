package com.example.ninjaattack.model.domain;

import lombok.Data;
import java.util.UUID;

@Data
public class Game {
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

    // --- (新增) 计时器状态 ---
    // Long.MAX_VALUE 意味着计时器被“解除” (disarmed)
    private long p1ActionDeadline = Long.MAX_VALUE;
    private long p2ActionDeadline = Long.MAX_VALUE;
    // --- (新增) 结束 ---

    public Game(String p1Username, String p2Username) {
        this.gameId = UUID.randomUUID().toString().substring(0, 8);
        this.p1 = new Player("p1", p1Username);
        this.p2 = new Player("p2", p2Username);
        this.board = new Board();
        this.currentRound = 1;
        this.phase = GamePhase.PRE_GAME;
        this.firstMovePlayerId = Math.random() < 0.5 ? "p1" : "p2";

        // (删除) 不再在这里开始伏兵阶段
        // resetForAmbushPhase();
    }

    public void resetForAmbushPhase() {
        this.phase = GamePhase.AMBUSH;
        this.p1AmbushesPlacedThisRound = 0;
        this.p2AmbushesPlacedThisRound = 0;

        // (删除) 计时器将由 GameService 在转换阶段时启动
        // startTimer("p1", 15);
        // startTimer("p2", 15);
    }

    public String getOpponentId(String playerId) {
        return playerId.equals("p1") ? "p2" : "p1";
    }

    public Player getPlayer(String playerId) {
        return playerId.equals("p1") ? p1 : p2;
    }

    // --- (新增) 计时器辅助方法 ---
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
    // --- (新增) 结束 ---
}