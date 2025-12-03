package com.example.ninjaattack.model.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Player {
    private String id; // "p1" or "p2"
    private String username;
    private int extraTurns = 0; // 额外落子次数

    public Player(String id, String username) {
        this.id = id;
        this.username = username;
    }
}