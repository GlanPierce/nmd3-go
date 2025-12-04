import { UI } from './ui.js';
import { initGame } from './game.js';

let stompClient = null;
let currentUserId = null;

export function connectMasterWebSocket(user, onLobbyReturnCallback) {
    return new Promise((resolve, reject) => {
        currentUserId = user.id;

        if (stompClient && stompClient.connected) {
            console.log("WebSocket 已连接，无需重连。");
            resolve(stompClient);
            return;
        }

        let socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);

        stompClient.debug = (str) => {
            console.log(new Date().toLocaleTimeString() + ' STOMP: ' + str);
        };

        stompClient.connect({}, (frame) => {
            console.log('WebSocket 主连接已建立: ' + frame);

            const privateQueue = '/queue/matchmaking-' + currentUserId;
            console.log("正在订阅私人队列: " + privateQueue);

            stompClient.subscribe(privateQueue, (message) => {
                const matchResult = JSON.parse(message.body);
                UI.lobbyContainer.style.display = 'none';
                initGame(stompClient, matchResult, onLobbyReturnCallback);
            });

            UI.lobbyStatus.textContent = "已连接到服务器，请寻找对战。";
            UI.findMatchBtn.disabled = false;

            resolve(stompClient);

        }, (error) => {
            console.error('WebSocket 连接失败:', error);
            UI.lobbyStatus.textContent = "连接服务器失败，请刷新页面重试。";
            UI.findMatchBtn.disabled = true;
            reject(error);
        });
    });
}

export function disconnectMasterWebSocket() {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect(() => console.log("已登出并断开 WebSocket"));
        stompClient = null;
    }
    currentUserId = null;
}

export function initLobby() {
    UI.findMatchBtn.addEventListener('click', () => {
        if (stompClient && stompClient.connected) {
            console.log("发送 'find match' 请求...");
            stompClient.send("/app/matchmaking/find", {}, "{}");
            UI.lobbyStatus.textContent = "正在寻找对手...";
            UI.findMatchBtn.disabled = true;
        } else {
            UI.lobbyStatus.textContent = "正在重新连接到服务器，请稍等...";
        }
    });

    if (UI.profileBtn) {
        UI.profileBtn.addEventListener('click', () => {
            const username = UI.userInfoUsername.textContent;
            const score = UI.userInfoScore.textContent;
            const currentAvatarSrc = UI.userAvatarSmall.src;
            const currentAvatar = currentAvatarSrc.substring(currentAvatarSrc.lastIndexOf('/') + 1);

            UI.profileUsername.textContent = username;
            UI.profileScore.textContent = score;
            UI.profileCurrentAvatar.src = `assets/avatars/${currentAvatar}`;

            UI.avatarOptions.forEach(img => {
                img.style.border = '2px solid transparent';
                if (img.dataset.avatar === currentAvatar) {
                    img.style.border = '2px solid #007bff';
                }
            });

            UI.profileModal.style.display = 'flex';
            switchProfileTab('profile'); // Default to profile tab
        });
    }

    if (UI.closeProfileBtn) {
        UI.closeProfileBtn.addEventListener('click', () => {
            UI.profileModal.style.display = 'none';
        });
    }

    let selectedAvatar = null;
    if (UI.avatarOptions) {
        UI.avatarOptions.forEach(img => {
            img.addEventListener('click', () => {
                selectedAvatar = img.dataset.avatar;
                UI.avatarOptions.forEach(i => i.style.border = '2px solid transparent');
                img.style.border = '2px solid #007bff';
            });
        });
    }

    if (UI.saveAvatarBtn) {
        UI.saveAvatarBtn.addEventListener('click', () => {
            if (!selectedAvatar) {
                alert("请选择一个头像");
                return;
            }

            const headers = { 'Content-Type': 'application/json' };

            fetch('/api/leaderboard/avatar', {
                method: 'PUT',
                headers: headers,
                body: JSON.stringify({ avatar: selectedAvatar })
            })
                .then(response => {
                    if (response.ok) {
                        alert('头像更新成功！');
                        UI.userAvatarSmall.src = `assets/avatars/${selectedAvatar}`;
                        UI.profileCurrentAvatar.src = `assets/avatars/${selectedAvatar}`;
                        UI.profileModal.style.display = 'none';
                    } else {
                        response.text().then(text => alert('更新失败: ' + text));
                    }
                })
                .catch(err => {
                    console.error(err);
                    alert('更新失败，请重试');
                });
        });
    }

    // Sidebar Event Listeners
    if (UI.tabProfileBtn) UI.tabProfileBtn.addEventListener('click', () => switchProfileTab('profile'));
    if (UI.tabHistoryBtn) UI.tabHistoryBtn.addEventListener('click', () => {
        switchProfileTab('history');
        fetchHistory();
    });
    if (UI.tabCollectionBtn) UI.tabCollectionBtn.addEventListener('click', () => {
        switchProfileTab('collection');
        fetchCollection();
    });
}

function switchProfileTab(tab) {
    // Reset buttons
    [UI.tabProfileBtn, UI.tabHistoryBtn, UI.tabCollectionBtn].forEach(btn => {
        if (btn) {
            btn.classList.remove('active');
            btn.style.background = 'transparent';
        }
    });
    // Reset sections
    [UI.sectionProfile, UI.sectionHistory, UI.sectionCollection].forEach(sec => {
        if (sec) sec.style.display = 'none';
    });

    // Activate selected
    if (tab === 'profile') {
        if (UI.tabProfileBtn) {
            UI.tabProfileBtn.classList.add('active');
            UI.tabProfileBtn.style.background = '#e9ecef';
        }
        if (UI.sectionProfile) UI.sectionProfile.style.display = 'block';
    } else if (tab === 'history') {
        if (UI.tabHistoryBtn) {
            UI.tabHistoryBtn.classList.add('active');
            UI.tabHistoryBtn.style.background = '#e9ecef';
        }
        if (UI.sectionHistory) UI.sectionHistory.style.display = 'block';
    } else if (tab === 'collection') {
        if (UI.tabCollectionBtn) {
            UI.tabCollectionBtn.classList.add('active');
            UI.tabCollectionBtn.style.background = '#e9ecef';
        }
        if (UI.sectionCollection) UI.sectionCollection.style.display = 'block';
    }
}

function fetchHistory() {
    const container = UI.historyListContainer;
    container.innerHTML = '<p>加载中...</p>';

    fetch('/api/leaderboard/history')
        .then(res => res.json())
        .then(data => {
            if (data.length === 0) {
                container.innerHTML = '<p>暂无记录</p>';
                return;
            }

            let html = '<ul style="list-style: none; padding: 0;">';
            data.forEach(game => {
                const isWin = game.result === '胜利';
                const isLoss = game.result === '失败';
                const resultColor = isWin ? 'green' : (isLoss ? 'red' : 'gray');
                // Fix: score change color should match result, not just the sign
                const scoreChangeColor = isWin ? 'green' : (isLoss ? 'red' : 'gray');
                const date = game.timestamp ? new Date(game.timestamp).toLocaleString() : "未知时间";

                html += `
                    <li style="border-bottom: 1px solid #eee; padding: 10px 0;">
                        <div style="display: grid; grid-template-columns: 1fr auto; gap: 10px; align-items: center;">
                            <div>
                                <div style="font-weight: bold;">vs ${game.opponentName}</div>
                                <div style="font-size: 0.9em;">
                                    <span style="color: ${resultColor};">${game.result}</span>
                                    <span style="color: ${scoreChangeColor}; margin-left: 5px;">(${game.scoreChange > 0 ? '+' : ''}${game.scoreChange})</span>
                                </div>
                                <div style="font-size: 0.8em; color: #666;">${date}</div>
                            </div>
                            <div>
                                <button onclick="window.location.href='replay_viewer.html?id=${game.gameId}'" 
                                    style="padding: 5px 10px; cursor: pointer; background: #007bff; color: white; border: none; border-radius: 4px; white-space: nowrap;">回放</button>
                            </div>
                        </div>
                    </li>
                `;
            });
            html += '</ul>';
            container.innerHTML = html;
        })
        .catch(err => {
            console.error(err);
            container.innerHTML = '<p style="color: red;">加载失败</p>';
        });
}

function fetchCollection() {
    const container = UI.collectionListContainer;
    container.innerHTML = '<p>加载中...</p>';
    const username = UI.userInfoUsername.textContent;

    fetch('/api/replays/collection?username=' + username)
        .then(res => res.json())
        .then(items => {
            if (items.length === 0) {
                container.innerHTML = '<p>暂无收藏</p>';
                return;
            }

            // Need to fetch game details for each collection to show opponent and result
            const gamePromises = items.map(item =>
                fetch('/api/replays/' + item.gameId).then(r => r.json())
            );

            Promise.all(gamePromises).then(games => {
                let html = '<ul style="list-style: none; padding: 0;">';
                games.forEach((game, index) => {
                    const item = items[index];
                    const currentUser = username;
                    const isP1 = game.p1.username === currentUser;
                    const opponent = isP1 ? game.p2.username : game.p1.username;

                    let result = '未知';
                    if (game.result) {
                        if (game.result.winnerId === 'DRAW') {
                            result = '平局';
                        } else {
                            const won = (isP1 && game.result.winnerId === 'p1') || (!isP1 && game.result.winnerId === 'p2');
                            result = won ? '胜利' : '失败';
                        }
                    }

                    const resultColor = result === '胜利' ? 'green' : (result === '失败' ? 'red' : 'gray');

                    html += `
                        <li style="border-bottom: 1px solid #eee; padding: 10px 0;">
                            <div style="display: grid; grid-template-columns: 1fr auto; gap: 10px; align-items: center;">
                                <div>
                                    <div style="font-weight: bold;">vs ${opponent}</div>
                                    <div style="font-size: 0.9em;">
                                        <span style="color: ${resultColor};">${result}</span>
                                    </div>
                                    <div style="font-size: 0.8em; color: #666;">${new Date(item.savedAt).toLocaleString()}</div>
                                </div>
                                <div>
                                    <button onclick="window.location.href='replay_viewer.html?id=${item.gameId}'" 
                                        style="padding: 5px 10px; cursor: pointer; background: #007bff; color: white; border: none; border-radius: 4px; white-space: nowrap;">回放</button>
                                </div>
                            </div>
                        </li>
                    `;
                });
                html += '</ul>';
                container.innerHTML = html;
            }).catch(err => {
                console.error(err);
                container.innerHTML = '<p style="color: red;">加载游戏详情失败</p>';
            });
        })
        .catch(err => {
            console.error(err);
            container.innerHTML = '<p style="color: red;">加载失败</p>';
        });
}