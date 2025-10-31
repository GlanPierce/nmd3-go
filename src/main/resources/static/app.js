document.addEventListener('DOMContentLoaded', () => {

    // --- 全局状态 ---
    let currentUser = null; // (新增) 存储已登录用户

    // (注意) 游戏相关的全局变量已被移除，将在 matchmaking 分支加回
    // let currentGameId = null;
    // let stompClient = null;
    // ...

    // --- DOM 元素 ---

    // (新增) 认证相关
    const authOverlay = document.getElementById('auth-overlay');
    const authBox = document.getElementById('auth-box');
    const appRoot = document.getElementById('app-root');
    const loginForm = document.getElementById('login-form');
    const loginUsernameEl = document.getElementById('login-username');
    const loginPasswordEl = document.getElementById('login-password');
    const registerForm = document.getElementById('register-form');
    const registerUsernameEl = document.getElementById('register-username');
    const registerPasswordEl = document.getElementById('register-password');
    const authStatus = document.getElementById('auth-status');

    // (新增) 用户信息栏
    const userInfoBar = document.getElementById('user-info-bar');
    const userInfoUsername = document.getElementById('user-info-username');
    const userInfoScore = document.getElementById('user-info-score');
    const logoutBtn = document.getElementById('logout-btn');

    // (新增) 大厅 (为下一分支做准备)
    const lobbyContainer = document.getElementById('lobby-container');

    // (修改) 游戏相关 (为下一分支做准备)
    const gameContainer = document.getElementById('game-container');

    // (不变) 排行榜
    const leaderboardList = document.getElementById('leaderboard-list');
    const refreshLeaderboardBtn = document.getElementById('refresh-leaderboard-btn');


    // --- 事件监听 ---

    // (不变) 登录表单
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = loginUsernameEl.value;
        const password = loginPasswordEl.value;
        authStatus.textContent = '登录中...';
        authStatus.style.color = '#333';

        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (!response.ok) {
                const errorMsg = await response.text();
                throw new Error(errorMsg);
            }

            const user = await response.json();
            showApp(user);

        } catch (error) {
            authStatus.textContent = `登录失败: ${error.message}`;
            authStatus.style.color = '#d9534f';
        }
    });

    // --- (已修改) 注册表单 (添加了验证逻辑) ---
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = registerUsernameEl.value;
        const password = registerPasswordEl.value;
        authStatus.style.color = '#d9534f'; // 默认为错误色

        // --- (新增) 验证逻辑 ---

        // 1. 用户名验证: 3-16 位，只能包含字母、数字和下划线
        const usernameRegex = /^[a-zA-Z0-9_]{3,16}$/;
        if (!usernameRegex.test(username)) {
            authStatus.textContent = '注册失败: 用户名必须为 3-16 位的字母、数字或下划线。';
            return; // 阻止提交
        }

        // 2. 密码验证: 至少 8 位，且必须包含至少一个大写字母、一个小写字母和一个数字
        // (?=.*[a-z]) - 至少一个小写
        // (?=.*[A-Z]) - 至少一个大写
        // (?=.*\d) - 至少一个数字
        // [a-zA-Z\d\S]{8,} - 至少 8 位，可以包含字母、数字或任何非空白符号
        const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d\S]{8,}$/;
        if (!passwordRegex.test(password)) {
            authStatus.textContent = '注册失败: 密码必须至少 8 位，且包含大写、小写字母和数字。';
            return; // 阻止提交
        }

        // --- (新增) 验证结束 ---

        // 如果通过验证:
        authStatus.textContent = '注册中...';
        authStatus.style.color = '#333';

        try {
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (!response.ok) {
                const errorMsg = await response.text();
                throw new Error(errorMsg);
            }

            authStatus.textContent = '注册成功！请登录。';
            authStatus.style.color = '#28a745'; // 成功色
            registerForm.reset(); // 清空注册表单

        } catch (error) {
            authStatus.textContent = `注册失败: ${error.message}`;
            authStatus.style.color = '#d9534f';
        }
    });

    // (不变) 登出按钮
    logoutBtn.addEventListener('click', async () => {
        await fetch('/api/auth/logout', { method: 'POST' });
        currentUser = null;

        showLogin();
    });

    // (不变) 排行榜
    refreshLeaderboardBtn.addEventListener('click', fetchLeaderboard);


    // --- 核心功能函数 ---

    /**
     * (不变) 检查登录状态 (页面加载时调用)
     */
    async function checkLoginStatus() {
        try {
            const response = await fetch('/api/auth/me'); // 检查 session
            if (!response.ok) {
                throw new Error('未登录');
            }
            const user = await response.json();
            showApp(user); // 已登录，直接显示应用
        } catch (error) {
            showLogin(); // 未登录，显示登录界面
        }
    }

    /**
     * (不变) 显示登录界面
     */
    function showLogin() {
        authOverlay.style.display = 'flex';
        appRoot.style.display = 'none';
        lobbyContainer.style.display = 'none';
        gameContainer.style.display = 'none';
    }

    /**
     * (不变) 显示主应用
     */
    function showApp(user) {
        currentUser = user;

        // 隐藏登录，显示应用
        authOverlay.style.display = 'none';
        appRoot.style.display = 'flex';
        lobbyContainer.style.display = 'block';

        // 填充用户信息
        userInfoUsername.textContent = user.username;
        userInfoScore.textContent = user.score;

        // 自动刷新排行榜
        fetchLeaderboard();
    }

    /**
     * (不变) 获取并渲染排行榜
     */
    async function fetchLeaderboard() {
        try {
            const response = await fetch('/api/leaderboard');
            if (!response.ok) throw new Error('无法加载排行榜');
            const leaderboard = await response.json();

            leaderboardList.innerHTML = ''; // 清空
            leaderboard.forEach(user => {
                const li = document.createElement('li');
                li.innerHTML = `<span>${user.username}</span> <span>${user.score} 积分</span>`;
                leaderboardList.appendChild(li);
            });
        } catch (error) {
            leaderboardList.innerHTML = `<li>${error.message}</li>`;
        }
    }

    // --- (删除) 所有旧的游戏逻辑 ---

    // --- 初始加载 ---
    checkLoginStatus(); // (修改) 页面加载时的唯一入口点
});