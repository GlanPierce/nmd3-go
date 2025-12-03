package com.example.ninjaattack.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameHistoryDTO {
    private String gameId;
    private String opponentName;
    private String result; // "WIN", "LOSS", "DRAW"
    private int scoreChange; // Elo change
    private LocalDateTime timestamp;
    private String replayId; // For future use
}
