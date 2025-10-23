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
    private Board board; // 前端需要知道所有格子状态
    private GamePhase phase;
    private int currentRound;
    private String currentTurnPlayerId;
    private String statusMessage; // 给用户的提示信息
    private GameResult result; // 游戏结束时填充

    // 伏兵阶段专用
    private int p1AmbushesPlaced;
    private int p2AmbushesPlaced;
}