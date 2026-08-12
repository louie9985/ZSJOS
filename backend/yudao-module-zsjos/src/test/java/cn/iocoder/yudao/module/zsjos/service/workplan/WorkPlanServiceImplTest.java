package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.WorkPlanSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_PLAN_PERIOD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.PERIOD_MONTH;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.PERIOD_WEEK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class WorkPlanServiceImplTest {
    @InjectMocks private WorkPlanServiceImpl service;
    @Mock private WorkTaskMapper taskMapper;

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

    private WorkPlanSaveReqVO plan(String periodType, LocalDate startDate, LocalDate endDate) {
        return new WorkPlanSaveReqVO().setPeriodType(periodType).setStartDate(startDate).setEndDate(endDate);
    }
}
