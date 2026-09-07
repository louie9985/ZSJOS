package cn.iocoder.yudao.framework.websocket.core.session;

import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket Session 生命周期监听器。
 *
 * 监听器只用于观察连接状态，不应阻断连接和消息处理。
 */
public interface WebSocketSessionLifecycleListener {

    default void onConnected(WebSocketSession session) {
    }

    default void onHeartbeat(WebSocketSession session) {
    }

    default void onDisconnected(WebSocketSession session) {
    }

}
