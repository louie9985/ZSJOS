package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkPlanReminderServiceTest {
    @InjectMocks private WorkPlanReminderService service;
    @Mock private WorkTaskMapper taskMapper;
    @Mock private WorkPlanNotifyEventPublisher notifyPublisher;
    @Test void onlyAtomicMarkerWinnerPublishes() {
        WorkTaskDO task = new WorkTaskDO().setId(1L);
        when(taskMapper.selectReminderCandidates(any(), eq(200))).thenReturn(List.of(task)); when(taskMapper.markReminderNotified(eq(1L), any())).thenReturn(1); when(taskMapper.selectOverdueCandidates(any(), eq(200))).thenReturn(List.of());
        service.scan(); verify(notifyPublisher).publishTask(anyString(), eq(task), anyString(), eq(0L), any(), anyMap());
    }
}
