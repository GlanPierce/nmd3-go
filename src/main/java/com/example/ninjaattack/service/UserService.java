package com.example.ninjaattack.service;

import com.example.ninjaattack.model.domain.User;
import com.example.ninjaattack.repository.UserRepository;
import org.springframework.context.annotation.Lazy; // (新增) 解决循环依赖
import org.springframework.security.core.userdetails.UserDetails; // (新增)
import org.springframework.security.core.userdetails.UserDetailsService; // (新增)
import org.springframework.security.core.userdetails.UsernameNotFoundException; // (新增)
import org.springframework.security.crypto.password.PasswordEncoder; // (新增)
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // (新增)

import java.util.List;

@Service
@Transactional // (新增) 确保 Service 方法在数据库事务中运行
// (修改) 实现 UserDetailsService 接口
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // (新增)

    /**
     * (修改) 构造函数注入 PasswordEncoder
     * (新增) @Lazy 解决 SecurityConfig 和 UserService 之间的循环依赖问题
     */
    public UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * (新增) 这是 UserDetailsService 接口要求必须实现的方法。
     * Spring Security 会在登录时自动调用此方法。
     */
    @Override
    @Transactional(readOnly = true) // 这是一个只读操作
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // (修改) 从新的 JPA 仓库中查找用户
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("未找到用户: " + username));
    }

    /**
     * (新增) 注册新用户的方法
     * (我们将在下一步的 AuthController 中调用它)
     */
    public User register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("用户名已存在");
        }
        // (新增) 必须加密密码！
        String encodedPassword = passwordEncoder.encode(password);
        User newUser = new User(username, encodedPassword, 0);
        return userRepository.save(newUser);
    }

    /**
     * (修改) 排行榜逻辑现在使用 JPA
     */
    @Transactional(readOnly = true)
    public List<User> getLeaderboard() {
        return userRepository.findAllByOrderByScoreDesc();
    }

    /**
     * (修改) 积分结算逻辑现在使用 JPA
     */
    public void applyGameResult(String winnerUsername, String loserUsername) {
        // (修改) 现在我们确信用户存在 (因为他们登录了)
        User winner = userRepository.findByUsername(winnerUsername)
                .orElseThrow(() -> new UsernameNotFoundException("未找到胜利者: " + winnerUsername));
        User loser = userRepository.findByUsername(loserUsername)
                .orElseThrow(() -> new UsernameNotFoundException("未找到失败者: " + loserUsername));

        winner.setScore(winner.getScore() + 3);
        loser.setScore(Math.max(0, loser.getScore() - 2));

        userRepository.save(winner);
        userRepository.save(loser);
    }
}