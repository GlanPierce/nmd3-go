package com.example.ninjaattack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // 开启 WebSocket 消息代理
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 1. 设置"消息代理" (Broker), 即订阅前缀。
        // 客户端订阅 "/topic/..." 来接收消息
        registry.enableSimpleBroker("/topic");

        // 2. 设置"应用"前缀 (Application Destination Prefix)
        // 客户端发送消息到 "/app/..." 路径, 由 @MessageMapping 处理
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 3. 注册 STOMP "端点" (Endpoint), 这是客户端连接的入口。
        // "/ws" 是 WebSocket 的连接点。
        // withSockJS() 提供了B/S 浏览器兼容性回退。
        registry.addEndpoint("/ws").withSockJS();
    }
}