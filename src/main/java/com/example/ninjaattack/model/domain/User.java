package com.example.ninjaattack.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@NoArgsConstructor
@Entity // 告诉 JPA 这是一个数据库实体
@Table(name = "users") // 数据库中的表名
public class User implements UserDetails { // 实现 UserDetails 接口

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 这就是您的 "uid"

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    @JsonIgnore // 永远不要在 API 响应中返回密码
    private String password; // 存储加密后的密码

    @Column(nullable = false)
    private int score = 0; // 默认为 0

    // 构造函数
    public User(String username, String password, int score) {
        this.username = username;
        this.password = password;
        this.score = score;
    }

    // --- Spring Security (UserDetails) 实现 ---

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 简单起见，我们给所有用户一个 "USER" 角色
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // getPassword() 和 getUsername() 由 @Data (Lombok) 自动提供

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true; // 账户永不过期
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true; // 账户永不锁定
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true; // 凭证永不过期
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true; // 账户启用
    }
}