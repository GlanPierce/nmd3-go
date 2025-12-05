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

    // --- Authentication Beans ---

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
     * Configure HTTP Security Rules (SecurityFilterChain)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                // Define which URLs require/don't require login
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // Static resources and public APIs must be allowed
                                "/",
                                "/index.html",
                                "/login.html",
                                "/login.js",
                                "/css/**",
                                "/js/**",
                                "/assets/**",
                                "/ws/**",
                                "/api/auth/**",
                                "/api/leaderboard",
                                "/*.ico",
                                "/*.js.map")
                        .permitAll()
                        // All other requests require login
                        .anyRequest().authenticated())

                // Exception handling for unauthenticated access
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                                response.sendError(401, "Unauthorized");
                            } else {
                                response.sendRedirect("/login.html");
                            }
                        }))

                // Ensure Session Context (login state) is maintained across requests
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