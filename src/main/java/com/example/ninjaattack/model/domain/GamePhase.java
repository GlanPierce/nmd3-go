package com.example.ninjaattack.model.domain;

public enum GamePhase {
    PRE_GAME,        // 匹配成功，等待双方 "Ready"
    AMBUSH,          // 伏兵阶段
    PLACEMENT,       // 落子阶段
    EXTRA_ROUNDS,    // 额外轮次
    GAME_OVER,       // 游戏结束
    MATCH_CANCELLED  // (新增) 玩家未在30秒内确认
}