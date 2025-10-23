package com.example.ninjaattack.model.dto;

import com.example.ninjaattack.model.domain.Board;
import com.example.ninjaattack.model.domain.GamePhase;
import com.example.ninjaattack.model.domain.GameResult;
import lombok.Data;

@Data
public class GameStateDTO {
    private String gameId;
    private String p1Username;
    private String p2Username;
    private int p1ExtraTurns;
    private int p2ExtraTurns;
    private Board board;
    private GamePhase phase;
    private int currentRound;
    private String currentTurnPlayerId;
    private String statusMessage;
    private GameResult result;

    private int p1AmbushesPlaced;
    private int p2AmbushesPlaced;

    // --- (新增) 剩余时间 (毫秒) ---
    // -1 意味着计时器未激活
    private long p1TimeLeft = -1;
    private long p2TimeLeft = -1;
    // --- (新增) 结束 ---
}