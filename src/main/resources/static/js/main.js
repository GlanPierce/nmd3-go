import { UI, initUI } from './ui.js';
import { handleLogout, checkLoginStatus, fetchLeaderboard, fetchActiveGame } from './api.js';
import { connectMasterWebSocket, disconnectMasterWebSocket, initLobby } from './lobby.js';
import { initGameControls, restoreGame } from './game.js';

/**
 * 整个应用的"状态机"
 */
document.addEventListener('DOMContentLoaded', () => {

    // 1. 立即调用 initUI()，填充 UI 对象
    try {
        initUI();
        console.log("UI Initialized");
    } catch (e) {
        alert("UI Init Failed: " + e.message);
        console.error(e);
        return;
    }

    // 2. 绑定登出按钮
    if (UI.logoutBtn) {
        UI.logoutBtn.addEventListener('click', onLogout);
    }

    initLobby();
    initGameControls();

    if (UI.refreshLeaderboardBtn) {
        UI.refreshLeaderboardBtn.addEventListener('click', fetchLeaderboard);
    }

    // 3. 检查用户是否已登录
    checkLoginStatus(onLoginSuccess, onLoginFail);
});

/**
 * 登录成功的回调
 */
async function onLoginSuccess(user) {
    console.log("登录成功:", user);

    // 显示应用
    if (UI.appRoot) UI.appRoot.style.display = 'flex';
    if (UI.lobbyContainer) UI.lobbyContainer.style.display = 'block';
    if (UI.gameContainer) UI.gameContainer.style.display = 'none';

    // 填充用户信息
    if (UI.userInfoUsername) UI.userInfoUsername.textContent = user.username;
    if (UI.userInfoScore) UI.userInfoScore.textContent = user.score;
    if (document.getElementById('user-avatar-small')) {
        const avatar = user.avatar || 'avatar_1.svg';
        document.getElementById('user-avatar-small').src = `assets/avatars/${avatar}`;
    }

    // Store username in localStorage for replay viewer
    localStorage.setItem('username', user.username);

    // 将 user 对象传递给 lobby，并等待连接成功
    try {
        const stompClient = await connectMasterWebSocket(user, onReturnToLobby);

        // 检查是否有活跃游戏，自动恢复
        const activeGame = await fetchActiveGame();
        if (activeGame) {
            console.log("发现活跃游戏，正在恢复...", activeGame);
            restoreGame(stompClient, activeGame, user, onReturnToLobby);
        } else {
            console.log("无活跃游戏，停留在 Lobby。");
        }

    } catch (error) {
        console.error("连接服务器失败:", error);
    }

    // 自动刷新排行榜
    fetchLeaderboard();
}

/**
 * 登录失败的回调 - 重定向到登录页面
 */
function onLoginFail() {
    console.log("未登录，重定向到登录页面。");
    window.location.href = 'login.html';
}

/**
 * 登出按钮的回调
 */
function onLogout() {
    handleLogout(() => {
        disconnectMasterWebSocket();
        // Clear username from localStorage
        localStorage.removeItem('username');
        // Redirect to login page
        window.location.href = 'login.html';
    });
}

/**
 * 游戏结束/取消时，Game.js 会调用此回调
 */
function onReturnToLobby(message) {
    console.log("返回大厅:", message);
    if (UI.gameContainer) UI.gameContainer.style.display = 'none';
    if (UI.lobbyContainer) UI.lobbyContainer.style.display = 'block';

    if (UI.lobbyStatus) UI.lobbyStatus.textContent = message;
    if (UI.findMatchBtn) UI.findMatchBtn.disabled = false;

    // Refresh user info (score, etc.)
    import('./api.js').then(module => {
        module.checkLoginStatus((user) => {
            if (UI.userInfoUsername) UI.userInfoUsername.textContent = user.username;
            if (UI.userInfoScore) UI.userInfoScore.textContent = user.score;
        }, () => { });
    });
}