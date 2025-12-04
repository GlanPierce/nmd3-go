package com.example.ninjaattack.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveRecord {
    private String playerId;
    private String type; // "PIECE" or "AMBUSH"
    private int r;
    private int c;
    private long timestamp;
}
