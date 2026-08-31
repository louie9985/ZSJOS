package cn.iocoder.yudao.module.zsjos.service.bpm;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.bpm.vo.ZsjosBpmBusinessTaskTargetRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderApprovalTaskTargetRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.enums.LeadConstants;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderSupervisorConfirmationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZsjosBpmBusinessTaskTargetServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final String TASK_ID = "task-1";
    private static final Long ORDER_ID = 100L;
    private static final Long APPEAL_ID = 200L;

    @InjectMocks private ZsjosBpmBusinessTaskTargetServiceImpl service;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private SalesOrderSupervisorConfirmationService salesOrderTargetService;
    @Mock private LeadAppealMapper leadAppealMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(USER_ID);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus());
        lenient().when(adminUserApi.getUser(USER_ID)).thenReturn(user);
    }

    @Test
    void salesOrderTodoUsesBusinessTargetService() {
        when(processTaskApi.getTodoTask(USER_ID, TASK_ID)).thenReturn(task(
                cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.PROCESS_DEFINITION_KEY,
                cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.BUSINESS_KEY_PREFIX + ORDER_ID,
                cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.TASK_REGISTRATION,
                null));
        SalesOrderApprovalTaskTargetRespVO source = new SalesOrderApprovalTaskTargetRespVO();
        source.setWorkType("approval");
        source.setOrderId(ORDER_ID);
        source.setTaskId(TASK_ID);
        source.setTaskDefinitionKey(cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.TASK_REGISTRATION);
        source.setCenter(cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.CENTER_REGISTRATION);
        when(salesOrderTargetService.getTaskTarget(TASK_ID, USER_ID, false)).thenReturn(source);

        ZsjosBpmBusinessTaskTargetRespVO target = service.getTarget(TASK_ID, "todo", USER_ID);

        assertTrue(target.getSupported());
        assertEquals("sales_order", target.getBizType());
        assertEquals("/zsjos/sales-order-approvals", target.getRoute());
        assertEquals("approval", target.getQuery().get("workType"));
        assertEquals(ORDER_ID, target.getQuery().get("orderId"));
        assertEquals(TASK_ID, target.getQuery().get("taskId"));
        verify(salesOrderTargetService).getTaskTarget(TASK_ID, USER_ID, false);
        verify(processTaskApi).getTodoTask(USER_ID, TASK_ID);
    }

    @Test
    void salesOrderDoneUsesReadOnlyTarget() {
        when(processTaskApi.getDoneTask(USER_ID, TASK_ID)).thenReturn(task(
                cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.PROCESS_DEFINITION_KEY,
                cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.BUSINESS_KEY_PREFIX + ORDER_ID,
                cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.TASK_REGISTRATION,
                null));
        SalesOrderApprovalTaskTargetRespVO source = new SalesOrderApprovalTaskTargetRespVO();
        source.setWorkType("approval");
        source.setOrderId(ORDER_ID);
        source.setTaskId(TASK_ID);
        source.setTaskDefinitionKey(cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.TASK_REGISTRATION);
        source.setCenter(cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.CENTER_REGISTRATION);
        when(salesOrderTargetService.getTaskTarget(TASK_ID, USER_ID, true)).thenReturn(source);

        ZsjosBpmBusinessTaskTargetRespVO target = service.getTarget(TASK_ID, "done", USER_ID);

        assertTrue(target.getSupported());
        assertEquals("sales_order", target.getBizType());
        verify(salesOrderTargetService).getTaskTarget(TASK_ID, USER_ID, true);
        verify(processTaskApi).getDoneTask(USER_ID, TASK_ID);
        verify(processTaskApi, never()).getTodoTask(anyLong(), anyString());
    }

    @Test
    void leadAppealTodoBuildsTargetAndKeepsHandlingState() {
        when(processTaskApi.getTodoTask(USER_ID, TASK_ID)).thenReturn(task(
                LeadConstants.APPEAL_PROCESS_DEFINITION_KEY,
                LeadConstants.APPEAL_BUSINESS_KEY_PREFIX + APPEAL_ID,
                LeadConstants.APPEAL_TASK_DEFINITION_KEY,
                "process-1"));
        when(permissionApi.hasAnyPermissions(USER_ID, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        when(leadAppealMapper.selectById(APPEAL_ID)).thenReturn(appeal("process-1", LeadConstants.APPEAL_STAGE_SALES_MANAGER, LeadConstants.APPEAL_STATUS_SALES_MANAGER_REVIEWING));

        ZsjosBpmBusinessTaskTargetRespVO target = service.getTarget(TASK_ID, "todo", USER_ID);

        assertTrue(target.getSupported());
        assertEquals("lead_appeal", target.getBizType());
        assertEquals("/zsjos/appeals", target.getRoute());
        assertEquals(APPEAL_ID, target.getQuery().get("appealId"));
        assertEquals(200L, target.getQuery().get("leadId"));
        assertEquals(Boolean.FALSE, target.getQuery().get("handled"));
    }

    @Test
    void leadAppealDoneBuildsHandledTarget() {
        when(processTaskApi.getDoneTask(USER_ID, TASK_ID)).thenReturn(task(
                LeadConstants.APPEAL_PROCESS_DEFINITION_KEY,
                LeadConstants.APPEAL_BUSINESS_KEY_PREFIX + APPEAL_ID,
                LeadConstants.APPEAL_TASK_DEFINITION_KEY,
                "process-1"));
        when(permissionApi.hasAnyPermissions(USER_ID, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        when(leadAppealMapper.selectById(APPEAL_ID)).thenReturn(appeal("process-1", LeadConstants.APPEAL_STAGE_SALES_MANAGER, LeadConstants.APPEAL_STATUS_OVERTURNED));

        ZsjosBpmBusinessTaskTargetRespVO target = service.getTarget(TASK_ID, "done", USER_ID);

        assertTrue(target.getSupported());
        assertEquals(Boolean.TRUE, target.getQuery().get("handled"));
        verify(processTaskApi).getDoneTask(USER_ID, TASK_ID);
    }

    @Test
    void unsupportedProcessReturnsStableMessage() {
        when(processTaskApi.getTodoTask(USER_ID, TASK_ID)).thenReturn(task("other_process", "biz:1", "task", "process-1"));

        ZsjosBpmBusinessTaskTargetRespVO target = service.getTarget(TASK_ID, "todo", USER_ID);

        assertFalse(target.getSupported());
        assertEquals("unsupported", target.getBizType());
        assertEquals("该流程暂未接入员工端业务审批页，请在完整 BPM 表单中处理。", target.getMessage());
    }

    @Test
    void appealTaskRequiresMatchingDefinitionKey() {
        when(processTaskApi.getTodoTask(USER_ID, TASK_ID)).thenReturn(task(
                LeadConstants.APPEAL_PROCESS_DEFINITION_KEY,
                LeadConstants.APPEAL_BUSINESS_KEY_PREFIX + APPEAL_ID,
                "wrongKey",
                "process-1"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.getTarget(TASK_ID, "todo", USER_ID));

        assertEquals(ZsjosErrorCodeConstants.LEAD_APPEAL_PERMISSION_DENIED.getCode(), error.getCode());
    }

    private BpmTaskRespDTO task(String processDefinitionKey, String businessKey, String taskDefinitionKey, String processInstanceId) {
        return new BpmTaskRespDTO()
                .setId(TASK_ID)
                .setProcessInstanceId(processInstanceId)
                .setProcessDefinitionKey(processDefinitionKey)
                .setBusinessKey(businessKey)
                .setTaskDefinitionKey(taskDefinitionKey)
                .setSignTask(false);
    }

    private LeadAppealDO appeal(String processInstanceId, String reviewStage, String status) {
        LeadAppealDO appeal = new LeadAppealDO();
        appeal.setId(APPEAL_ID);
        appeal.setLeadId(200L);
        appeal.setProcessInstanceId(processInstanceId);
        appeal.setReviewStage(reviewStage);
        appeal.setStatus(status);
        appeal.setReviewerUserIdsSnapshot("[" + USER_ID + "]");
        return appeal;
    }
}
