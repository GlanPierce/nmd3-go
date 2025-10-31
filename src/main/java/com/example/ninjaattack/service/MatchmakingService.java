package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.Game;
import com.example.ninjaattack.model.dto.MatchResult;
import lombok.Data;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class MatchmakingService {

    @Data
    private static class WaitingPlayer {
        private final Long userId; // 玩家的 UID (来自 User.id)
        private final String username; // 玩家的昵称 (用于消息发送和显示)
    }

    private final Queue<WaitingPlayer> waitingQueue = new ConcurrentLinkedQueue<>();
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public MatchmakingService(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * (修改) 寻找或开始一个新对战。
     * @param userId 玩家的 UID (Long id)
     * @param username 玩家的昵称 (String username)
     */
    public synchronized void findAndStartMatch(Long userId, String username) {

        if (waitingQueue.stream().anyMatch(p -> p.getUserId().equals(userId))) {
            System.out.println("玩家 " + username + " (ID: " + userId + ") 已在队列中。");
            return;
        }

        WaitingPlayer opponent = waitingQueue.poll();

        if (opponent == null) {
            System.out.println("玩家 " + username + " (ID: " + userId + ") 加入等待队列。");
            waitingQueue.add(new WaitingPlayer(userId, username));
        } else {
            // 匹配成功！
            System.out.println("匹配成功: " + username + " vs " + opponent.getUsername());

            Game game = gameService.createGame(username, opponent.getUsername());

            String gameId = game.getGameId();
            String p1Name = game.getP1().getUsername();
            String p2Name = game.getP2().getUsername();

            MatchResult resultForUser = new MatchResult(
                    gameId,
                    username.equals(p1Name) ? "p1" : "p2",
                    p1Name,
                    p2Name
            );

            MatchResult resultForOpponent = new MatchResult(
                    gameId,
                    opponent.getUsername().equals(p1Name) ? "p1" : "p2",
                    p1Name,
                    p2Name
            );

            // (重大修改)
            // 我们不再使用 convertAndSendToUser (它依赖 /user 前缀)
            // 我们直接发送到我们为每个玩家定义的、基于 UID 的唯一队列
            String userDestination = "/queue/matchmaking-" + userId;
            String opponentDestination = "/queue/matchmaking-" + opponent.getUserId();

            messagingTemplate.convertAndSend(userDestination, resultForUser);
            messagingTemplate.convertAndSend(opponentDestination, resultForOpponent);

            System.out.println("已发送匹配结果到 " + userDestination + " 和 " + opponentDestination);
        }
    }
}