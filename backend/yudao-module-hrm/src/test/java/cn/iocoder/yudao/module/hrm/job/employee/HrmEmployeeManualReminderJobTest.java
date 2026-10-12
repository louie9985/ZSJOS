package cn.iocoder.yudao.module.hrm.job.employee;

import cn.iocoder.yudao.module.hrm.service.employee.info.HrmLifecycleReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class HrmEmployeeManualReminderJobTest {
    @Test void oldSchedulerOnlyDispatchesMessages() {
        var reminder=mock(HrmLifecycleReminderService.class);
        when(reminder.dispatch()).thenReturn("reminder-only");
        var job=new HrmEmployeeChangeJob(); ReflectionTestUtils.setField(job,"reminders",reminder);
        assertEquals("reminder-only",job.execute("2099-12-31"));
        verify(reminder).dispatch(); verifyNoMoreInteractions(reminder);
    }
}
