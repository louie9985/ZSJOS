package cn.iocoder.yudao.module.bpm.api.event;

/**
 * Optional integration point for recording process events in a host audit system.
 * BPM does not depend on any concrete audit module; implementations must not
 * throw exceptions back into the workflow transaction.
 */
@FunctionalInterface
public interface BpmProcessInstanceAuditHook {

    void onProcessInstanceEvent(BpmProcessInstanceStatusEvent event);
}
