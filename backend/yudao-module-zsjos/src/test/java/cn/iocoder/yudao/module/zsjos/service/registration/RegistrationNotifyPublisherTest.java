package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationNotifyPublisherTest {

    @InjectMocks private RegistrationNotifyPublisher publisher;
    @Mock private NotifyBusinessEventApi notifyBusinessEventApi;

    @Test
    void plannerAssignmentPublishesLeadNumberAndPlannerRecipient() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(2L); registrationCase.setOrderId(9L); registrationCase.setVersion(3);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(9L); order.setLeadId(29L); order.setStudentName("验收学员");
        TenantContextHolder.setTenantId(1L);
        try {
            publisher.publishPlannerAssigned(registrationCase, order, "KZ202608172332360004", 241L);
        } finally {
            TenantContextHolder.clear();
        }

        ArgumentCaptor<NotifyBusinessEvent> captor = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi).publish(captor.capture());
        NotifyBusinessEvent event = captor.getValue();
        assertEquals(RegistrationConstants.NOTIFY_SCENE_PLANNER_ASSIGNED, event.getSceneCode());
        assertEquals("registration-planner-assigned:2:3:241", event.getSourceEventKey());
        Map<String, Object> payload = event.getPayload();
        assertEquals("KZ202608172332360004", payload.get("leadNo"));
        assertEquals(241L, payload.get("studyPlannerUserId"));
    }

    @Test
    void directorAssignmentUsesRouteScopedIdempotencyKey() {
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setId(3L); registrationCase.setOrderId(10L);
        SalesOrderDO order = new SalesOrderDO();
        order.setId(10L); order.setStudentName("编导学员");
        TenantContextHolder.setTenantId(1L);
        try {
            publisher.publishDirectorAssigned(registrationCase, order, "", 31L, 301L);
        } finally {
            TenantContextHolder.clear();
        }

        ArgumentCaptor<NotifyBusinessEvent> captor = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyBusinessEventApi).publish(captor.capture());
        assertEquals(RegistrationConstants.NOTIFY_SCENE_DIRECTOR_ASSIGNED, captor.getValue().getSceneCode());
        assertEquals("registration-director-assigned:3:31:301", captor.getValue().getSourceEventKey());
        assertEquals(301L, captor.getValue().getPayload().get("contentDirectorUserId"));
    }
}
