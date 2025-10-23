package com.example.ninjaattack.model.dto;

import lombok.Data;

@Data
public class MoveRequest {
    private String playerId;
    private int r; // row
    private int c; // col
}