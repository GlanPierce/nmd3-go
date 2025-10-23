package com.example.ninjaattack.model.domain;

import lombok.Data;

@Data
public class Square {
    private String ownerId; // "p1", "p2", or null (被落子占领)
    private boolean p1Ambush; // p1 是否在此有伏兵
    private boolean p2Ambush; // p2 是否在此有伏兵

    public boolean hasAmbush() {
        return p1Ambush || p2Ambush;
    }

    public void clearAmbushes() {
        this.p1Ambush = false;
        this.p2Ambush = false;
    }

    public boolean hasOpponentAmbush(String playerId) {
        return (playerId.equals("p1") && p2Ambush) || (playerId.equals("p2") && p1Ambush);
    }
}