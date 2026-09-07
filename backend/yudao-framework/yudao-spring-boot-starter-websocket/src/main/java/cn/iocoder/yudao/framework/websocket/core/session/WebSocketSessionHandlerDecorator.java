package cn.iocoder.yudao.framework.websocket.core.session;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link WebSocketHandler} 的装饰类，实现了以下功能：
 *
 * 1. {@link WebSocketSession} 连接或关闭时，使用 {@link #sessionManager} 进行管理
 * 2. 封装 {@link WebSocketSession} 支持并发操作
 *
 * @author 芋道源码
 */
@Slf4j
public class WebSocketSessionHandlerDecorator extends WebSocketHandlerDecorator {

    /**
     * 发送时间的限制，单位：毫秒
     */
    private static final Integer SEND_TIME_LIMIT = 1000 * 5;
    /**
     * 发送消息缓冲上线，单位：bytes
     */
    private static final Integer BUFFER_SIZE_LIMIT = 1024 * 100;

    private final WebSocketSessionManager sessionManager;
    private final List<? extends WebSocketSessionLifecycleListener> lifecycleListeners;

    public WebSocketSessionHandlerDecorator(WebSocketHandler delegate,
                                            WebSocketSessionManager sessionManager,
                                            List<? extends WebSocketSessionLifecycleListener> lifecycleListeners) {
        super(delegate);
        this.sessionManager = sessionManager;
        this.lifecycleListeners = lifecycleListeners;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 实现 session 支持并发，可参考 https://blog.csdn.net/abu935009066/article/details/131218149
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT);
        // 添加到 WebSocketSessionManager 中
        sessionManager.addSession(concurrentSession);
        lifecycleListeners.forEach(listener -> {
            try {
                listener.onConnected(concurrentSession);
            } catch (Throwable ex) {
                log.warn("[afterConnectionEstablished][session({}) 生命周期监听器处理连接异常]",
                        concurrentSession.getId(), ex);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        sessionManager.removeSession(session);
        lifecycleListeners.forEach(listener -> {
            try {
                listener.onDisconnected(session);
            } catch (Throwable ex) {
                log.warn("[afterConnectionClosed][session({}) 生命周期监听器处理断开异常]", session.getId(), ex);
            }
        });
    }

}
