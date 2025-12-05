// (修改) 只导入 UI 对象
import { UI } from './ui.js';

// 注册
export async function handleRegister(event) {
    event.preventDefault();
    const username = UI.registerUsernameEl.value;
    const password = UI.registerPasswordEl.value;
    UI.authStatus.style.color = '#d9534f';

    // 验证
    const usernameRegex = /^[a-zA-Z0-9_]{3,16}$/;
    if (!usernameRegex.test(username)) {
        UI.authStatus.textContent = '注册失败: 用户名必须为 3-16 位的字母、数字或下划线。';
        return;
    }
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d\S]{8,}$/;
    if (!passwordRegex.test(password)) {
        UI.authStatus.textContent = '注册失败: 密码必须至少 8 位，且包含大写、小写字母和数字。';
        return;
    }

    setAuthStatus('注册中...', '#333');
    try {
        const response = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (!response.ok) throw new Error(await response.text());
        setAuthStatus('注册成功！请登录。', '#28a745');
        UI.registerForm.reset();
    } catch (error) {
        setAuthStatus(`注册失败: ${error.message}`, '#d9534f');
    }
}

// 登录
export async function handleLogin(event, onLoginSuccess) {
    event.preventDefault();
    const username = UI.loginUsernameEl.value;
    const password = UI.loginPasswordEl.value;
    setAuthStatus('登录中...', '#333');

    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (!response.ok) throw new Error(await response.text());
        const user = await response.json();
        onLoginSuccess(user); // 登录成功，调用主回调
    } catch (error) {
        console.error(error);
        alert("Login Error: " + error.message); // Debug
        setAuthStatus(`登录失败: ${error.message}`, '#d9534f');
    }
}

// 登出
export async function handleLogout(onLogoutSuccess) {
    await fetch('/api/auth/logout', { method: 'POST' });
    onLogoutSuccess();
}

// 检查状态
export async function checkLoginStatus(onLoginSuccess, onLoginFail) {
    try {
        const response = await fetch('/api/auth/me');
        if (!response.ok) throw new Error('未登录');
        const user = await response.json();
        onLoginSuccess(user); // 已登录
    } catch (error) {
        onLoginFail(); // 未登录
    }
}

// 排行榜
export async function fetchLeaderboard() {
    try {
        const response = await fetch('/api/leaderboard');
        if (!response.ok) throw new Error('无法加载排行榜');
        const leaderboard = await response.json();

        UI.leaderboardList.innerHTML = ''; // 清空
        leaderboard.forEach(user => {
            const li = document.createElement('li');
            // Update: Display Elo and games played
            li.innerHTML = `<span>${user.username}</span> <span>Elo: ${user.score} <small>(${user.gamesPlayed} 场)</small></span>`;
            UI.leaderboardList.appendChild(li);
        });
    } catch (error) {
        UI.leaderboardList.innerHTML = `<li>${error.message}</li>`;
    }
}

// (新增) 检查是否有活跃游戏
export async function fetchActiveGame() {
    try {
        const response = await fetch('/api/game/active');
        if (response.status === 204) return null; // No active game
        if (!response.ok) throw new Error('Failed to fetch active game');
        return await response.json();
    } catch (error) {
        console.error("Error fetching active game:", error);
        return null;
    }
}

// 辅助函数
function setAuthStatus(message, color) {
    if (UI.authStatus) {
        UI.authStatus.textContent = message;
        UI.authStatus.style.color = color;
    }
}