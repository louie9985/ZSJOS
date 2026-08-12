package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;

@Component
@Slf4j
public class NotifyBusinessEventListener {

    @Resource private NotifyBusinessEventProcessor processor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void onBusinessEvent(NotifyBusinessEvent event) {
        try {
            processor.process(event);
        } catch (Exception exception) {
            // Business notification is best-effort and must never expose the event payload in logs.
            log.warn("[onBusinessEvent][scene({}) eventKey({}) notification processing failed]",
                    event.getSceneCode(), event.getSourceEventKey(), exception);
        }
    }
}
