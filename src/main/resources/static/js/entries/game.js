import { UI, initUI } from '../core/ui.js';
import { handleLogout, checkLoginStatus, fetchActiveGame } from '../core/api.js';
import { connectMasterWebSocket, disconnectMasterWebSocket } from '../managers/lobby-manager.js';
import { initGameControls, restoreGame } from '../managers/game-setup.js';

document.addEventListener('DOMContentLoaded', () => {
    try {
        initUI();
        console.log("UI Initialized (Game)");
    } catch (e) {
        console.error("UI Init Failed: " + e.message);
        return;
    }

    if (UI.logoutBtn) {
        UI.logoutBtn.addEventListener('click', onLogout);
    }

    // Also bind refresh button
    if (document.getElementById('refresh-btn')) {
        document.getElementById('refresh-btn').addEventListener('click', () => {
            window.location.reload();
        });
    }

    initGameControls();

    checkLoginStatus(onLoginSuccess, onLoginFail);
});

async function onLoginSuccess(user) {
    console.log("登录成功:", user);

    if (UI.userInfoUsername) UI.userInfoUsername.textContent = user.username;
    if (document.getElementById('user-avatar-small')) {
        const avatar = user.avatar || 'avatar_1.svg';
        document.getElementById('user-avatar-small').src = `assets/avatars/${avatar}`;
    }

    localStorage.setItem('username', user.username);

    try {
        // Connect WS first
        const stompClient = await connectMasterWebSocket(user, onReturnToLobby);

        // Fetch active game
        const activeGame = await fetchActiveGame();
        if (activeGame) {
            console.log("正在恢复游戏...", activeGame);
            restoreGame(stompClient, activeGame, user, onReturnToLobby);
        } else {
            console.log("无活跃游戏，返回大厅。");
            window.location.href = 'lobby.html';
        }

    } catch (error) {
        console.error("连接服务器失败:", error);
        alert("连接失败，请刷新重试");
    }
}

function onLoginFail() {
    console.log("未登录，重定向到登录页面。");
    window.location.href = 'login.html';
}

function onLogout() {
    if (confirm("确定要退出吗？")) {
        handleLogout(() => {
            disconnectMasterWebSocket();
            localStorage.removeItem('username');
            window.location.href = 'login.html';
        });
    }
}

function onReturnToLobby(message) {
    console.log("返回大厅:", message);
    window.location.href = 'lobby.html';
}
