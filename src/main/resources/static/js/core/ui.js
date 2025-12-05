export const UI = {};

export function initUI() {
    // --- App Root ---
    UI.appRoot = document.getElementById('app-root');

    // --- User Info ---
    UI.userInfoBar = document.getElementById('user-info-bar');
    UI.userInfoUsername = document.getElementById('user-info-username');
    UI.userInfoScore = document.getElementById('user-info-score');
    UI.userAvatarSmall = document.getElementById('user-avatar-small');
    UI.profileBtn = document.getElementById('profile-btn');
    UI.logoutBtn = document.getElementById('logout-btn');

    // --- Profile Modal ---
    UI.profileModal = document.getElementById('profile-modal');
    UI.profileUsername = document.getElementById('profile-username');
    UI.profileScore = document.getElementById('profile-score');
    UI.profileCurrentAvatar = document.getElementById('profile-current-avatar');
    UI.avatarOptions = document.querySelectorAll('.avatar-option');
    UI.saveAvatarBtn = document.getElementById('save-avatar-btn');
    UI.closeProfileBtn = document.getElementById('close-profile-btn');

    // [NEW] Sidebar Elements
    UI.tabProfileBtn = document.getElementById('tab-profile-btn');
    UI.tabHistoryBtn = document.getElementById('tab-history-btn');
    UI.tabCollectionBtn = document.getElementById('tab-collection-btn');
    UI.sectionProfile = document.getElementById('section-profile');
    UI.sectionHistory = document.getElementById('section-history');
    UI.sectionCollection = document.getElementById('section-collection');
    UI.historyListContainer = document.getElementById('history-list-container');
    UI.collectionListContainer = document.getElementById('collection-list-container');

    // --- Lobby ---
    UI.lobbyContainer = document.getElementById('lobby-container');
    UI.findMatchBtn = document.getElementById('find-match-btn');
    UI.lobbyStatus = document.getElementById('lobby-status');

    // --- Game ---
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

    // --- Modals ---
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

    // --- Leaderboard ---
    UI.leaderboardList = document.getElementById('leaderboard-list');
    UI.refreshLeaderboardBtn = document.getElementById('refresh-leaderboard-btn');

    // --- Helper Methods ---

    UI.showMatchFoundModal = (opponentName) => {
        UI.matchOpponentNameEl.textContent = opponentName;
        UI.matchReadyBtn.disabled = false;
        UI.matchFoundModal.style.display = 'flex';
    };

    UI.hideMatchFoundModal = () => {
        UI.matchFoundModal.style.display = 'none';
    };

    UI.isMatchModalVisible = () => {
        return UI.matchFoundModal.style.display === 'flex';
    };

    UI.setMatchStatus = (msg) => {
        UI.matchStatusMessageEl.textContent = msg;
    };

    UI.bindMatchReadyBtn = (callback) => {
        UI.matchReadyBtn.onclick = callback;
    };

    UI.disableMatchReadyBtn = () => {
        UI.matchReadyBtn.disabled = true;
    };

    UI.updateMatchConfirmTimer = (seconds) => {
        UI.matchConfirmTimerEl.textContent = seconds;
    };

    UI.switchToGameView = () => {
        UI.lobbyContainer.style.display = 'none';
        UI.gameContainer.style.display = 'block';
    };

    UI.updateGameInfo = (state, myPlayerId, myUsername) => {
        UI.p1NameEl.textContent = state.p1Username;
        UI.p2NameEl.textContent = state.p2Username;
        UI.myIdentityEl.textContent = `${myPlayerId} (${myUsername})`;
        UI.gameIdSpan.textContent = state.gameId;
        UI.p1ExtraEl.textContent = state.p1ExtraTurns;
        UI.p2ExtraEl.textContent = state.p2ExtraTurns;
        UI.statusMessageEl.textContent = state.statusMessage;
    };

    UI.updateTimers = (p1Time, p2Time) => {
        const p1Sec = p1Time < 0 ? "--" : Math.ceil(p1Time / 1000);
        const p2Sec = p2Time < 0 ? "--" : Math.ceil(p2Time / 1000);
        UI.p1TimerEl.textContent = p1Sec;
        UI.p2TimerEl.textContent = p2Sec;
        UI.p1TimerEl.parentElement.classList.toggle('timing-out', p1Sec > 0 && p1Sec <= 5);
        UI.p2TimerEl.parentElement.classList.toggle('timing-out', p2Sec > 0 && p2Sec <= 5);
    };

    UI.showGameOverModal = (state, onPlayAgain) => {
        const res = state.result;
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
            if (onPlayAgain) onPlayAgain();
        };
    };

    UI.renderBoard = (board, myPlayerId) => {
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
    };

    function addIndicator(squareEl, player) {
        squareEl.classList.add(player + '-ambush');
        const ambushEl = document.createElement('div');
        ambushEl.classList.add('ambush-indicator', player);
        squareEl.appendChild(ambushEl);
    }
}