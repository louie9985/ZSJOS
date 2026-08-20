package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskNotifyStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskNotifyStageMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadNotifyEventPublisher;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactNotifyPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    @Mock private StudentContactNotifyPublisher studentPublisher;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private BusinessTaskCommandService taskCommandService;

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
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq(NEXT_FOLLOW_UP_REMINDER), eq(20L), contains(":overdue"),
                eq(3L), isNull(), eq(now), contextCaptor.capture());
        assertEquals("已逾期", contextCaptor.getValue().get("reminder.stage"));
    }

    @Test
    void reminderStagesExposeReadableChineseLabels() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 20, 0);

        assertStageLabel(now, "advance", 30, now.plusMinutes(10), "即将到期");
        assertStageLabel(now, "due", 0, now, "已到期");
        assertStageLabel(now, "overdue", 5, now.minusMinutes(10), "已逾期");
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

    @Test
    void firstContactDueCreatesSupervisorAssistanceTask() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 12, 0);
        BusinessTaskDO task = task("pending", now); task.setTaskType("student_first_contact");
        task.setBizType("student_service");
        NotifyTimingRuleRespDTO due = new NotifyTimingRuleRespDTO(9L,
                "zsjos.student.first_contact_reminder", "due", 0);
        AdminUserRespDTO planner = new AdminUserRespDTO(); planner.setId(30L); planner.setDeptId(40L);
        DeptRespDTO dept = new DeptRespDTO(); dept.setId(40L); dept.setLeaderUserId(50L);
        when(notifyRuleApi.getEnabledTimingRules(anyCollection())).thenReturn(List.of(due));
        when(taskMapper.selectByIdForUpdate(8L, 9L)).thenReturn(task);
        when(adminUserApi.getUser(30L)).thenReturn(planner); when(deptApi.getDept(40L)).thenReturn(dept);

        assertEquals(1, service.emitDueForTask(8L, now));

        verify(studentPublisher).publish(eq("zsjos.student.first_contact_reminder"), eq(20L),
                contains(":due"), eq(9L), eq(now), anyMap());
        ArgumentCaptor<BusinessTaskCreateCommand> taskCaptor = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(taskCommandService).create(taskCaptor.capture());
        assertEquals("student_first_contact_assistance", taskCaptor.getValue().taskType());
        assertEquals(50L, taskCaptor.getValue().assigneeId());
        assertEquals("student-assistance:8", taskCaptor.getValue().idempotencyKey());
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

    private void assertStageLabel(LocalDateTime now, String stage, int offset,
                                  LocalDateTime dueAt, String expectedLabel) {
        BusinessTaskDO task = task("pending", dueAt);
        when(notifyRuleApi.getEnabledTimingRules(anyCollection())).thenReturn(List.of(rule(2L, stage, offset)));
        when(taskMapper.selectByIdForUpdate(8L, 9L)).thenReturn(task);

        assertEquals(1, service.emitDueForTask(8L, now));

        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq(NEXT_FOLLOW_UP_REMINDER), eq(20L), contains(":" + stage),
                eq(2L), isNull(), eq(now), contextCaptor.capture());
        assertEquals(expectedLabel, contextCaptor.getValue().get("reminder.stage"));
        clearInvocations(publisher, stageMapper, notifyRuleApi, taskMapper);
    }
}
