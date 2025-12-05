import { UI } from '../core/ui.js';
import { PLAYER_1, PLAYER_2, GamePhase, API_ENDPOINTS } from '../core/constants.js';

export class GameManager {
    constructor() {
        this.stompClient = null;
        this.gameId = null;
        this.myPlayerId = null;
        this.myUsername = null;
        this.opponentUsername = null;
        this.gameState = null;
        this.subscription = null;
        this.localTimerInterval = null;
        this.matchConfirmTimerInterval = null;
        this.onGameOverCallback = null;
    }

    init(client, matchResult, onLobbyReturn) {
        this.stompClient = client;
        this.onGameOverCallback = onLobbyReturn;
        this.gameId = matchResult.gameId;
        this.myPlayerId = matchResult.assignedPlayerId;

        this.myUsername = (this.myPlayerId === PLAYER_1) ? matchResult.p1Username : matchResult.p2Username;
        this.opponentUsername = (this.myPlayerId === PLAYER_1) ? matchResult.p2Username : matchResult.p1Username;

        console.log(`Game Init: ID=${this.gameId}, Role=${this.myPlayerId}`);

        // UI Setup
        UI.showMatchFoundModal(this.opponentUsername);
        UI.setMatchStatus("点击 '准备' 确认进入游戏");

        if (navigator.vibrate) navigator.vibrate([200, 100, 200]);

        this.startMatchConfirmTimer();
        this.subscribeToGame();

        // Bind Ready Button
        UI.bindMatchReadyBtn(() => {
            if (this.stompClient && this.stompClient.connected) {
                this.stompClient.send(API_ENDPOINTS.READY(this.gameId), {}, JSON.stringify({ playerId: this.myPlayerId }));
                UI.disableMatchReadyBtn();
                UI.setMatchStatus(`${this.myUsername} 已准备，等待对手...`);
            }
        });
    }

    restore(client, gameState, user, onLobbyReturn) {
        this.stompClient = client;
        this.onGameOverCallback = onLobbyReturn;
        this.gameId = gameState.gameId;

        if (user.username === gameState.p1Username) {
            this.myPlayerId = PLAYER_1;
            this.myUsername = gameState.p1Username;
            this.opponentUsername = gameState.p2Username;
        } else if (user.username === gameState.p2Username) {
            this.myPlayerId = PLAYER_2;
            this.myUsername = gameState.p2Username;
            this.opponentUsername = gameState.p1Username;
        } else {
            console.error("Username mismatch during restore");
            return;
        }

        console.log(`Game Restored: ID=${this.gameId}, Role=${this.myPlayerId}`);

        UI.hideMatchFoundModal();
        this.stopMatchConfirmTimer();
        UI.switchToGameView();

        this.subscribeToGame();
        this.updateUI(gameState);
    }

    subscribeToGame() {
        if (this.subscription) this.subscription.unsubscribe();
        this.subscription = this.stompClient.subscribe('/topic/game/' + this.gameId, (msg) => {
            this.handleGameMessage(JSON.parse(msg.body));
        });
    }

    handleGameMessage(state) {
        if (state.phase === GamePhase.MATCH_CANCELLED) {
            this.stopMatchConfirmTimer();
            UI.hideMatchFoundModal();
            if (this.onGameOverCallback) this.onGameOverCallback(state.statusMessage || "Match Cancelled");
            return;
        }

        const isFirstUpdate = (this.gameState === null || this.gameState.phase === GamePhase.PRE_GAME);
        const isGameStarting = (state.phase === GamePhase.AMBUSH);

        if (isFirstUpdate && isGameStarting) {
            this.stopMatchConfirmTimer();
            UI.hideMatchFoundModal();
            UI.switchToGameView();
        }

        // Handle "Player Ready" messages in Pre-Game
        if (state.phase === GamePhase.PRE_GAME && UI.isMatchModalVisible()) {
            let msg = state.statusMessage;
            if (msg.includes("玩家 p1")) msg = msg.replace("玩家 p1", state.p1Username);
            if (msg.includes("玩家 p2")) msg = msg.replace("玩家 p2", state.p2Username);
            UI.setMatchStatus(msg);
            return;
        }

        this.updateUI(state);
    }

    updateUI(state) {
        this.gameState = state;
        if (this.localTimerInterval) clearInterval(this.localTimerInterval);

        UI.updateGameInfo(state, this.myPlayerId, this.myUsername);
        UI.renderBoard(state.board, this.myPlayerId);

        // Timers - Sync with server time
        let p1Time = state.p1TimeLeft;
        let p2Time = state.p2TimeLeft;

        // Use performance.now() for high-precision local tracking
        const lastUpdateTimestamp = performance.now();

        const updateTimerDisplay = () => {
            UI.updateTimers(p1Time, p2Time);
        };

        updateTimerDisplay();

        if (state.phase !== GamePhase.PRE_GAME && state.phase !== GamePhase.GAME_OVER && (p1Time > 0 || p2Time > 0)) {
            this.localTimerInterval = setInterval(() => {
                // Calculate elapsed time since last server update
                const now = performance.now();
                const elapsed = now - lastUpdateTimestamp;

                // Estimate current remaining time
                let currentP1Time = p1Time > 0 ? Math.max(0, p1Time - elapsed) : -1;
                let currentP2Time = p2Time > 0 ? Math.max(0, p2Time - elapsed) : -1;

                UI.updateTimers(currentP1Time, currentP2Time);

                if (currentP1Time <= 0 && currentP2Time <= 0) {
                    clearInterval(this.localTimerInterval);
                }
            }, 100); // Update frequently (100ms) for smooth display, but logic is based on elapsed time
        }

        if (state.phase === GamePhase.GAME_OVER) {
            this.handleGameOver(state);
        }
    }

    handleGameOver(state) {
        if (this.localTimerInterval) clearInterval(this.localTimerInterval);
        UI.showGameOverModal(state, () => {
            if (this.onGameOverCallback) this.onGameOverCallback("Welcome back.");
        });
    }

    handleBoardClick(r, c) {
        if (!this.gameState || !this.stompClient || !this.stompClient.connected) return;

        const phase = this.gameState.phase;
        let url = '';
        const body = { playerId: this.myPlayerId, r: r, c: c };

        if (phase === GamePhase.AMBUSH) {
            if (this.myPlayerId === PLAYER_1 && this.gameState.p1AmbushesPlaced >= 2) return;
            if (this.myPlayerId === PLAYER_2 && this.gameState.p2AmbushesPlaced >= 2) return;
            url = API_ENDPOINTS.AMBUSH(this.gameId);
        } else if (phase === GamePhase.PLACEMENT || phase === GamePhase.EXTRA_ROUNDS) {
            if (this.gameState.currentTurnPlayerId !== this.myPlayerId) return;
            url = API_ENDPOINTS.PLACE(this.gameId);
        } else {
            return;
        }

        this.stompClient.send(url, {}, JSON.stringify(body));
    }

    startMatchConfirmTimer() {
        let countdown = 30;
        UI.updateMatchConfirmTimer(countdown);
        if (this.matchConfirmTimerInterval) clearInterval(this.matchConfirmTimerInterval);

        this.matchConfirmTimerInterval = setInterval(() => {
            countdown--;
            UI.updateMatchConfirmTimer(countdown);
            if (countdown <= 0) {
                this.stopMatchConfirmTimer();
                UI.hideMatchFoundModal();
                if (this.onGameOverCallback) this.onGameOverCallback("Match timeout.");
            }
        }, 1000);
    }

    stopMatchConfirmTimer() {
        if (this.matchConfirmTimerInterval) clearInterval(this.matchConfirmTimerInterval);
    }
}
