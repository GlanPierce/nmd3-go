package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.dto.GameStateDTO;
import com.example.ninjaattack.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/active")
    public ResponseEntity<GameStateDTO> getActiveGame(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        GameStateDTO game = gameService.findActiveGameByUsername(principal.getName());
        if (game != null) {
            return ResponseEntity.ok(game);
        } else {
            return ResponseEntity.noContent().build();
        }
    }
}
