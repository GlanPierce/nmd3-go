package com.example.ninjaattack.controller;

import com.example.ninjaattack.model.domain.User;
import com.example.ninjaattack.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid; // (新增) 用于验证
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth") // 所有认证相关的 API 都在 /api/auth 路径下
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager; // (新增) 注入认证管理器

    public AuthController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    // --- 注册 ---

    // DTO (数据传输对象) 用于接收注册请求
    @Data
    static class RegisterRequest {
        @Valid // (可选) 确保不为空
        private String username;
        @Valid
        private String password;
    }

    /**
     * 处理 POST /api/auth/register 请求
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        try {
            userService.register(request.getUsername(), request.getPassword());
            return ResponseEntity.ok("注册成功");
        } catch (IllegalStateException e) {
            // "用户名已存在" 是一种 "Conflict" (冲突)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // --- 登录 ---

    // DTO 用于接收登录请求
    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }

    /**
     * 处理 POST /api/auth/login 请求
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            // 1. (关键) 使用 AuthenticationManager 验证用户名和密码
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );

            // 2. 将认证信息放入 SecurityContext (安全上下文)
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            // 3. (关键) 创建并保存 HTTP Session
            // 这就是 WebSocket 稍后用来“记住”你的方式
            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", context);

            // 4. 返回成功信息
            User user = (User) authentication.getPrincipal();
            // 返回 User 对象 (密码字段已在 User.java 中被 @JsonIgnore 隐藏)
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            // 如果认证失败 (密码错误等)，会抛出异常
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("登录失败: 用户名或密码错误");
        }
    }

    // --- 检查状态 ---

    /**
     * (新增) 处理 GET /api/auth/me 请求
     * 这是一个非常有用的 API，前端页面加载时会调用它，
     * 用来检查用户是否已经登录 (是否已有 Session)。
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        // 从安全上下文中获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 检查是否是匿名用户或未认证
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录");
        }

        // 如果已登录，返回用户信息
        return ResponseEntity.ok(authentication.getPrincipal());
    }
}