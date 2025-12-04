const ReplaySystem = {
    currentGame: null,
    currentMoveIndex: -1,
    playbackInterval: null,
    boardSize: 6, // Assuming 6x6 board based on game logic

    initList: function () {
        console.log("Initializing Replay List...");
        this.loadReplayList();

        document.getElementById('tab-recent').addEventListener('click', () => {
            this.switchTab('recent');
            this.loadReplayList();
        });
        document.getElementById('tab-collection').addEventListener('click', () => {
            this.switchTab('collection');
            this.loadCollection();
        });
    },

    switchTab: function (tab) {
        document.querySelectorAll('.tabs button').forEach(b => b.classList.remove('active'));
        document.getElementById('tab-' + tab).classList.add('active');
    },

    loadReplayList: function () {
        const container = document.getElementById('replay-list-container');
        container.innerHTML = '<p>加载中...</p>';

        fetch('/api/replays')
            .then(res => res.json())
            .then(games => {
                if (games.length === 0) {
                    container.innerHTML = '<p>暂无最近对局。</p>';
                    return;
                }
                this.renderList(games, container);
            })
            .catch(err => {
                console.error(err);
                container.innerHTML = '<p>加载失败。</p>';
            });
    },

    loadCollection: function () {
        const container = document.getElementById('replay-list-container');
        container.innerHTML = '<p>加载中...</p>';

        // Assuming we store username in localStorage or similar from main app
        // For now, prompt or use a placeholder if not integrated
        const username = localStorage.getItem('username');
        if (!username) {
            container.innerHTML = '<p>请先在主页登录。</p>';
            return;
        }

        fetch('/api/replays/collection?username=' + username)
            .then(res => res.json())
            .then(items => {
                if (items.length === 0) {
                    container.innerHTML = '<p>暂无收藏。</p>';
                    return;
                }
                // We need to fetch game details for each collection item or adjust API
                // For simplicity, let's just list them with links
                let html = '<ul class="replay-list">';
                items.forEach(item => {
                    html += `
                        <li class="replay-item">
                            <div class="replay-info">
                                <h4>游戏 ID: ${item.gameId}</h4>
                                <p>备注: ${item.note || '无'}</p>
                                <p>收藏时间: ${new Date(item.savedAt).toLocaleString()}</p>
                            </div>
                            <div class="replay-actions">
                                <button onclick="window.location.href='replay_viewer.html?id=${item.gameId}'">观看</button>
                            </div>
                        </li>
                    `;
                });
                html += '</ul>';
                container.innerHTML = html;
            });
    },

    renderList: function (games, container) {
        let html = '<ul class="replay-list">';
        games.forEach(game => {
            const p1 = game.p1 ? game.p1.username : 'Unknown';
            const p2 = game.p2 ? game.p2.username : 'Unknown';
            const result = game.result ? (game.result.winnerId === 'DRAW' ? '平局' : (game.result.winnerId === 'p1' ? p1 + ' 胜' : p2 + ' 胜')) : '进行中/未知';

            html += `
                <li class="replay-item">
                    <div class="replay-info">
                        <h4>${p1} vs ${p2}</h4>
                        <p>结果: ${result}</p>
                        <p>ID: ${game.gameId}</p>
                    </div>
                    <div class="replay-actions">
                        <button onclick="window.location.href='replay_viewer.html?id=${game.gameId}'">观看</button>
                    </div>
                </li>
            `;
        });
        html += '</ul>';
        container.innerHTML = html;
    },

    // --- Viewer Logic ---

    loadReplay: function (gameId) {
        console.log("Loading replay for game: " + gameId);
        fetch('/api/replays/' + gameId)
            .then(response => response.json())
            .then(game => {
                this.currentGame = game;
                this.initViewer(game);
            })
            .catch(err => alert("加载录像失败: " + err));
    },

    initViewer: function (game) {
        // Update Info
        document.getElementById('game-id').textContent = game.gameId;
        document.getElementById('p1-name').textContent = game.p1.username;
        document.getElementById('p2-name').textContent = game.p2.username;

        // Calculate scores (piece counts)
        // This is a simplification; real score might need complex calculation or be stored in result
        const p1Score = game.result ? game.result.p1PieceCount : 0;
        const p2Score = game.result ? game.result.p2PieceCount : 0;
        document.getElementById('p1-score').textContent = p1Score;
        document.getElementById('p2-score').textContent = p2Score;

        const resultText = game.result ? (game.result.winnerId === 'DRAW' ? '平局' : (game.result.winnerId === 'p1' ? 'P1 获胜' : 'P2 获胜')) : '未完成';
        document.getElementById('status-message').textContent = `游戏结束 - ${resultText}`;

        this.renderBoard([]); // Init empty board
        this.currentMoveIndex = -1;
        this.updateMoveInfo();

        // Bind controls
        document.getElementById('btn-first').onclick = () => this.goToMove(-1);
        document.getElementById('btn-prev').onclick = () => this.goToMove(this.currentMoveIndex - 1);
        document.getElementById('btn-next').onclick = () => this.goToMove(this.currentMoveIndex + 1);
        document.getElementById('btn-last').onclick = () => this.goToMove(this.currentGame.history.length - 1);
        document.getElementById('btn-play').onclick = () => this.togglePlayback();

        document.getElementById('btn-collect').onclick = () => {
            const username = localStorage.getItem('username');
            if (!username) {
                alert('请先登录 (无法获取用户名)');
                return;
            }
            // Directly collect without prompting for note
            this.addToCollection(game.gameId, username, '');
        };
    },

    renderBoard: function (movesToRender) {
        const boardDiv = document.getElementById('board');
        boardDiv.innerHTML = '';
        // Styles are now handled by CSS (.board-grid), but we ensure grid layout here just in case
        boardDiv.style.display = 'grid';
        boardDiv.style.gridTemplateColumns = `repeat(${this.boardSize}, 1fr)`;
        boardDiv.style.gridTemplateRows = `repeat(${this.boardSize}, 1fr)`;

        // Create empty grid
        const grid = Array(this.boardSize).fill(null).map(() => Array(this.boardSize).fill(null));

        // Apply moves
        movesToRender.forEach(move => {
            if (move.type === 'PIECE') {
                grid[move.r][move.c] = { type: 'piece', owner: move.playerId };
            } else if (move.type === 'AMBUSH') {
                grid[move.r][move.c] = { type: 'ambush', owner: move.playerId };
            }
        });

        // Render grid
        for (let r = 0; r < this.boardSize; r++) {
            for (let c = 0; c < this.boardSize; c++) {
                const squareEl = document.createElement('div');
                squareEl.classList.add('square');
                squareEl.dataset.r = r;
                squareEl.dataset.c = c;

                const item = grid[r][c];
                if (item) {
                    if (item.type === 'piece') {
                        const pieceEl = document.createElement('div');
                        pieceEl.classList.add('piece', item.owner);
                        squareEl.appendChild(pieceEl);
                    } else if (item.type === 'ambush') {
                        // Replay shows all ambushes
                        squareEl.classList.add(item.owner + '-ambush');
                        const ambushEl = document.createElement('div');
                        ambushEl.classList.add('ambush-indicator', item.owner);
                        squareEl.appendChild(ambushEl);
                    }
                }
                boardDiv.appendChild(squareEl);
            }
        }
    },

    goToMove: function (index) {
        if (!this.currentGame) return;

        // Clamp index
        if (index < -1) index = -1;
        if (index >= this.currentGame.history.length) index = this.currentGame.history.length - 1;

        this.currentMoveIndex = index;

        // Slice history up to current index
        const moves = this.currentGame.history.slice(0, index + 1);
        this.renderBoard(moves);
        this.updateMoveInfo();
    },

    updateMoveInfo: function () {
        const total = this.currentGame.history.length;
        const current = this.currentMoveIndex + 1;
        document.getElementById('move-info').textContent = `步数: ${current} / ${total}`;
    },

    togglePlayback: function () {
        if (this.playbackInterval) {
            clearInterval(this.playbackInterval);
            this.playbackInterval = null;
            document.getElementById('btn-play').textContent = '播放';
        } else {
            document.getElementById('btn-play').textContent = '暂停';
            this.playbackInterval = setInterval(() => {
                if (this.currentMoveIndex < this.currentGame.history.length - 1) {
                    this.goToMove(this.currentMoveIndex + 1);
                } else {
                    this.togglePlayback(); // Stop at end
                }
            }, 1000);
        }
    },

    addToCollection: function (gameId, username, note) {
        fetch('/api/replays/collection', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ gameId, username, note })
        }).then(res => {
            if (res.ok) alert("已收藏!");
            else res.text().then(t => alert(t));
        });
    }
};
