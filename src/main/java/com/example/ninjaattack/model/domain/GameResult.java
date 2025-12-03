package com.example.ninjaattack.model.domain;

import lombok.Data;

@Data
public class GameResult {
    private String winnerId; // "p1", "p2", or "DRAW"
    private int p1MaxConnection;
    private int p2MaxConnection;
    private int p1PieceCount;
    private int p2PieceCount;
    private int p1RatingChange;
    private int p2RatingChange;
}