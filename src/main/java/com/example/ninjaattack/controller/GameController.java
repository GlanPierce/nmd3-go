package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.dto.GameStateDTO;
import com.example.ninjaattack.model.dto.MoveRequest;
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

    @PostMapping("/start")
    public ResponseEntity<?> createGame(@RequestParam String p1, @RequestParam String p2) {
        try {
            GameStateDTO gameState = gameService.createGame(p1, p2);
            return ResponseEntity.ok(gameState);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{gameId}/state")
    public ResponseEntity<GameStateDTO> getGameState(@PathVariable String gameId) {
        return ResponseEntity.ok(gameService.getGameState(gameId));
    }

    @PostMapping("/{gameId}/ambush")
    public ResponseEntity<?> placeAmbush(@PathVariable String gameId, @RequestBody MoveRequest move) {
        try {
            GameStateDTO gameState = gameService.placeAmbush(gameId, move);
            return ResponseEntity.ok(gameState);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{gameId}/place")
    public ResponseEntity<?> placePiece(@PathVariable String gameId, @RequestBody MoveRequest move) {
        try {
            GameStateDTO gameState = gameService.placePiece(gameId, move);
            return ResponseEntity.ok(gameState);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}