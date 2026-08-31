package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.WS_MESSAGE_TYPE;

@Component
@Slf4j
public class LeadAssignmentRealtimeListener {

    @Resource private WebSocketMessageSender webSocketSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssignmentChanged(LeadAssignmentRealtimeEvent event) {
        try {
            webSocketSender.sendObject(UserTypeEnum.ADMIN.getValue(), event.userId(), WS_MESSAGE_TYPE,
                    Map.of("leadId", event.leadId(), "eventType", event.eventType()));
        } catch (Exception exception) {
            log.warn("[onAssignmentChanged][leadId({}) eventType({}) realtime push failed]",
                    event.leadId(), event.eventType(), exception);
        }
    }
}
