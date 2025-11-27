import { UI } from './ui.js';

// 游戏状态
let stompClient = null;
let currentGameState = null;
let currentGameId = null;
let myPlayerId = null;
let currentGameSubscription = null;
let localTimerInterval = null;
let matchConfirmTimerInterval = null;
let onGameOverCallback = null; // 返回大厅的回调

// (新增) 全局存储双方的用户名
let opponentUsername = null;
let myUsername = null;

/**
 * (入口) 匹配成功后，由 lobby.js 调用
 */
export function initGame(client, matchResult, onLobbyReturn) {
    stompClient = client;
    onGameOverCallback = onLobbyReturn;

    // 1. 保存身份和用户名 (关键修改)
    currentGameId = matchResult.gameId;
    myPlayerId = matchResult.assignedPlayerId;

    // (新增) 存储双方用户名
    myUsername = (myPlayerId === 'p1') ? matchResult.p1Username : matchResult.p2Username;
    opponentUsername = (myPlayerId === 'p1') ? matchResult.p2Username : matchResult.p1Username;

    console.log(`匹配成功! 游戏ID: ${currentGameId}, 我的身份: ${myPlayerId}`);

    // 2. 显示匹配确认弹窗
    UI.matchOpponentNameEl.textContent = opponentUsername;
    UI.matchReadyBtn.disabled = false;
    UI.matchFoundModal.style.display = 'flex';

    // (关键修改) 3. 立即显示初始提示
    UI.matchStatusMessageEl.textContent = "点击 '准备' 确认进入游戏";

    // 4. 震动 (不变)
    if (navigator.vibrate) {
        navigator.vibrate([200, 100, 200]);
    }

    // 5. 启动30秒本地倒计时 (不变)
    let countdown = 30;
    UI.matchConfirmTimerEl.textContent = countdown;
    if (matchConfirmTimerInterval) clearInterval(matchConfirmTimerInterval);

    matchConfirmTimerInterval = setInterval(() => {
        countdown--;
        UI.matchConfirmTimerEl.textContent = countdown;

        if (countdown <= 0) {
            clearInterval(matchConfirmTimerInterval);
            UI.matchFoundModal.style.display = 'none';
            onGameOverCallback("匹配超时，已返回大厅。");
        }
    }, 1000);

    // 6. 订阅游戏主题 (不变)
    if (currentGameSubscription) {
        currentGameSubscription.unsubscribe();
    }
    currentGameSubscription = stompClient.subscribe('/topic/game/' + currentGameId, handleGameMessage);

    // 7. 为 "Ready" 按钮绑定事件 (不变)
    UI.matchReadyBtn.onclick = () => {
        if (stompClient && stompClient.connected && currentGameId) {
            console.log("发送 'ready' 消息...");
            stompClient.send(`/app/game/${currentGameId}/ready`, {}, JSON.stringify({ playerId: myPlayerId }));
            UI.matchReadyBtn.disabled = true;
            // (修改) 玩家点击后显示自己的用户名已准备
            UI.matchStatusMessageEl.textContent = `${myUsername} 已准备，等待对手...`;
        }
    };
}

/**
 * (新增) 恢复游戏状态 (重连)
 */
export function restoreGame(client, gameState, user, onLobbyReturn) {
    stompClient = client;
    onGameOverCallback = onLobbyReturn;
    currentGameId = gameState.gameId;

    // 确定身份
    if (user.username === gameState.p1Username) {
        myPlayerId = 'p1';
        myUsername = gameState.p1Username;
        opponentUsername = gameState.p2Username;
    } else if (user.username === gameState.p2Username) {
        myPlayerId = 'p2';
        myUsername = gameState.p2Username;
        opponentUsername = gameState.p1Username;
    } else {
        console.error("用户名不匹配，无法恢复游戏");
        return;
    }

    console.log(`恢复游戏成功! 游戏ID: ${currentGameId}, 我的身份: ${myPlayerId}`);

    // 隐藏匹配弹窗 (如果存在)
    UI.matchFoundModal.style.display = 'none';
    if (matchConfirmTimerInterval) clearInterval(matchConfirmTimerInterval);

    // 显示游戏界面
    UI.lobbyContainer.style.display = 'none';
    UI.gameContainer.style.display = 'block';

    // 填充基本信息
    UI.p1NameEl.textContent = gameState.p1Username;
    UI.p2NameEl.textContent = gameState.p2Username;
    UI.myIdentityEl.textContent = `${myPlayerId} (${myUsername})`;
    UI.gameIdSpan.textContent = gameState.gameId;

    // 订阅游戏主题
    if (currentGameSubscription) {
        currentGameSubscription.unsubscribe();
    }
    currentGameSubscription = stompClient.subscribe('/topic/game/' + currentGameId, handleGameMessage);

    // 立即更新 UI
    updateUI(gameState);
}

/**
 * 专门的游戏消息处理器
 */
function handleGameMessage(gameMessage) {
    const gameState = JSON.parse(gameMessage.body);

    // 检查匹配是否被取消
    if (gameState.phase === 'MATCH_CANCELLED') {
        console.log("匹配被服务器取消。");
        if (matchConfirmTimerInterval) clearInterval(matchConfirmTimerInterval);
        UI.matchFoundModal.style.display = 'none';
        onGameOverCallback(gameState.statusMessage || "匹配被取消。");
        return;
    }

    // 检查游戏是否刚刚开始 (从 PRE_GAME -> AMBUSH)
    const isFirstUpdate = (currentGameState === null || currentGameState.phase === 'PRE_GAME');
    const isGameStarting = (gameState.phase === 'AMBUSH');

    if (isFirstUpdate && isGameStarting) {
        console.log("双方准备就绪，游戏开始！");
        if (matchConfirmTimerInterval) clearInterval(matchConfirmTimerInterval);
        UI.matchFoundModal.style.display = 'none';

        // 填充游戏信息
        UI.p1NameEl.textContent = gameState.p1Username;
        UI.p2NameEl.textContent = gameState.p2Username;
        UI.myIdentityEl.textContent = `${myPlayerId} (${myUsername})`; // (修改) 使用 myUsername
        UI.gameIdSpan.textContent = gameState.gameId;

        UI.gameContainer.style.display = 'block'; // 正式显示游戏界面
    }

    // (修改) 检查是否只是一个 "P1 已准备" 的状态更新
    if (gameState.phase === 'PRE_GAME' && UI.matchFoundModal.style.display === 'flex') {
        // (关键修改) 将 "玩家 p1" 转换为实际的用户名
        let readyMessage = gameState.statusMessage;
        if (readyMessage && readyMessage.includes("玩家 p1 已准备")) {
            readyMessage = readyMessage.replace("玩家 p1", gameState.p1Username);
        }
        if (readyMessage && readyMessage.includes("玩家 p2 已准备")) {
            readyMessage = readyMessage.replace("玩家 p2", gameState.p2Username);
        }

        UI.matchStatusMessageEl.textContent = readyMessage;
        return; // 不要调用 updateUI，继续等待
    }

    // 更新游戏 UI
    updateUI(gameState);
}

/**
 * 更新游戏 UI (由 WebSocket 消息触发)
 */
function updateUI(state) {
    currentGameState = state;

    if (localTimerInterval) clearInterval(localTimerInterval);

    UI.p1ExtraEl.textContent = state.p1ExtraTurns;
    UI.p2ExtraEl.textContent = state.p2ExtraTurns;
    UI.statusMessageEl.textContent = state.statusMessage;

    renderBoard(state.board);

    // 处理新的计时器 (15秒回合计时)
    let p1Time = state.p1TimeLeft;
    let p2Time = state.p2TimeLeft;

    const updateTimerUI = () => {
        const p1Sec = p1Time < 0 ? "--" : Math.ceil(p1Time / 1000);
        const p2Sec = p2Time < 0 ? "--" : Math.ceil(p2Time / 1000);
        UI.p1TimerEl.textContent = p1Sec;
        UI.p2TimerEl.textContent = p2Sec;
        UI.p1TimerEl.parentElement.classList.toggle('timing-out', p1Sec > 0 && p1Sec <= 5);
        UI.p2TimerEl.parentElement.classList.toggle('timing-out', p2Sec > 0 && p2Sec <= 5);
    };

    updateTimerUI();

    if (state.phase !== 'PRE_GAME' && state.phase !== 'GAME_OVER' && (p1Time > 0 || p2Time > 0)) {
        localTimerInterval = setInterval(() => {
            if (p1Time > 0) p1Time -= 1000;
            if (p2Time > 0) p2Time -= 1000;
            updateTimerUI();
            if (p1Time <= 0 && p2Time <= 0) {
                clearInterval(localTimerInterval);
            }
        }, 1000);
    }

    // 检查游戏是否结束
    if (state.phase === 'GAME_OVER' && UI.modal.style.display === 'none') {
        showGameOverModal(state);
    }
}

/**
 * 渲染棋盘
 */
function renderBoard(board) {
    UI.boardElement.innerHTML = '';
    const isDevMode = UI.devModeToggle.checked;
    for (let r = 0; r < 6; r++) {
        for (let c = 0; c < 6; c++) {
            const squareData = board.grid[r][c];
            const squareEl = document.createElement('div');
            squareEl.classList.add('square');
            squareEl.dataset.r = r;
            squareEl.dataset.c = c;

            if (squareData.ownerId) {
                const pieceEl = document.createElement('div');
                pieceEl.classList.add('piece', squareData.ownerId);
                squareEl.appendChild(pieceEl);
            }
            if (isDevMode) {
                if (squareData.p1Ambush) { addIndicator(squareEl, 'p1'); }
                if (squareData.p2Ambush) { addIndicator(squareEl, 'p2'); }
            } else {
                if (myPlayerId === 'p1' && squareData.p1Ambush) { addIndicator(squareEl, 'p1'); }
                else if (myPlayerId === 'p2' && squareData.p2Ambush) { addIndicator(squareEl, 'p2'); }
            }
            UI.boardElement.appendChild(squareEl);
        }
    }
}
function addIndicator(squareEl, player) {
    squareEl.classList.add(player + '-ambush');
    const ambushEl = document.createElement('div');
    ambushEl.classList.add('ambush-indicator', player);
    squareEl.appendChild(ambushEl);
}

/**
 * 游戏结束弹窗
 */
function showGameOverModal(state) {
    if (localTimerInterval) clearInterval(localTimerInterval);

    const res = state.result;

    // (修改) 使用用户名显示胜负
    const winnerUsername = res.winnerId === 'p1' ? state.p1Username : state.p2Username;

    if (res.winnerId === 'DRAW') {
        UI.winnerMsgEl.textContent = '平局!';
    } else {
        UI.winnerMsgEl.textContent = `胜利者: ${winnerUsername} (${res.winnerId})!`;
    }

    UI.p1ResultEl.textContent = `最大连接: ${res.p1MaxConnection}, 总子数: ${res.p1PieceCount}`;
    UI.p2ResultEl.textContent = `最大连接: ${res.p2MaxConnection}, 总子数: ${res.p2PieceCount}`;

    UI.modal.style.display = 'block';

    UI.playAgainBtn.textContent = "返回大厅";
    UI.playAgainBtn.onclick = () => {
        UI.modal.style.display = 'none';
        onGameOverCallback("欢迎回来，请寻找新的对战。");
    };
}

/**
 * (入口) 棋盘点击 (不变)
 */
export function initGameControls() {
    UI.boardElement.addEventListener('click', (e) => {
        if (!currentGameState || !stompClient || !stompClient.connected || !myPlayerId) {
            return;
        }
        const square = e.target.closest('.square');
        if (!square) return;

        const { r, c } = square.dataset;
        const phase = currentGameState.phase;
        let url = '';
        let body = { playerId: myPlayerId, r: parseInt(r), c: parseInt(c) };

        if (phase === 'AMBUSH') {
            if (myPlayerId === 'p1' && currentGameState.p1AmbushesPlaced >= 2) return;
            if (myPlayerId === 'p2' && currentGameState.p2AmbushesPlaced >= 2) return;
            url = `/app/game/${currentGameId}/ambush`;
        } else if (phase === 'PLACEMENT' || phase === 'EXTRA_ROUNDS') {
            if (currentGameState.currentTurnPlayerId !== myPlayerId) return;
            url = `/app/game/${currentGameId}/place`;
        } else {
            return;
        }
        stompClient.send(url, {}, JSON.stringify(body));
    });

    // (不变) 开发者模式
    UI.devModeToggle.addEventListener('change', () => {
        if (currentGameState) {
            renderBoard(currentGameState.board);
        }
    });

    // (不变) 创建空棋盘 (用于游戏开始前)
    (function createEmptyBoard() {
        for (let r = 0; r < 6; r++) {
            for (let c = 0; c < 6; c++) {
                const squareEl = document.createElement('div');
                squareEl.classList.add('square');
                squareEl.dataset.r = r;
                squareEl.dataset.c = c;
                UI.boardElement.appendChild(squareEl);
            }
        }
    })();
}