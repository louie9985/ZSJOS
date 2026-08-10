package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotifyMessageWebSocketListenerTest {

    @Mock
    private WebSocketSenderApi webSocketSenderApi;

    @InjectMocks
    private NotifyMessageWebSocketListener listener;

    @Test
    void shouldPushMessageIdToTargetUser() {
        NotifyMessageCreatedEvent event = new NotifyMessageCreatedEvent(10L, 20L, 2);

        listener.onMessageCreated(event);

        verify(webSocketSenderApi).sendObject(2, 20L, NotifyMessageWebSocketListener.MESSAGE_TYPE,
                java.util.Map.of("messageId", 10L));
    }

    @Test
    void shouldNotFailWhenRealtimePushFails() {
        NotifyMessageCreatedEvent event = new NotifyMessageCreatedEvent(10L, 20L, 2);
        doThrow(new IllegalStateException("offline")).when(webSocketSenderApi)
                .sendObject(2, 20L, NotifyMessageWebSocketListener.MESSAGE_TYPE,
                        java.util.Map.of("messageId", 10L));

        listener.onMessageCreated(event);
    }
}
