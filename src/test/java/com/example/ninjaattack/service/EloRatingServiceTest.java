package com.example.ninjaattack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class EloRatingServiceTest {

    private EloRatingService eloRatingService;

    @BeforeEach
    public void setUp() {
        eloRatingService = new EloRatingService();
        // Manually inject values using ReflectionTestUtils
        ReflectionTestUtils.setField(eloRatingService, "fmaBonus", 30);
        ReflectionTestUtils.setField(eloRatingService, "kFactorProvisional", 40);
        ReflectionTestUtils.setField(eloRatingService, "kFactorDefault", 32);
        ReflectionTestUtils.setField(eloRatingService, "provisionalThreshold", 30);
    }

    @Test
    public void testCalculateNewRating_SameRating_Player1Wins_FirstMover() {
        int p1Rating = 1200;
        int p2Rating = 1200;

        // P1 is First Mover (+30 bonus).
        // Effective P1: 1230, Effective P2: 1200.
        // Expected Score P1: 1 / (1 + 10^((1200-1230)/400)) = 1 / (1 + 10^(-0.075)) = 1
        // / (1 + 0.841) = 0.543
        // Actual Score: 1.0
        // K = 40 (Provisional)
        // Change = 40 * (1.0 - 0.543) = 40 * 0.457 = 18.28 -> 18
        // New Rating = 1200 + 18 = 1218

        int newRating = eloRatingService.calculateNewRating(p1Rating, p2Rating, 1.0, true, 0);
        assertEquals(1218, newRating);
    }

    @Test
    public void testCalculateNewRating_SameRating_Player1Loses_FirstMover() {
        int p1Rating = 1200;
        int p2Rating = 1200;

        // P1 is First Mover (+30 bonus). Expected: 0.543
        // Actual Score: 0.0
        // K = 40
        // Change = 40 * (0.0 - 0.543) = -21.72 -> -22
        // New Rating = 1200 - 22 = 1178

        int newRating = eloRatingService.calculateNewRating(p1Rating, p2Rating, 0.0, true, 0);
        assertEquals(1178, newRating);
    }

    @Test
    public void testCalculateNewRating_EstablishedPlayer() {
        int p1Rating = 1200;
        int p2Rating = 1200;

        // P1 is First Mover (+30). Expected: 0.543
        // Actual Score: 1.0
        // K = 32 (Established, gamesPlayed > 30)
        // Change = 32 * (1.0 - 0.543) = 32 * 0.457 = 14.624 -> 15
        // New Rating = 1200 + 15 = 1215

        int newRating = eloRatingService.calculateNewRating(p1Rating, p2Rating, 1.0, true, 31);
        assertEquals(1215, newRating);
    }
}
