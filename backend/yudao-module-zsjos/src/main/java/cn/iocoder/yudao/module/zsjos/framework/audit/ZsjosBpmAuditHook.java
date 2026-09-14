package cn.iocoder.yudao.module.zsjos.framework.audit;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceAuditHook;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.zsjos.service.audit.AuditActionCatalog;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bridges BPM status events into the ZSJOS audit store without coupling BPM to ZSJOS. */
@Component
public class ZsjosBpmAuditHook implements BpmProcessInstanceAuditHook {
    @Resource
    private BusinessAuditService auditService;

    @Override
    public void onProcessInstanceEvent(BpmProcessInstanceStatusEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventKey", event.getEventKey());
        details.put("processInstanceId", event.getId());
        details.put("processDefinitionKey", event.getProcessDefinitionKey());
        details.put("processDefinitionId", event.getProcessDefinitionId());
        details.put("processDefinitionVersion", event.getProcessDefinitionVersion());
        details.put("status", event.getStatus());
        details.put("businessKey", event.getBusinessKey());
        details.put("reason", event.getReason());
        details.put("initiatorUserId", event.getInitiatorUserId());
        details.put("initiatorNameSnapshot", event.getInitiatorNameSnapshot());
        details.put("executorType", event.getExecutorType());
        details.put("executorIdentity", event.getExecutorIdentity());
        auditService.record(AuditActionCatalog.CATEGORY_EXECUTION,
                AuditActionCatalog.EXECUTION_BPM, "bpm-process", event.getBusinessKey(),
                event.getExecutorType() == null ? "SYSTEM_BPM" : event.getExecutorType(), details);
    }
}
