package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskNotifyStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskNotifyStageMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadNotifyEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.NEXT_FOLLOW_UP_REMINDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessTaskReminderServiceTest {
    @InjectMocks private BusinessTaskReminderService service;
    @Mock private BusinessTaskMapper taskMapper;
    @Mock private BusinessTaskNotifyStageMapper stageMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private NotifyRuleApi notifyRuleApi;
    @Mock private LeadNotifyEventPublisher publisher;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(9L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void downtimeAcrossStagesEmitsOnlyMostUrgentStage() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 0);
        BusinessTaskDO task = task("pending", now.minusMinutes(10));
        List<NotifyTimingRuleRespDTO> rules = List.of(
                rule(1L, "advance", 30), rule(2L, "due", 0), rule(3L, "overdue", 5));
        when(notifyRuleApi.getEnabledTimingRules(anyCollection())).thenReturn(rules);
        when(taskMapper.selectPendingReminderCandidates(anyList(), any(), eq(200))).thenReturn(List.of(task));
        when(taskMapper.selectByIdForUpdate(8L, 9L)).thenReturn(task);

        assertEquals(1, service.emitPending(now));

        verify(stageMapper, times(3)).insert(any(BusinessTaskNotifyStageDO.class));
        verify(publisher).publish(eq(NEXT_FOLLOW_UP_REMINDER), eq(20L), contains(":overdue"),
                eq(3L), isNull(), eq(now), anyMap());
    }

    @Test
    void cancelledTaskNeverEmits() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 0);
        BusinessTaskDO task = task("cancelled", now.minusMinutes(10));
        when(notifyRuleApi.getEnabledTimingRules(anyCollection())).thenReturn(List.of(rule(2L, "due", 0)));
        when(taskMapper.selectByIdForUpdate(8L, 9L)).thenReturn(task);

        assertEquals(0, service.emitDueForTask(8L, now));

        verifyNoInteractions(stageMapper, publisher);
    }

    private BusinessTaskDO task(String status, LocalDateTime dueAt) {
        BusinessTaskDO task = new BusinessTaskDO();
        task.setId(8L); task.setTaskType(TASK_TYPE_FOLLOW_UP_REMINDER); task.setBizId(20L);
        task.setAssigneeId(30L); task.setStatus(status); task.setDueAt(dueAt);
        return task;
    }

    private NotifyTimingRuleRespDTO rule(Long id, String stage, int offset) {
        return new NotifyTimingRuleRespDTO(id, NEXT_FOLLOW_UP_REMINDER, stage, offset);
    }
}
