package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.dto.GameStateDTO;
// (已删除) import com.example.ninjaattack.model.dto.MoveRequest;
import com.example.ninjaattack.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * "开始游戏" 仍然是唯一需要的 RESTful API。
     * 客户端调用这个 API 来创建游戏，并获取初始的游戏状态和 GameId。
     */
    @PostMapping("/start")
    public ResponseEntity<?> createGame(@RequestParam String p1, @RequestParam String p2) {
        try {
            GameStateDTO gameState = gameService.createGame(p1, p2);
            return ResponseEntity.ok(gameState);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- (已删除) ---
    // 以下方法已作废，因为它们现在由 GameSocketController 和 WebSocket 处理

    // (已删除) @GetMapping("/{gameId}/state") ...

    // (已删除) @PostMapping("/{gameId}/ambush") ...

    // (已删除) @PostMapping("/{gameId}/place") ...

}