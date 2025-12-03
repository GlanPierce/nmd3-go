package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.domain.User;
import com.example.ninjaattack.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<User>> getLeaderboard() {
        return ResponseEntity.ok(userService.getLeaderboard());
    }

    @PutMapping("/avatar")
    public ResponseEntity<?> updateAvatar(@AuthenticationPrincipal User user,
            @RequestBody Map<String, String> payload) {
        String avatar = payload.get("avatar");
        if (avatar == null) {
            return ResponseEntity.badRequest().body("Avatar is required");
        }
        try {
            userService.updateAvatar(user.getUsername(), avatar);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<com.example.ninjaattack.model.dto.GameHistoryDTO>> getMatchHistory(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getMatchHistory(user.getUsername()));
    }
}