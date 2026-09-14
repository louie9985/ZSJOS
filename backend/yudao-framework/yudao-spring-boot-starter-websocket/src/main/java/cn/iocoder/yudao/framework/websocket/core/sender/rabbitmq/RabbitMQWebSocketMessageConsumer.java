package cn.iocoder.yudao.framework.websocket.core.sender.rabbitmq;

import cn.iocoder.yudao.framework.audit.*;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import java.util.List;

/**
 * {@link RabbitMQWebSocketMessage} 广播消息的消费者，真正把消息发送出去
 *
 * @author 芋道源码
 */
@RabbitListener(
        bindings = @QueueBinding(
                value = @Queue(
                        // 在 Queue 的名字上，使用 UUID 生成其后缀。这样，启动的 Consumer 的 Queue 不同，以达到广播消费的目的
                        name = "${yudao.websocket.sender-rabbitmq.queue}" + "-" + "#{T(java.util.UUID).randomUUID()}",
                        // Consumer 关闭时，该队列就可以被自动删除了
                        autoDelete = "true"
                ),
                exchange = @Exchange(
                        name = "${yudao.websocket.sender-rabbitmq.exchange}",
                        type = ExchangeTypes.TOPIC,
                        declare = "false"
                )
        )
)
public class RabbitMQWebSocketMessageConsumer {

    private final RabbitMQWebSocketMessageSender rabbitMQWebSocketMessageSender;
    private final List<ExecutionAuditHook> executionAuditHooks;
    public RabbitMQWebSocketMessageConsumer(RabbitMQWebSocketMessageSender sender) { this(sender, java.util.Collections.emptyList()); }
    public RabbitMQWebSocketMessageConsumer(RabbitMQWebSocketMessageSender sender, List<ExecutionAuditHook> hooks) { this.rabbitMQWebSocketMessageSender = sender; this.executionAuditHooks = hooks; }

    @RabbitHandler
    public void onMessage(RabbitMQWebSocketMessage message) {
        try {
            ExecutionAuditRunner.run(new ExecutionAuditContext("SYSTEM_RABBITMQ", "websocket", null, null, null, null, null,
                    java.util.Map.of("messageType", message.getMessageType())), executionAuditHooks, () -> {
                rabbitMQWebSocketMessageSender.send(message.getSessionId(), message.getUserType(), message.getUserId(), message.getMessageType(), message.getMessageContent()); return null;
            });
        } catch (Exception e) { throw new IllegalStateException("RabbitMQ message consumption failed", e); }
    }

}
