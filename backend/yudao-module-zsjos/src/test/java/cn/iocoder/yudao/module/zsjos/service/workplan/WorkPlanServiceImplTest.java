package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.WorkPlanSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.WorkPlanCancelReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.WorkPlanSummaryReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanSummaryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanSummaryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkChangeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanFieldDefinitionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanFieldValueMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkReportMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_PLAN_PERIOD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_PLAN_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.PLAN_ACTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.PLAN_CANCELLED;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.PERIOD_MONTH;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.PERIOD_WEEK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class WorkPlanServiceImplTest {
    @InjectMocks private WorkPlanServiceImpl service;
    @Mock private WorkTaskMapper taskMapper;
    @Mock private WorkPlanMapper planMapper;
    @Mock private WorkPlanSummaryMapper summaryMapper;
    @Mock private WorkChangeMapper changeMapper;
    @Mock private WorkPlanFieldDefinitionMapper definitionMapper;
    @Mock private WorkPlanFieldValueMapper fieldValueMapper;
    @Mock private WorkReportMapper reportMapper;
    @Mock private WorkPlanObjectPermissionProvider permissionProvider;
    @Mock private BusinessTaskCommandService taskCommandService;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test void simplifiedStateModelUsesActiveAndSummaryCompletion() {
        assertEquals("active", cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.PLAN_ACTIVE);
        assertEquals("completed", cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.PLAN_COMPLETED);
    }

    @Test void descriptivePeriodTypesDoNotRestrictCalendarRange() {
        WorkPlanSaveReqVO month = plan(PERIOD_MONTH, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 10));
        WorkPlanSaveReqVO week = plan(PERIOD_WEEK, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20));

        assertDoesNotThrow(() -> service.validatePlanRequest(month));
        assertDoesNotThrow(() -> service.validatePlanRequest(week));
    }

    @Test void planEndDateBeforeStartDateIsStillRejected() {
        WorkPlanSaveReqVO request = plan(PERIOD_MONTH, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 19));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.validatePlanRequest(request));
        assertEquals(WORK_PLAN_PERIOD_INVALID.getCode(), exception.getCode());
    }

    @Test void taskCancellationDoesNotCascadeUnlessExplicitlyRequested() {
        WorkTaskDO parent = new WorkTaskDO().setId(1L);
        WorkTaskDO child = new WorkTaskDO().setId(2L).setParentTaskId(1L);

        assertEquals(List.of(parent), service.cancellationTargets(1L, List.of(parent, child), false));
        assertEquals(2, service.cancellationTargets(1L, List.of(parent, child), true).size());
    }

    @Test void onlyAssignmentAndConfirmationChangesRequireReason() {
        WorkTaskDO existing = new WorkTaskDO().setAssigneeUserId(1L).setConfirmationRequired(false);
        WorkTaskDO ordinaryUpdate = new WorkTaskDO().setAssigneeUserId(1L).setConfirmationRequired(false)
                .setTitle("新说明");
        WorkTaskDO reassignment = new WorkTaskDO().setAssigneeUserId(2L).setConfirmationRequired(false);

        assertEquals(false, service.assignmentChanged(existing, ordinaryUpdate));
        assertEquals(true, service.assignmentChanged(existing, reassignment));
    }

    @Test void summaryIsRejectedWhileAnyTaskRemainsActive() {
        WorkPlanDO plan = new WorkPlanDO().setId(1L).setStatus(PLAN_ACTIVE).setVersion(2);
        WorkPlanSummaryReqVO request = new WorkPlanSummaryReqVO().setVersion(2).setSummary("阶段总结");
        when(planMapper.selectByIdForUpdate(1L, 1L)).thenReturn(plan);
        when(taskMapper.countActiveByPlanId(1L)).thenReturn(1L);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitSummary(1L, request, 10L));

        assertEquals(WORK_PLAN_STATE_INVALID.getCode(), error.getCode());
        verify(summaryMapper, never()).insert(org.mockito.ArgumentMatchers.any(WorkPlanSummaryDO.class));
    }

    @Test void cancelLocksPlanBeforeReadingTasks() {
        WorkPlanDO plan = new WorkPlanDO().setId(1L).setStatus(PLAN_ACTIVE).setVersion(2);
        WorkPlanCancelReqVO request = new WorkPlanCancelReqVO(); request.setVersion(2); request.setReason("计划终止");
        when(planMapper.selectByIdForUpdate(1L, 1L)).thenReturn(plan);
        when(taskMapper.selectListByPlanId(1L)).thenReturn(List.of());
        when(planMapper.transition(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(PLAN_ACTIVE), org.mockito.ArgumentMatchers.eq(PLAN_CANCELLED),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("计划终止"))).thenReturn(1);

        service.cancel(1L, request, 10L);

        var order = inOrder(planMapper, taskMapper);
        order.verify(planMapper).selectByIdForUpdate(1L, 1L);
        order.verify(taskMapper).selectListByPlanId(1L);
    }

    @Test void detailQueriesTaskChangesOnlyForVisibleTaskIds() {
        WorkPlanDO plan = new WorkPlanDO().setId(1L).setStatus(PLAN_ACTIVE);
        WorkTaskDO visible = new WorkTaskDO().setId(11L).setPlanId(1L).setAssigneeUserId(10L);
        WorkTaskDO hidden = new WorkTaskDO().setId(12L).setPlanId(1L).setAssigneeUserId(20L);
        when(planMapper.selectById(1L)).thenReturn(plan);
        when(taskMapper.selectListByPlanId(1L)).thenReturn(List.of(visible, hidden));
        when(definitionMapper.selectListByPlanId(1L)).thenReturn(List.of());
        when(fieldValueMapper.selectListBySubject("plan", 1L)).thenReturn(List.of());
        when(fieldValueMapper.selectListBySubject("task", 11L)).thenReturn(List.of());
        when(reportMapper.selectListByTaskId(11L)).thenReturn(List.of());
        when(permissionProvider.hasFullPlanAccess(plan, 10L)).thenReturn(false);
        when(changeMapper.selectListByPlan(1L, List.of(11L))).thenReturn(List.of());

        service.get(1L, 10L);

        verify(changeMapper).selectListByPlan(1L, List.of(11L));
    }

    private WorkPlanSaveReqVO plan(String periodType, LocalDate startDate, LocalDate endDate) {
        return new WorkPlanSaveReqVO().setPeriodType(periodType).setStartDate(startDate).setEndDate(endDate);
    }
}
