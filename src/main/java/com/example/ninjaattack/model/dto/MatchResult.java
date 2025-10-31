package com.example.ninjaattack.model.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {
    private String gameId;
    private String assignedPlayerId; // "p1" or "p2"
    private String p1Username;
    private String p2Username;
}