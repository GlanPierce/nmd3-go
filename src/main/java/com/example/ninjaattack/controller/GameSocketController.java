package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.dto.MoveRequest;
import com.example.ninjaattack.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal; // (新增)

@Controller
public class GameSocketController {

    private final GameService gameService;

    public GameSocketController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * (新增) 玩家在连接到游戏主题后，发送此消息以宣告 "准备就绪"
     * 客户端发送到: /app/game/{gameId}/ready
     * * @param gameId 游戏ID
     * @param request 我们复用 MoveRequest DTO 来传递 { "playerId": "p1" } 或 { "playerId": "p2" }
     * @param principal 自动注入的已登录用户 (用于验证)
     */
    @MessageMapping("/game/{gameId}/ready")
    public void playerReady(@DestinationVariable String gameId, MoveRequest request, Principal principal) {
        if (principal == null) {
            return; // 未登录
        }

        // (可选的安全检查: 确保发送 "p1 ready" 的人真的是 P1)
        // ... 此处省略 ...

        // 调用我们第 2 步在 GameService 中添加的方法
        gameService.playerReady(gameId, request.getPlayerId());
    }

    /**
     * (不变) 处理伏兵请求
     * 客户端发送到: /app/game/{gameId}/ambush
     */
    @MessageMapping("/game/{gameId}/ambush")
    public void handleAmbush(@DestinationVariable String gameId, MoveRequest move, Principal principal) {
        if (principal == null) return; // (新增) 安全检查

        try {
            gameService.placeAmbush(gameId, move);
        } catch (Exception e) {
            System.err.println("Ambush error: " + e.getMessage());
            // (TODO) 在未来，我们应该将这个错误私信发回给 `principal.getName()`
        }
    }

    /**
     * (不变) 处理落子请求
     * 客户端发送到: /app/game/{gameId}/place
     */
    @MessageMapping("/game/{gameId}/place")
    public void handlePlace(@DestinationVariable String gameId, MoveRequest move, Principal principal) {
        if (principal == null) return; // (新增) 安全检查

        try {
            gameService.placePiece(gameId, move);
        } catch (Exception e) {
            System.err.println("Place error: " + e.getMessage());
            // (TODO) 在未来，我们应该将这个错误私信发回给 `principal.getName()`
        }
    }
}