package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 在站内信事务提交后通知在线用户，离线消息仍以数据库记录为准。
 */
@Component
@Slf4j
public class NotifyMessageWebSocketListener {

    public static final String MESSAGE_TYPE = "notify-message-new";

    @Resource
    private WebSocketSenderApi webSocketSenderApi;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageCreated(NotifyMessageCreatedEvent event) {
        try {
            webSocketSenderApi.sendObject(event.userType(), event.userId(), MESSAGE_TYPE,
                    Map.of("messageId", event.messageId()));
        } catch (Exception exception) {
            // WebSocket 是实时提示通道，推送失败不能回滚已经持久化的站内信。
            log.warn("[onMessageCreated][messageId({}) user({}/{}) WebSocket 推送失败]",
                    event.messageId(), event.userId(), event.userType(), exception);
        }
    }
}
