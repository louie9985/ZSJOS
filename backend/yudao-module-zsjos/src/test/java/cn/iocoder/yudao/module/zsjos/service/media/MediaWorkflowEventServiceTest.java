package cn.iocoder.yudao.module.zsjos.service.media;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
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
class MediaWorkflowEventServiceTest {

    @InjectMocks private MediaWorkflowEventService service;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private BusinessTaskCommandService taskService;
    @Mock private NotifyBusinessEventApi notifyApi;

    @Test
    void notifyAddsAssigneeAndKeepsBusinessNumberSnapshot() {
        TenantContextHolder.setTenantId(1L);
        try {
            service.notify("media.content.rejected", "content", 10L, 21L, 31L, "content-result:10:1",
                    Map.of("bizNo", "CT-202608210001", "deepLink", "/zsjos/content?contentId=10"));
        } finally {
            TenantContextHolder.clear();
        }

        ArgumentCaptor<NotifyBusinessEvent> captor = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(notifyApi).publish(captor.capture());
        NotifyBusinessEvent event = captor.getValue();
        assertEquals("content-result:10:1", event.getSourceEventKey());
        assertEquals("CT-202608210001", event.getPayload().get("bizNo"));
        assertEquals(21L, event.getPayload().get("assigneeUserId"));
    }
}
