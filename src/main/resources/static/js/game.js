import { GameManager } from './game-manager.js';
import { UI } from './ui.js';

const gameManager = new GameManager();

export function initGame(client, matchResult, onLobbyReturn) {
    gameManager.init(client, matchResult, onLobbyReturn);
}

export function restoreGame(client, gameState, user, onLobbyReturn) {
    gameManager.restore(client, gameState, user, onLobbyReturn);
}

export function initGameControls() {
    UI.boardElement.addEventListener('click', (e) => {
        const square = e.target.closest('.square');
        if (!square) return;

        const { r, c } = square.dataset;
        gameManager.handleBoardClick(parseInt(r), parseInt(c));
    });

    UI.devModeToggle.addEventListener('change', () => {
        if (gameManager.gameState) {
            UI.renderBoard(gameManager.gameState.board, gameManager.myPlayerId);
        }
    });

    // Create empty board initially
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