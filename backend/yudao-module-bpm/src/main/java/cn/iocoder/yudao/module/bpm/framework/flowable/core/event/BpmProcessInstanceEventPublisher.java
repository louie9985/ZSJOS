package cn.iocoder.yudao.module.bpm.framework.flowable.core.event;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.ObjectProvider;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceAuditHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

/**
 * {@link BpmProcessInstanceStatusEvent} 的生产者
 *
 * @author 芋道源码
 */
@Validated
public class BpmProcessInstanceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BpmProcessInstanceEventPublisher.class);

    private final ApplicationEventPublisher publisher;
    private final ObjectProvider<BpmProcessInstanceAuditHook> auditHooks;

    public BpmProcessInstanceEventPublisher(ApplicationEventPublisher publisher,
                                             ObjectProvider<BpmProcessInstanceAuditHook> auditHooks) {
        this.publisher = publisher;
        this.auditHooks = auditHooks;
    }

    public void sendProcessInstanceResultEvent(@Valid BpmProcessInstanceStatusEvent event) {
        publisher.publishEvent(event);
        auditHooks.orderedStream().forEach(hook -> {
            try {
                hook.onProcessInstanceEvent(event);
            } catch (Exception ex) {
                log.warn("BPM process audit hook failed, eventKey={}", event.getEventKey(), ex);
            }
        });
    }

}
