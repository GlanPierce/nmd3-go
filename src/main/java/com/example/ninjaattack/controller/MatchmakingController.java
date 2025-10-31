package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.domain.User;
import com.example.ninjaattack.service.MatchmakingService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication; // (新增)
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    public MatchmakingController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    /**
     * (新增) 处理来自 "已登录" 客户端的 "寻找对战" 请求
     * 客户端将发送消息到: /app/matchmaking/find
     * * @param principal Spring Security 自动注入的已登录用户身份
     */
    // 在 MatchmakingController.java 中
    @MessageMapping("/matchmaking/find")
    public void findMatch(Principal principal) {
        if (principal == null) return;

        Authentication auth = (Authentication) principal;
        User user = (User) auth.getPrincipal();
        Long userId = user.getId();
        String username = user.getUsername();

        // ⬇️ ⬇️ ⬇️ 添加这行日志 ⬇️ ⬇️ ⬇️
        System.out.println("[MATCHMAKING] 收到 " + username + " (ID: " + userId + ") 的匹配请求。");

        matchmakingService.findAndStartMatch(userId, username);
    }
}