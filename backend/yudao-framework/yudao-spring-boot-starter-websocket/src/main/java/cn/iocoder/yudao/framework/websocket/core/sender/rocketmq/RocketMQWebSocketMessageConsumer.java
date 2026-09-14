package cn.iocoder.yudao.framework.websocket.core.sender.rocketmq;

import cn.iocoder.yudao.framework.audit.ExecutionAuditContext;
import cn.iocoder.yudao.framework.audit.ExecutionAuditHook;
import cn.iocoder.yudao.framework.audit.ExecutionAuditRunner;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import java.util.List;

/**
 * {@link RocketMQWebSocketMessage} 广播消息的消费者，真正把消息发送出去
 *
 * @author 芋道源码
 */
@RocketMQMessageListener( // 重点：添加 @RocketMQMessageListener 注解，声明消费的 topic
        topic = "${yudao.websocket.sender-rocketmq.topic}",
        consumerGroup = "${yudao.websocket.sender-rocketmq.consumer-group}",
        messageModel = MessageModel.BROADCASTING // 设置为广播模式，保证每个实例都能收到消息
)
public class RocketMQWebSocketMessageConsumer implements RocketMQListener<RocketMQWebSocketMessage> {

    private final RocketMQWebSocketMessageSender rocketMQWebSocketMessageSender;
    private final List<ExecutionAuditHook> executionAuditHooks;
    public RocketMQWebSocketMessageConsumer(RocketMQWebSocketMessageSender sender) {
        this(sender, java.util.Collections.emptyList());
    }
    public RocketMQWebSocketMessageConsumer(RocketMQWebSocketMessageSender sender, List<ExecutionAuditHook> hooks) {
        this.rocketMQWebSocketMessageSender = sender;
        this.executionAuditHooks = hooks;
    }

    @Override
    public void onMessage(RocketMQWebSocketMessage message) {
        try {
            ExecutionAuditRunner.run(new ExecutionAuditContext("SYSTEM_ROCKETMQ", "websocket", null, null,
                    null, null, null, java.util.Map.of("messageType", message.getMessageType())),
                    executionAuditHooks, () -> { rocketMQWebSocketMessageSender.send(message.getSessionId(),
                            message.getUserType(), message.getUserId(), message.getMessageType(), message.getMessageContent()); return null; });
        } catch (Exception e) { throw new IllegalStateException("RocketMQ message consumption failed", e); }
    }

}
