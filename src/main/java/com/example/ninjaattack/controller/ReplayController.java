package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.domain.Game;
import com.example.ninjaattack.model.entity.ReplayCollection;
import com.example.ninjaattack.repository.ReplayCollectionRepository;
import com.example.ninjaattack.service.GamePersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/replays")
public class ReplayController {

    private final GamePersistenceService gamePersistenceService;
    private final ReplayCollectionRepository replayCollectionRepository;

    public ReplayController(GamePersistenceService gamePersistenceService,
            ReplayCollectionRepository replayCollectionRepository) {
        this.gamePersistenceService = gamePersistenceService;
        this.replayCollectionRepository = replayCollectionRepository;
    }

    // Get list of finished games (Simple implementation: load all and filter)
    // In a real app, you'd want a specific repository method for this.
    @GetMapping
    public List<Game> getReplays() {
        // TODO: Optimize this by adding a method to GamePersistenceService/Repository
        // to fetch only FINISHED games
        // For now, we reuse loadActiveGames but we need a method for finished ones.
        // Since we don't have a direct "loadFinishedGames" yet, let's assume we can add
        // one or just use what we have.
        // Actually, let's stick to the plan: "Endpoint to get replay list".
        // We will need to add `loadFinishedGames` to GamePersistenceService.
        return gamePersistenceService.loadFinishedGames();
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<Game> getReplay(@PathVariable String gameId) {
        Game game = gamePersistenceService.loadGame(gameId);
        if (game != null) {
            return ResponseEntity.ok(game);
        }
        return ResponseEntity.notFound().build();
    }

    // --- Replay Collection ---

    @GetMapping("/collection")
    public List<ReplayCollection> getMyCollection(@RequestParam String username) {
        return replayCollectionRepository.findByUsername(username);
    }

    @PostMapping("/collection")
    public ResponseEntity<?> addToCollection(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String gameId = payload.get("gameId");
        String note = payload.get("note");

        if (replayCollectionRepository.existsByUsernameAndGameId(username, gameId)) {
            return ResponseEntity.badRequest().body("Already in collection");
        }

        ReplayCollection collection = new ReplayCollection();
        collection.setUsername(username);
        collection.setGameId(gameId);
        collection.setNote(note);
        replayCollectionRepository.save(collection);

        return ResponseEntity.ok("Added to collection");
    }
}
