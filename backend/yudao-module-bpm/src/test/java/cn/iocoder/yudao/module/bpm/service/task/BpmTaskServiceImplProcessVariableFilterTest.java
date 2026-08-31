package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskPageReqVO;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BpmTaskServiceImplProcessVariableFilterTest {

    private final BpmTaskServiceImpl service = new BpmTaskServiceImpl();

    @Test
    void taskQueryUsesOrGroupForMultipleAllowedValues() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);

        Boolean applied = ReflectionTestUtils.invokeMethod(service, "applyProcessVariableFilter",
                query, "reviewStage", List.of("sales_manager", "quality"));

        assertTrue(applied);
        verify(query).or();
        verify(query).processVariableValueEquals("reviewStage", "sales_manager");
        verify(query).processVariableValueEquals("reviewStage", "quality");
        verify(query).endOr();
    }

    @Test
    void historicQueryUsesDirectEqualityForSingleAllowedValue() {
        HistoricTaskInstanceQuery query = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);

        Boolean applied = ReflectionTestUtils.invokeMethod(service, "applyProcessVariableFilter",
                query, "reviewStage", List.of("chairman"));

        assertTrue(applied);
        verify(query).processVariableValueEquals("reviewStage", "chairman");
        verify(query, never()).or();
        verify(query, never()).endOr();
    }

    @Test
    void incompleteFilterReturnsNoMatchInsteadOfDroppingTheConstraint() {
        TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);

        Boolean applied = ReflectionTestUtils.invokeMethod(service, "applyProcessVariableFilter",
                query, null, List.of("sales_manager"));

        assertFalse(applied);
        verifyNoInteractions(query);
    }

    @Test
    void blankAllowedValueReturnsNoMatchInsteadOfQueryingFlowable() {
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        HistoricTaskInstanceQuery historicQuery = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);

        Boolean taskApplied = ReflectionTestUtils.invokeMethod(service, "applyProcessVariableFilter",
                taskQuery, "reviewStage", List.of(""));
        Boolean historicApplied = ReflectionTestUtils.invokeMethod(service, "applyProcessVariableFilter",
                historicQuery, "reviewStage", java.util.Arrays.asList("quality", null));

        assertFalse(taskApplied);
        assertFalse(historicApplied);
        verifyNoInteractions(taskQuery, historicQuery);
    }

    @Test
    void donePageScopesHistoricTasksToCurrentTenant() {
        HistoryService historyService = mock(HistoryService.class);
        HistoricTaskInstanceQuery query = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(query);
        when(query.count()).thenReturn(0L);
        ReflectionTestUtils.setField(service, "historyService", historyService);
        BpmTaskPageReqVO pageReqVO = new BpmTaskPageReqVO();
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(10);

        TenantContextHolder.setTenantId(1L);
        try {
            service.getTaskDonePage(233L, pageReqVO, "reviewStage", List.of("quality"));

            verify(query).taskTenantId("1");
            verify(query).processVariableValueEquals("reviewStage", "quality");
        } finally {
            TenantContextHolder.clear();
        }
    }
}
