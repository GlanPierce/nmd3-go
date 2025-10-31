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
@EnableWebSecurity // 启用 Spring Web Security
public class SecurityConfig {

    /**
     * 1. 定义密码加密器 (PasswordEncoder)
     * 我们使用 BCrypt，这是当前的行业标准。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 2. 定义 UserDetailsService
     * 告诉 Spring Security 如何加载用户数据。
     * 它将调用我们稍后在 UserService 中实现的 loadUserByUsername 方法。
     */
    @Bean
    public UserDetailsService userDetailsService(UserService userService) {
        return userService;
    }

    /**
     * 3. 定义 AuthenticationProvider (认证提供者)
     * 将 UserDetailsService (加载用户) 和 PasswordEncoder (加密器) 绑定在一起。
     */
    @Bean
    public AuthenticationProvider authenticationProvider(UserService userService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService(userService));
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * 4. 定义 AuthenticationManager (认证管理器)
     * 这是处理“登录”请求的核心组件，我们将"登录"API中注入它。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 5. (最重要) 配置 HTTP 安全规则 (SecurityFilterChain)
     * 定义哪些 URL 需要登录，哪些可以公开访问。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF 保护 (简单起见，方便前端)
                .csrf(AbstractHttpConfigurer::disable)

                // 定义哪些 URL 需要/不需要登录
                .authorizeHttpRequests(auth -> auth
                        // 允许所有人(permitAll)访问：
                        .requestMatchers(
                                "/", "/index.html", "/*.css", "/*.js", // 静态主页和资源
                                "/*.js.map", "/*.ico", // 其他静态资源
                                "/ws/**", // WebSocket 连接点
                                "/api/auth/register", // 我们的注册 API
                                "/api/auth/login",  // 我们的登录 API
                                "/api/leaderboard"  // 排行榜
                        ).permitAll()
                        // 其他所有请求(anyRequest)都需要身份认证(authenticated)
                        .anyRequest().authenticated()
                )

                // (新增) 确保 SecurityContext 在请求之间被保存
                // 这对于让 WebSocket 继承 HTTP 登录状态至关重要
                .securityContext(context -> context
                        .securityContextRepository(new HttpSessionSecurityContextRepository())
                )

                // 登出配置
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout") // 登出 API
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(200)) // 登出成功返回 200 OK
                );

        return http.build();
    }
}