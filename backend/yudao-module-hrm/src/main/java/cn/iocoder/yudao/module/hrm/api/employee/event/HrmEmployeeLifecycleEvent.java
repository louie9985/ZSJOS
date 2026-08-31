package cn.iocoder.yudao.module.hrm.api.employee.event;

import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Synchronous employee lifecycle event. Listeners participate in the publishing transaction.
 */
@Getter
public class HrmEmployeeLifecycleEvent extends ApplicationEvent {

    private final Long tenantId;
    private final String eventKey;
    private final HrmEmployeeLifecycleEventType type;
    private final Long sourceId;
    private final LocalDateTime occurredAt;
    private final HrmEmployeeRespDTO before;
    private final HrmEmployeeRespDTO after;

    public HrmEmployeeLifecycleEvent(Object source, Long tenantId, String eventKey,
                                     HrmEmployeeLifecycleEventType type, Long sourceId,
                                     HrmEmployeeRespDTO before, HrmEmployeeRespDTO after) {
        super(source);
        this.tenantId = tenantId;
        this.eventKey = eventKey;
        this.type = type;
        this.sourceId = sourceId;
        this.occurredAt = LocalDateTime.now();
        this.before = before;
        this.after = after;
    }

    public Long getEmployeeId() {
        return after != null ? after.getId() : before != null ? before.getId() : null;
    }

    public Long getUserId() {
        return after != null ? after.getUserId() : before != null ? before.getUserId() : null;
    }

}
