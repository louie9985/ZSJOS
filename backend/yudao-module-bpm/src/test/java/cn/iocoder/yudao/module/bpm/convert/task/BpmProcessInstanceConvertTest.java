package cn.iocoder.yudao.module.bpm.convert.task;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmProcessInstanceConvertTest {

    @Test
    void buildsVersionedStableStatusEvent() {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn("process-1");
        when(instance.getProcessDefinitionId()).thenReturn("definition-3");
        when(instance.getProcessDefinitionKey()).thenReturn("content-review");
        when(instance.getBusinessKey()).thenReturn("content-review-batch:9");

        BpmProcessInstanceStatusEvent event = BpmProcessInstanceConvert.INSTANCE
                .buildProcessInstanceStatusEvent(this, instance, 3, 2, "done");

        assertEquals("definition-3", event.getProcessDefinitionId());
        assertEquals(3, event.getProcessDefinitionVersion());
        assertEquals("process-instance-status:process-1:2", event.getEventKey());
        assertEquals("content-review-batch:9", event.getBusinessKey());
    }

}
