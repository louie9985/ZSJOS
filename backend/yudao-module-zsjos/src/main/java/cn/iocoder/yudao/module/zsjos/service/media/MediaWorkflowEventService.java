package cn.iocoder.yudao.module.zsjos.service.media;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MediaWorkflowEventService {

    @Resource private BusinessEventMapper eventMapper;
    @Resource private BusinessTaskCommandService taskService;
    @Resource private NotifyBusinessEventApi notifyApi;

    public void transition(String type, Long id, Long operator, String from, String to, String reason, String key) {
        if (eventMapper.selectByIdempotencyKey(key) != null) return;
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType("state_changed");
        event.setAggregateType(type);
        event.setAggregateId(id);
        event.setOperatorUserId(operator);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setReason(reason);
        event.setOccurredAt(LocalDateTime.now());
        event.setIdempotencyKey(key);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            // A concurrent replay already persisted the same business transition.
        }
    }

    public Long createTaskAndNotify(String scene, String taskType, String bizType, Long bizId, Long assignee,
                                    String title, String action, Long operator, String key,
                                    Map<String, Object> payload) {
        Long taskId = taskService.create(new BusinessTaskCreateCommand(taskType, bizType, bizId, assignee, title,
                null, action, null, null, null, key));
        notify(scene, bizType, bizId, assignee, operator, key, payload);
        return taskId;
    }

    public void notify(String scene, String bizType, Long bizId, Long assignee, Long operator, String key,
                       Map<String, Object> payload) {
        Map<String, Object> values = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        if (assignee != null) values.put("assigneeUserId", assignee);
        notifyApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId())
                .sceneCode(scene)
                .sourceEventKey(key)
                .bizType(bizType)
                .bizId(bizId)
                .operatorUserId(operator)
                .occurredAt(LocalDateTime.now())
                .payload(values)
                .build());
    }

    public void completeTask(String type, Long bizId, Long assignee) {
        taskService.complete(type, bizId, assignee, LocalDateTime.now());
    }
}
