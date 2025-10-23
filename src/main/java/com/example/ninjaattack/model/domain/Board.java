package com.example.ninjaattack.model.domain;

import lombok.Data;

@Data
public class Board {
    private Square[][] grid;
    private static final int SIZE = 6;

    public Board() {
        this.grid = new Square[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                grid[i][j] = new Square();
            }
        }
    }

    public Square getSquare(int r, int c) {
        if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) {
            return null;
        }
        return grid[r][c];
    }
}