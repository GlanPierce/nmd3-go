document.addEventListener('DOMContentLoaded', () => {

    // --- 全局状态 ---
    let currentGameId = null;
    let myPlayerId = 'p1'; // 默认 P1
    let currentGameState = null;
    let pollingInterval = null;

    // --- DOM 元素 ---
    const boardElement = document.getElementById('board');
    const playerSelect = document.getElementById('player-select');
    const startGameBtn = document.getElementById('start-game-btn');
    const refreshBtn = document.getElementById('refresh-btn');
    const gameIdSpan = document.getElementById('game-id');
    const statusMessageEl = document.getElementById('status-message');
    const p1NameEl = document.getElementById('p1-name');
    const p2NameEl = document.getElementById('p2-name');
    const p1ExtraEl = document.getElementById('p1-extra');
    const p2ExtraEl = document.getElementById('p2-extra');

    // (已添加) 计时器元素
    const p1TimerEl = document.getElementById('p1-timer');
    const p2TimerEl = document.getElementById('p2-timer');

    // (已添加) 开发者模式
    const devModeToggle = document.getElementById('dev-mode-toggle');

    // 弹窗
    const modal = document.getElementById('game-over-modal');
    const winnerMsgEl = document.getElementById('winner-message');
    const p1ResultEl = document.getElementById('p1-result');
    const p2ResultEl = document.getElementById('p2-result');
    const playAgainBtn = document.getElementById('play-again-btn');

    // 排行榜
    const leaderboardList = document.getElementById('leaderboard-list');
    const refreshLeaderboardBtn = document.getElementById('refresh-leaderboard-btn');

    // --- 事件监听 ---

    // 切换玩家身份
    playerSelect.addEventListener('change', (e) => {
        myPlayerId = e.target.value;
        if (currentGameState) {
            renderBoard(currentGameState.board);
        }
    });

    // 监听开发者模式切换
    devModeToggle.addEventListener('change', () => {
        if (currentGameState) {
            renderBoard(currentGameState.board);
        }
    });

    // 开始游戏
    startGameBtn.addEventListener('click', async () => {
        // P1 和 P2 的名字是硬编码的, 对应 UserRepository
        const p1 = "NinjaA";
        const p2 = "ShadowB";

        try {
            const response = await fetch(`/api/game/start?p1=${p1}&p2=${p2}`, { method: 'POST' });
            if (!response.ok) throw new Error('无法开始游戏');
            const gameState = await response.json();

            currentGameId = gameState.gameId;
            gameIdSpan.textContent = currentGameId;
            myPlayerId = 'p1'; // 新游戏默认你是 P1
            playerSelect.value = 'p1';
            modal.style.display = 'none'; // 关闭可能打开的弹窗

            updateUI(gameState);
            startPolling();
        } catch (error) {
            statusMessageEl.textContent = `错误: ${error.message}`;
        }
    });

    // 手动刷新
    refreshBtn.addEventListener('click', () => {
        if (currentGameId) {
            fetchGameState();
        }
    });

    // 棋盘点击
    boardElement.addEventListener('click', async (e) => {
        if (!currentGameState) return;

        const square = e.target.closest('.square');
        if (!square) return;

        const { r, c } = square.dataset;
        const phase = currentGameState.phase;

        let url = '';
        let body = { playerId: myPlayerId, r: parseInt(r), c: parseInt(c) };

        if (phase === 'AMBUSH') {
            // 检查是否轮到我放伏兵, 或者我是否已经放满了
            if (myPlayerId === 'p1' && currentGameState.p1AmbushesPlaced >= 2) {
                console.log("P1 伏兵已满");
                return;
            }
            if (myPlayerId === 'p2' && currentGameState.p2AmbushesPlaced >= 2) {
                console.log("P2 伏兵已满");
                return;
            }

            url = `/api/game/${currentGameId}/ambush`;

        } else if (phase === 'PLACEMENT' || phase === 'EXTRA_ROUNDS') {
            // 检查是否轮到我
            if (currentGameState.currentTurnPlayerId !== myPlayerId) {
                console.log("不是你的回合");
                return;
            }
            url = `/api/game/${currentGameId}/place`;
        } else {
            return; // 游戏结束或非行动阶段
        }

        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });

            const newState = await response.json();

            if (!response.ok) {
                // 后端返回的错误信息在 newState.message 或 newState.error
                const errorMsg = newState.message || (typeof newState === 'string' ? newState : '操作失败');
                throw new Error(errorMsg);
            }

            updateUI(newState);

        } catch (error) {
            statusMessageEl.textContent = `操作失败: ${error.message}`;
            // 失败后快速拉取一次正确状态
            setTimeout(fetchGameState, 500);
        }
    });

    // 弹窗相关
    playAgainBtn.addEventListener('click', () => {
        modal.style.display = 'none';
        startGameBtn.click(); // 模拟点击开始新游戏
    });

    // 排行榜
    refreshLeaderboardBtn.addEventListener('click', fetchLeaderboard);

    // --- 核心功能函数 ---

    // 轮询获取最新状态
    function startPolling() {
        if (pollingInterval) clearInterval(pollingInterval);
        // (已修改) 将轮询间隔缩短到 2 秒，以便计时器更平滑
        pollingInterval = setInterval(fetchGameState, 1000);
    }

    async function fetchGameState() {
        if (!currentGameId || (currentGameState && currentGameState.phase === 'GAME_OVER')) {
            if (pollingInterval) clearInterval(pollingInterval);
            return;
        }
        try {
            const response = await fetch(`/api/game/${currentGameId}/state`);
            if (!response.ok) throw new Error('网络错误');
            const gameState = await response.json();
            updateUI(gameState);
        } catch (error) {
            console.error('轮询失败:', error);
        }
    }

    // (已修改) 更新整个 UI
    function updateUI(state) {
        currentGameState = state; // 保存全局状态

        // 基础信息
        p1NameEl.textContent = state.p1Username;
        p2NameEl.textContent = state.p2Username;
        p1ExtraEl.textContent = state.p1ExtraTurns;
        p2ExtraEl.textContent = state.p2ExtraTurns;
        statusMessageEl.textContent = state.statusMessage;

        // --- (新增) 更新计时器显示 ---
        // -1 意味着计时器未激活
        const p1Time = state.p1TimeLeft < 0 ? "--" : Math.ceil(state.p1TimeLeft / 1000);
        const p2Time = state.p2TimeLeft < 0 ? "--" : Math.ceil(state.p2TimeLeft / 1000);

        p1TimerEl.textContent = p1Time;
        p2TimerEl.textContent = p2Time;

        // (可选) 为快要超时的玩家添加高亮
        p1TimerEl.parentElement.classList.toggle('timing-out', state.p1TimeLeft > 0 && state.p1TimeLeft < 5000);
        p2TimerEl.parentElement.classList.toggle('timing-out', state.p2TimeLeft > 0 && state.p2TimeLeft < 5000);
        // --- (新增) 结束 ---

        // 渲染棋盘
        renderBoard(state.board);

        // 检查游戏是否结束
        if (state.phase === 'GAME_OVER' && modal.style.display === 'none') {
            if (pollingInterval) clearInterval(pollingInterval);
            showGameOverModal(state);
            fetchLeaderboard(); // 游戏结束时自动刷新排行榜
        }
    }

    // (已修改) 渲染棋盘
    function renderBoard(board) {
        boardElement.innerHTML = ''; // 清空棋盘

        // 获取开发者模式的当前状态
        const isDevMode = devModeToggle.checked;

        for (let r = 0; r < 6; r++) {
            for (let c = 0; c < 6; c++) {
                const squareData = board.grid[r][c];
                const squareEl = document.createElement('div');
                squareEl.classList.add('square');
                squareEl.dataset.r = r;
                squareEl.dataset.c = c;

                // 1. 显示落子
                if (squareData.ownerId) {
                    const pieceEl = document.createElement('div');
                    pieceEl.classList.add('piece', squareData.ownerId); // 'p1' or 'p2'
                    squareEl.appendChild(pieceEl);
                }

                // 2. 显示伏兵 (根据模式决定)
                if (isDevMode) {
                    // 开发者模式: 显示所有伏兵
                    if (squareData.p1Ambush) {
                        squareEl.classList.add('p1-ambush');
                        const ambushEl = document.createElement('div');
                        ambushEl.classList.add('ambush-indicator', 'p1');
                        squareEl.appendChild(ambushEl);
                    }
                    if (squareData.p2Ambush) {
                        squareEl.classList.add('p2-ambush');
                        const ambushEl = document.createElement('div');
                        ambushEl.classList.add('ambush-indicator', 'p2');
                        squareEl.appendChild(ambushEl);
                    }
                } else {
                    // 玩家模式: 仅显示我方伏兵
                    if (myPlayerId === 'p1' && squareData.p1Ambush) {
                        squareEl.classList.add('p1-ambush');
                        const ambushEl = document.createElement('div');
                        ambushEl.classList.add('ambush-indicator', 'p1');
                        squareEl.appendChild(ambushEl);
                    } else if (myPlayerId === 'p2' && squareData.p2Ambush) {
                        squareEl.classList.add('p2-ambush');
                        const ambushEl = document.createElement('div');
                        ambushEl.classList.add('ambush-indicator', 'p2');
                        squareEl.appendChild(ambushEl);
                    }
                }

                boardElement.appendChild(squareEl);
            }
        }
    }

    // 显示游戏结束弹窗
    function showGameOverModal(state) {
        const res = state.result;
        if (res.winnerId === 'DRAW') {
            winnerMsgEl.textContent = '平局!';
        } else {
            const winnerName = res.winnerId === 'p1' ? state.p1Username : state.p2Username;
            winnerMsgEl.textContent = `胜利者: ${winnerName} (${res.winnerId})!`;
        }

        p1ResultEl.textContent = `最大连接: ${res.p1MaxConnection}, 总子数: ${res.p1PieceCount}`;
        p2ResultEl.textContent = `最大连接: ${res.p2MaxConnection}, 总子数: ${res.p2PieceCount}`;

        modal.style.display = 'block';
    }

    // 获取并渲染排行榜
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

    // --- 初始加载 ---
    createEmptyBoard(); // 先画一个空棋盘
    fetchLeaderboard(); // 页面加载时获取一次排行榜

    function createEmptyBoard() {
        for (let r = 0; r < 6; r++) {
            for (let c = 0; c < 6; c++) {
                const squareEl = document.createElement('div');
                squareEl.classList.add('square');
                squareEl.dataset.r = r;
                squareEl.dataset.c = c;
                boardElement.appendChild(squareEl);
            }
        }
    }
});