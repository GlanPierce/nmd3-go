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
            fetchHistory();
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
}

function fetchHistory() {
    const historySection = document.getElementById('profile-history-section');
    historySection.innerHTML = '<h3>历史记录</h3><p>加载中...</p>';

    fetch('/api/leaderboard/history')
        .then(res => res.json())
        .then(data => {
            if (data.length === 0) {
                historySection.innerHTML = '<h3>历史记录</h3><p>暂无记录</p>';
                return;
            }

            let html = '<h3>历史记录</h3><ul style="list-style: none; padding: 0;">';
            data.forEach(game => {
                const color = game.result === '胜利' ? 'green' : (game.result === '失败' ? 'red' : 'gray');
                const date = game.timestamp ? new Date(game.timestamp).toLocaleString() : "未知时间";
                html += `
                    <li style="border-bottom: 1px solid #eee; padding: 8px 0;">
                        <div style="display: flex; justify-content: space-between;">
                            <span>vs <strong>${game.opponentName}</strong></span>
                            <div>
                                <span style="color: ${color}; font-weight: bold;">${game.result}</span>
                                <span style="font-size: 0.9em; margin-left: 5px; color: ${game.scoreChange > 0 ? 'green' : (game.scoreChange < 0 ? 'red' : 'gray')}">
                                    (${game.scoreChange > 0 ? '+' : ''}${game.scoreChange})
                                </span>
                            </div>
                        </div>
                        <div style="font-size: 0.85em; color: #666;">
                            ${date}
                        </div>
                    </li>
                `;
            });
            html += '</ul>';
            historySection.innerHTML = html;
        })
        .catch(err => {
            console.error(err);
            historySection.innerHTML = '<h3>历史记录</h3><p style="color: red;">加载失败</p>';
        });
}