// (修改) 1. 导出一个 *空* 对象。
// 因为模块是单例，当 main.js 填充它时，
// 其他导入了 UI 的模块也会看到填充后的内容。
export const UI = {};

// (修改) 2. 创建一个 init 函数来填充这个对象
export function initUI() {

    // --- 认证 ---
    UI.authOverlay = document.getElementById('auth-overlay');
    UI.authBox = document.getElementById('auth-box');
    UI.appRoot = document.getElementById('app-root');
    UI.loginForm = document.getElementById('login-form');
    UI.loginUsernameEl = document.getElementById('login-username');
    UI.loginPasswordEl = document.getElementById('login-password');
    UI.registerForm = document.getElementById('register-form');
    UI.registerUsernameEl = document.getElementById('register-username');
    UI.registerPasswordEl = document.getElementById('register-password');
    UI.authStatus = document.getElementById('auth-status');

    // --- 用户信息栏 ---
    UI.userInfoBar = document.getElementById('user-info-bar');
    UI.userInfoUsername = document.getElementById('user-info-username');
    UI.userInfoScore = document.getElementById('user-info-score');
    UI.logoutBtn = document.getElementById('logout-btn');

    // --- 大厅 ---
    UI.lobbyContainer = document.getElementById('lobby-container');
    UI.findMatchBtn = document.getElementById('find-match-btn');
    UI.lobbyStatus = document.getElementById('lobby-status');

    // --- 游戏 ---
    UI.gameContainer = document.getElementById('game-container');
    UI.boardElement = document.getElementById('board');
    UI.refreshBtn = document.getElementById('refresh-btn');
    UI.gameIdSpan = document.getElementById('game-id');
    UI.devModeToggle = document.getElementById('dev-mode-toggle');
    UI.myIdentityEl = document.getElementById('my-identity');
    UI.statusMessageEl = document.getElementById('status-message');
    UI.p1NameEl = document.getElementById('p1-name');
    UI.p2NameEl = document.getElementById('p2-name');
    UI.p1ExtraEl = document.getElementById('p1-extra');
    UI.p2ExtraEl = document.getElementById('p2-extra');
    UI.p1TimerEl = document.getElementById('p1-timer');
    UI.p2TimerEl = document.getElementById('p2-timer');

    // --- 弹窗 ---
    UI.modal = document.getElementById('game-over-modal');
    UI.winnerMsgEl = document.getElementById('winner-message');
    UI.p1ResultEl = document.getElementById('p1-result');
    UI.p2ResultEl = document.getElementById('p2-result');
    UI.playAgainBtn = document.getElementById('play-again-btn');
    UI.matchFoundModal = document.getElementById('match-found-modal');
    UI.matchOpponentNameEl = document.getElementById('match-opponent-name');
    UI.matchConfirmTimerEl = document.getElementById('match-confirm-timer');
    UI.matchReadyBtn = document.getElementById('match-ready-btn');
    UI.matchStatusMessageEl = document.getElementById('match-status-message');

    // --- 排行榜 ---
    UI.leaderboardList = document.getElementById('leaderboard-list');
    UI.refreshLeaderboardBtn = document.getElementById('refresh-leaderboard-btn');
}