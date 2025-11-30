package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.GameResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EloRatingService {

    @Value("${elo.fma-bonus:30}")
    private int fmaBonus;

    @Value("${elo.k-factor.provisional:40}")
    private int kFactorProvisional;

    @Value("${elo.k-factor.default:32}")
    private int kFactorDefault;

    @Value("${elo.provisional-threshold:30}")
    private int provisionalThreshold;

    /**
     * Calculate new rating for a player.
     *
     * @param currentRating  Player's current rating
     * @param opponentRating Opponent's rating
     * @param actualScore    1.0 for win, 0.5 for draw, 0.0 for loss
     * @param isFirstMover   Whether the player was the first mover
     * @param gamesPlayed    Number of games the player has played
     * @return New rating
     */
    public int calculateNewRating(int currentRating, int opponentRating, double actualScore, boolean isFirstMover,
            int gamesPlayed) {
        // 1. Calculate Expected Score with First Mover Advantage (FMA)
        double effectivePlayerRating = currentRating + (isFirstMover ? fmaBonus : 0);
        double effectiveOpponentRating = opponentRating + (isFirstMover ? 0 : fmaBonus);

        double expectedScore = 1.0 / (1.0 + Math.pow(10.0, (effectiveOpponentRating - effectivePlayerRating) / 400.0));

        // 2. Determine K-Factor based on experience
        int k = (gamesPlayed < provisionalThreshold) ? kFactorProvisional : kFactorDefault;

        // 3. Calculate New Rating
        int change = (int) Math.round(k * (actualScore - expectedScore));
        return currentRating + change;
    }

    // Legacy method for backward compatibility or simple tests (assumes no FMA,
    // established player)
    public int calculateNewRating(int currentRating, int opponentRating, double actualScore) {
        return calculateNewRating(currentRating, opponentRating, actualScore, false, provisionalThreshold + 1);
    }

    /**
     * Helper to determine the actual score from the GameResult for a specific
     * player.
     */
    public double getActualScore(String playerId, GameResult result) {
        if ("DRAW".equals(result.getWinnerId())) {
            return 0.5;
        }
        return playerId.equals(result.getWinnerId()) ? 1.0 : 0.0;
    }
}
