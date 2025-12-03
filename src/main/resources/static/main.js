import { UI, initUI } from './ui.js';
import { handleLogin, handleRegister, handleLogout, checkLoginStatus, fetchLeaderboard, fetchActiveGame } from './api.js';
import { connectMasterWebSocket, disconnectMasterWebSocket, initLobby } from './lobby.js';
import { initGameControls, restoreGame } from './game.js';

/**
 * 整个应用的"状态机"
 */
document.addEventListener('DOMContentLoaded', () => {

    // (关键修复)
    // 1. 立即调用 initUI()，填充 UI 对象
    try {
        initUI();
        console.log("UI Initialized");
    } catch (e) {
        alert("UI Init Failed: " + e.message);
        console.error(e);
    }

    // 2. 现在 UI 对象已填充，我们可以安全地绑定事件了
    if (UI.loginForm) {
        UI.loginForm.addEventListener('submit', (e) => handleLogin(e, onLoginSuccess));
    } else {
        console.error("UI.loginForm is missing");
    }

    if (UI.registerForm) {
        UI.registerForm.addEventListener('submit', handleRegister);
    }

    if (UI.logoutBtn) {
        UI.logoutBtn.addEventListener('click', onLogout);
    }

    initLobby();
    initGameControls();

    if (UI.refreshLeaderboardBtn) {
        UI.refreshLeaderboardBtn.addEventListener('click', fetchLeaderboard);
    }

    // 3. (入口) 检查用户是否已登录
    checkLoginStatus(onLoginSuccess, onLoginFail);
});

/**
 * 登录成功的回调
 */
async function onLoginSuccess(user) {
    console.log("登录成功:", user);

    // 1. 隐藏登录，显示应用
    if (UI.authOverlay) UI.authOverlay.style.display = 'none';
    if (UI.appRoot) UI.appRoot.style.display = 'flex';
    if (UI.lobbyContainer) UI.lobbyContainer.style.display = 'block';
    if (UI.gameContainer) UI.gameContainer.style.display = 'none';

    // 2. 填充用户信息
    if (UI.userInfoUsername) UI.userInfoUsername.textContent = user.username;
    if (UI.userInfoScore) UI.userInfoScore.textContent = user.score;
    if (document.getElementById('user-avatar-small')) {
        // 如果 user 对象包含 avatar 字段，则使用它，否则使用默认值
        const avatar = user.avatar || 'avatar_1.svg';
        document.getElementById('user-avatar-small').src = `assets/avatars/${avatar}`;
    }

    // 3. (关键修改)
    // 将 user 对象 (包含 user.id) 传递给 lobby，并等待连接成功
    try {
        const stompClient = await connectMasterWebSocket(user, onReturnToLobby);

        // 4. (新增) 检查是否有活跃游戏，自动恢复
        const activeGame = await fetchActiveGame();
        if (activeGame) {
            console.log("发现活跃游戏，正在恢复...", activeGame);
            restoreGame(stompClient, activeGame, user, onReturnToLobby);
        } else {
            console.log("无活跃游戏，停留在 Lobby。");
        }

    } catch (error) {
        console.error("连接服务器失败:", error);
        // 可以选择在这里显示错误提示
    }

    // 5. 自动刷新排行榜
    fetchLeaderboard();
}

/**
 * 登出或登录失败的回调
 */
function onLoginFail() {
    console.log("未登录，显示登录界面。");
    if (UI.authOverlay) UI.authOverlay.style.display = 'flex';
    if (UI.appRoot) UI.appRoot.style.display = 'none';
}

/**
 * 登出按钮的回调
 */
function onLogout() {
    disconnectMasterWebSocket();
    onLoginFail(); // (修改) 调用 onLoginFail 来显示登录界面
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