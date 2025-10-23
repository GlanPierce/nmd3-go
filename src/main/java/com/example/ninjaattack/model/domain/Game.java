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

    // (已修正) 使用计数器替代 pendingAmbushes
    private int p1AmbushesPlacedThisRound = 0;
    private int p2AmbushesPlacedThisRound = 0;

    // 用于常规轮次落子
    private String placementRoundStarter; // 本轮落子的先手
    private int placementsMadeThisTurn; // 0-3

    public Game(String p1Username, String p2Username) {
        this.gameId = UUID.randomUUID().toString().substring(0, 8);
        this.p1 = new Player("p1", p1Username);
        this.p2 = new Player("p2", p2Username);
        this.board = new Board();
        this.currentRound = 1;
        this.phase = GamePhase.AMBUSH;

        // 随机先手
        this.firstMovePlayerId = Math.random() < 0.5 ? "p1" : "p2";

        resetForAmbushPhase();
    }

    public void resetForAmbushPhase() {
        this.phase = GamePhase.AMBUSH;
        // (已修正) 重置计数器
        this.p1AmbushesPlacedThisRound = 0;
        this.p2AmbushesPlacedThisRound = 0;
    }

    public String getOpponentId(String playerId) {
        return playerId.equals("p1") ? "p2" : "p1";
    }

    public Player getPlayer(String playerId) {
        return playerId.equals("p1") ? p1 : p2;
    }
}