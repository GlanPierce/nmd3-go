package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.dto.MoveRequest;
import com.example.ninjaattack.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GameSocketController {

    private final GameService gameService;

    public GameSocketController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * 处理伏兵请求
     * 客户端发送到: /app/game/{gameId}/ambush
     */
    @MessageMapping("/game/{gameId}/ambush")
    public void handleAmbush(@DestinationVariable String gameId, MoveRequest move) {
        try {
            gameService.placeAmbush(gameId, move);
        } catch (Exception e) {
            // (可选) 向该玩家发送一个错误消息
            System.err.println("Ambush error: " + e.getMessage());
            // 实际项目中，这里应该用 simpMessagingTemplate 向 "user" 发送一个错误 DTO
        }
    }

    /**
     * 处理落子请求
     * 客户端发送到: /app/game/{gameId}/place
     */
    @MessageMapping("/game/{gameId}/place")
    public void handlePlace(@DestinationVariable String gameId, MoveRequest move) {
        try {
            gameService.placePiece(gameId, move);
        } catch (Exception e) {
            System.err.println("Place error: " + e.getMessage());
        }
    }
}