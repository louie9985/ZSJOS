package cn.iocoder.yudao.module.infra.service.websocket;

import cn.iocoder.yudao.framework.websocket.core.handler.JsonWebSocketMessageHandler;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionLifecycleListener;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionManager;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionHandlerDecorator;
import cn.iocoder.yudao.module.infra.dal.redis.websocket.WebSocketPresenceRedisDAO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebSocketLifecycleAndPresenceTest {

    @Test
    void lifecycleListenerFailureDoesNotInterruptWebSocket() throws Exception {
        WebSocketSessionManager sessionManager = mock(WebSocketSessionManager.class);
        WebSocketHandler delegate = mock(WebSocketHandler.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        WebSocketSessionLifecycleListener failingListener = new WebSocketSessionLifecycleListener() {
            @Override
            public void onConnected(WebSocketSession ignored) {
                throw new IllegalStateException("redis unavailable");
            }

            @Override
            public void onHeartbeat(WebSocketSession ignored) {
                throw new IllegalStateException("redis unavailable");
            }

            @Override
            public void onDisconnected(WebSocketSession ignored) {
                throw new IllegalStateException("redis unavailable");
            }
        };
        WebSocketSessionLifecycleListener observer = mock(WebSocketSessionLifecycleListener.class);
        List<WebSocketSessionLifecycleListener> listeners = List.of(failingListener, observer);
        JsonWebSocketMessageHandler messageHandler = new JsonWebSocketMessageHandler(Collections.emptyList(), listeners);
        WebSocketSessionHandlerDecorator handler = new WebSocketSessionHandlerDecorator(
                messageHandler, sessionManager, listeners);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("ping"));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(observer).onConnected(any(WebSocketSession.class));
        verify(observer).onHeartbeat(session);
        verify(observer).onDisconnected(session);
        verify(session).sendMessage(argThat(message -> "pong".equals(((TextMessage) message).getPayload())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisPresenceKeepsMultipleSessionsAndRemovesExpiredMembers() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(
                new LinkedHashSet<>(List.of("10:session-a", "10:session-b", "20:session-c", "invalid")));
        WebSocketPresenceRedisDAO dao = new WebSocketPresenceRedisDAO(redisTemplate);

        dao.touch(1L, 2, 10L, "session-a", 100_000L);
        dao.remove(1L, 2, 10L, "session-a");
        var onlineUserIds = dao.getOnlineUserIds(1L, 2, 200_000L);

        verify(zSetOperations).add("infra:websocket:presence:1:2", "10:session-a", 100_000D);
        verify(redisTemplate).expire("infra:websocket:presence:1:2", Duration.ofDays(1));
        verify(zSetOperations).remove("infra:websocket:presence:1:2", "10:session-a");
        verify(zSetOperations).removeRangeByScore(
                eq("infra:websocket:presence:1:2"), eq(Double.NEGATIVE_INFINITY), eq(110_000D));
        assertEquals(new LinkedHashSet<>(List.of(10L, 20L)), onlineUserIds);
        assertTrue(onlineUserIds.contains(10L));
    }

}
