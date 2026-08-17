package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.NOTIFY_SCENE_TASK_CREATED;

@Component
public class RegistrationNotifyPublisher {

    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;

    public void publishTaskCreated(RegistrationCaseDO registrationCase, SalesOrderDO order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("registrationCaseId", registrationCase.getId());
        payload.put("orderId", registrationCase.getOrderId());
        payload.put("orderNo", order == null ? "" : order.getOrderNo());
        payload.put("studentName", order == null ? "" : order.getStudentName());
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId())
                .sceneCode(NOTIFY_SCENE_TASK_CREATED)
                .sourceEventKey("registration-task-created:" + registrationCase.getId())
                .bizType("registration_case").bizId(registrationCase.getId())
                .occurredAt(registrationCase.getRegistrationApprovedAt()).payload(payload).build());
    }
}
