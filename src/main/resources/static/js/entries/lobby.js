import { UI, initUI } from '../core/ui.js';
import { handleLogout, checkLoginStatus, fetchLeaderboard, fetchActiveGame } from '../core/api.js';
import { connectMasterWebSocket, disconnectMasterWebSocket, initLobby } from '../managers/lobby-manager.js';

document.addEventListener('DOMContentLoaded', () => {
    try {
        initUI();
        console.log("UI Initialized (Lobby)");
    } catch (e) {
        console.error("UI Init Failed: " + e.message);
        return;
    }

    if (UI.logoutBtn) {
        UI.logoutBtn.addEventListener('click', onLogout);
    }

    initLobby();

    if (UI.refreshLeaderboardBtn) {
        UI.refreshLeaderboardBtn.addEventListener('click', fetchLeaderboard);
    }

    checkLoginStatus(onLoginSuccess, onLoginFail);
});

async function onLoginSuccess(user) {
    console.log("登录成功:", user);

    if (UI.userInfoUsername) UI.userInfoUsername.textContent = user.username;
    if (UI.userInfoScore) UI.userInfoScore.textContent = user.score;
    if (document.getElementById('user-avatar-small')) {
        const avatar = user.avatar || 'avatar_1.svg';
        document.getElementById('user-avatar-small').src = `assets/avatars/${avatar}`;
    }

    localStorage.setItem('username', user.username);

    try {
        // Check for active game first
        const activeGame = await fetchActiveGame();
        if (activeGame) {
            console.log("发现活跃游戏，跳转到游戏页面...");
            window.location.href = 'game.html';
            return;
        }

        // Connect to WS for matchmaking
        await connectMasterWebSocket(user);

    } catch (error) {
        console.error("连接服务器失败:", error);
    }

    fetchLeaderboard();
}

function onLoginFail() {
    console.log("未登录，重定向到登录页面。");
    window.location.href = 'login.html';
}

function onLogout() {
    handleLogout(() => {
        disconnectMasterWebSocket();
        localStorage.removeItem('username');
        window.location.href = 'login.html';
    });
}
