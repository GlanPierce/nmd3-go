package com.example.ninjaattack.config;

import com.example.ninjaattack.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // --- 认证相关的 Bean (不变) ---

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserService userService) {
        return userService;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserService userService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService(userService));
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 配置 HTTP 安全规则 (SecurityFilterChain)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                // 定义哪些 URL 需要/不需要登录
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // 静态资源和公共 API 必须放行
                                "/",
                                "/index.html",
                                "/style.css",
                                "/main.js",
                                "/api.js",
                                "/ui.js",
                                "/lobby.js",
                                "/game.js",
                                "/*.js.map",
                                "/*.ico",
                                "/ws/**", // WebSocket 连接握手
                                "/api/auth/**", // 认证 API
                                "/api/leaderboard" // 排行榜
                        ).permitAll()
                        // 其他所有请求都需要登录
                        .anyRequest().authenticated())

                // 确保 Session Context (登录状态) 在请求间保持
                .securityContext(context -> context
                        .securityContextRepository(new HttpSessionSecurityContextRepository()))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(200)))
                .rememberMe(remember -> remember
                        .userDetailsService(userService)
                        .key("uniqueAndSecretKey")
                        .tokenValiditySeconds(15552000) // 180 days
                );

        return http.build();
    }
}