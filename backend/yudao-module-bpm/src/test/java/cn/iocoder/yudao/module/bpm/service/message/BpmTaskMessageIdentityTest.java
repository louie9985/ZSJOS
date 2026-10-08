package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmTaskMessageIdentityTest {
    @Test void missingInitiatorPreservesTaskIdentityAndRecipient() {
        var api = mock(NotifyBusinessEventApi.class);
        var service = new BpmMessageServiceImpl();
        var properties = new WebProperties();
        var ui = new WebProperties.Ui();
        ui.setUrl("https://example.invalid");
        properties.setAdminUi(ui);
        ReflectionTestUtils.setField(service, "notifyBusinessEventApi", api);
        ReflectionTestUtils.setField(service, "webProperties", properties);
        var request = new BpmMessageSendWhenTaskCreatedReqDTO();
        request.setProcessInstanceId("process");
        request.setProcessInstanceName("测试流程");
        request.setTaskName("审批");
        request.setTaskId("task-1");
        request.setAssigneeUserId(7L);
        request.setStartUserNickname("发起人信息不可用");
        service.sendMessageWhenTaskAssigned(request);
        service.sendMessageWhenTaskAssigned(request);
        request.setTaskId("task-2");
        service.sendMessageWhenTaskAssigned(request);
        var events = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(api, times(3)).publish(events.capture());
        var values = events.getAllValues();
        assertEquals("bpm.task.assigned:task-1", values.get(0).getSourceEventKey());
        assertEquals(values.get(0).getSourceEventKey(), values.get(1).getSourceEventKey());
        assertNotEquals(values.get(0).getSourceEventKey(), values.get(2).getSourceEventKey());
        for (var event : values) {
            assertEquals(7L, event.getPayload().get("targetUserId"));
            assertEquals(cn.iocoder.yudao.framework.common.enums.UserTypeEnum.ADMIN.getValue(),
                    event.getPayload().get("targetUserType"));
            assertEquals("发起人信息不可用", event.getPayload().get("startUserNickname"));
        }
    }
}
