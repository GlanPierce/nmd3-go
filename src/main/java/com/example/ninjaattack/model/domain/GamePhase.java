package com.example.ninjaattack.model.domain;

public enum GamePhase {
    PRE_GAME,        // (新增) 游戏开始前的准备阶段
    AMBUSH,          // 伏兵阶段
    PLACEMENT,       // 落子阶段
    EXTRA_ROUNDS,    // 额外轮次
    GAME_OVER        // 游戏结束
}