import { UI, initUI } from './ui.js';
import { handleLogin, handleRegister, handleLogout, checkLoginStatus, fetchLeaderboard } from './api.js';
import { connectMasterWebSocket, disconnectMasterWebSocket, initLobby } from './lobby.js';
import { initGameControls } from './game.js';

/**
 * 整个应用的“状态机”
 */
document.addEventListener('DOMContentLoaded', () => {

    // (关键修复)
    // 1. 立即调用 initUI()，填充 UI 对象
    initUI();

    // 2. 现在 UI 对象已填充，我们可以安全地绑定事件了
    UI.loginForm.addEventListener('submit', (e) => handleLogin(e, onLoginSuccess));
    UI.registerForm.addEventListener('submit', handleRegister);
    UI.logoutBtn.addEventListener('click', onLogout);

    initLobby();
    initGameControls();
    UI.refreshLeaderboardBtn.addEventListener('click', fetchLeaderboard);

    // 3. (入口) 检查用户是否已登录
    checkLoginStatus(onLoginSuccess, onLoginFail);
});

/**
 * 登录成功的回调
 */
function onLoginSuccess(user) {
    console.log("登录成功:", user);

    // 1. 隐藏登录，显示应用
    UI.authOverlay.style.display = 'none';
    UI.appRoot.style.display = 'flex';
    UI.lobbyContainer.style.display = 'block';
    UI.gameContainer.style.display = 'none';

    // 2. 填充用户信息
    UI.userInfoUsername.textContent = user.username;
    UI.userInfoScore.textContent = user.score;

    // 3. (关键修改)
    // 将 user 对象 (包含 user.id) 传递给 lobby
    connectMasterWebSocket(user, onReturnToLobby);

    // 4. 自动刷新排行榜
    fetchLeaderboard();
}

/**
 * 登出或登录失败的回调
 */
function onLoginFail() {
    console.log("未登录，显示登录界面。");
    UI.authOverlay.style.display = 'flex';
    UI.appRoot.style.display = 'none';
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
    UI.gameContainer.style.display = 'none';
    UI.lobbyContainer.style.display = 'block';

    UI.lobbyStatus.textContent = message;
    UI.findMatchBtn.disabled = false;

    // (注意) 重新连接/订阅 匹配队列
    // 我们需要在这里重新获取 user 对象，或者（更好的）将其存储在 main.js 中
    // checkLoginStatus(onLoginSuccess, onLoginFail); // 重新登录/检查
    // ...
    // (我们暂时保持简单，假设 connectMasterWebSocket 仍然有效)
}