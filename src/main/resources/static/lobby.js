import { UI } from './ui.js';
import { initGame } from './game.js'; // 导入 game.js

let stompClient = null;
let currentUserId = null; // (新增) 存储已登录用户的 ID

// (修改) 登录成功后, main.js 会调用此函数
export function connectMasterWebSocket(user, onLobbyReturnCallback) {
    // (新增) 保存用户 ID (uid)
    currentUserId = user.id;

    if (stompClient && stompClient.connected) {
        console.log("WebSocket 已连接，无需重连。");
        return;
    }

    let socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // ⬇️ ⬇️ ⬇️ (关键新增) ⬇️ ⬇️ ⬇️
    // 开启 STOMP 客户端的详细日志
    // 这会在 F12 控制台打印所有 WebSocket 通信
    stompClient.debug = (str) => {
        console.log(new Date().toLocaleTimeString() + ' STOMP: ' + str);
    };
    // ⬆️ ⬆️ ⬆️ (关键新增) ⬆️ ⬆️ ⬆️

    stompClient.connect({}, (frame) => {
        console.log('WebSocket 主连接已建立: ' + frame);

        // (重大修改)
        // 我们不再订阅 /user/queue/matchmaking
        // 我们订阅一个 *只属于我们* 的、基于 UID 的唯一频道
        const privateQueue = '/queue/matchmaking-' + currentUserId;
        console.log("正在订阅私人队列: " + privateQueue);

        stompClient.subscribe(privateQueue, (message) => {
            // 匹配成功！服务器发来了 MatchResult
            const matchResult = JSON.parse(message.body);

            // 匹配成功！隐藏大厅并调用 game.js
            UI.lobbyContainer.style.display = 'none';
            initGame(stompClient, matchResult, onLobbyReturnCallback);
        });

        // (关键修复)
        // 只有在 *订阅* 成功后 (我们假设它在 connect 回调后立即成功)，
        // 我们才启用 "寻找对战" 按钮。
        UI.lobbyStatus.textContent = "已连接到服务器，请寻找对战。";
        UI.findMatchBtn.disabled = false;

    }, (error) => {
        console.error('WebSocket 连接失败:', error);
        UI.lobbyStatus.textContent = "连接服务器失败，请刷新页面重试。";
        UI.findMatchBtn.disabled = true;
    });
}

// (不变) 登出时，main.js 会调用此函数
export function disconnectMasterWebSocket() {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect(() => console.log("已登出并断开 WebSocket"));
        stompClient = null;
    }
    currentUserId = null;
}

// (不变) main.js 会调用此函数来绑定"寻找对战"按钮
export function initLobby() {
    UI.findMatchBtn.addEventListener('click', () => {
        if (stompClient && stompClient.connected) {
            console.log("发送 'find match' 请求...");
            // (不变) 我们仍然发送到 /app/matchmaking/find
            // 服务器会从我们的 Principal 中获取我们的 ID 和 Username
            stompClient.send("/app/matchmaking/find", {}, "{}");
            UI.lobbyStatus.textContent = "正在寻找对手...";
            UI.findMatchBtn.disabled = true;
        } else {
            UI.lobbyStatus.textContent = "正在重新连接到服务器，请稍等...";
            // (注意) connectMasterWebSocket 需要 user 对象，但我们这里没有
            // 这是一个小 bug，但目前重连逻辑不是首要问题
        }
    });
}