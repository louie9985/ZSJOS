package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmPendingTaskRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@ExtendWith(MockitoExtension.class)
class LeadAppealReviewDetailTest {
    @InjectMocks private LeadAppealServiceImpl service;
    @Mock private LeadAppealMapper appeals;
    @Mock private LeadMapper leads;
    @Mock private BusinessEventMapper events;
    @Mock private BpmProcessTaskApi tasks;
    @Mock private PermissionApi permissions;
    @Mock private AdminUserApi users;
    @Mock private LeadIdentityMaskingService identity;
    @Mock private SecurityFrameworkService security;
    private LeadAppealDO appeal;
    private LeadDO lead;
    private BpmTaskRespDTO task;

    @BeforeEach void setup() {
        appeal = new LeadAppealDO(); appeal.setId(1L); appeal.setLeadId(2L); appeal.setRoundNo(1);
        appeal.setInvalidEventId(3L); appeal.setStatus(APPEAL_STATUS_SALES_MANAGER_REVIEWING);
        appeal.setReviewStage(APPEAL_STAGE_SALES_MANAGER); appeal.setProcessInstanceId("p"); appeal.setReviewerUserIdsSnapshot("[9]");
        lead = new LeadDO(); lead.setId(2L); lead.setLeadNo("KZ-TEST"); lead.setSubmittedName("测试客资"); lead.setStatus(STATUS_INVALID);
        task = new BpmTaskRespDTO(); task.setId("t"); task.setProcessInstanceId("p");
        task.setBusinessKey(APPEAL_BUSINESS_KEY_PREFIX + "1"); task.setTaskDefinitionKey(APPEAL_TASK_DEFINITION_KEY);
        lenient().when(appeals.selectById(1L)).thenReturn(appeal);
        lenient().when(permissions.hasAnyPermissions(9L, PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        var user = new AdminUserRespDTO(); user.setId(9L); user.setStatus(0);
        lenient().when(users.getUser(9L)).thenReturn(user);
        lenient().when(leads.selectById(2L)).thenReturn(lead);
        lenient().when(events.selectLatestInvalidId(2L)).thenReturn(3L);
        lenient().when(appeals.selectListByLeadId(2L)).thenReturn(List.of(appeal));
    }
    @Test void permissionAndSnapshotAreRequiredBeforeReadingTaskOrLead() {
        when(permissions.hasAnyPermissions(9L, PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.getReviewContext(1L, "t", 9L));
        verifyNoInteractions(tasks);
        when(permissions.hasAnyPermissions(9L, PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        appeal.setReviewerUserIdsSnapshot("[8]");
        assertThrows(RuntimeException.class, () -> service.getReviewContext(1L, "t", 9L));
        verifyNoInteractions(tasks);
    }
    @Test void rejectsOtherTaskBusinessProcessAndNode() {
        when(tasks.getDoneTask(9L, "t")).thenReturn(task);
        task.setBusinessKey(APPEAL_BUSINESS_KEY_PREFIX + "99");
        assertThrows(RuntimeException.class, () -> service.getReviewContext(1L, "t", 9L));
        task.setBusinessKey(APPEAL_BUSINESS_KEY_PREFIX + "1"); task.setProcessInstanceId("other");
        assertThrows(RuntimeException.class, () -> service.getReviewContext(1L, "t", 9L));
        task.setProcessInstanceId("p"); task.setTaskDefinitionKey("other");
        assertThrows(RuntimeException.class, () -> service.getReviewContext(1L, "t", 9L));
        verify(leads, never()).selectById(anyLong());
    }
    @Test void deniesMissingOrForeignTask() {
        assertThrows(RuntimeException.class, () -> service.getReviewContext(1L, "", 9L));
        assertThrows(RuntimeException.class, () -> service.getReviewContext(1L, "foreign", 9L));
        verify(leads, never()).selectById(anyLong());
    }
    @Test void pendingTaskIsActionableOnlyInCurrentCycle() {
        when(tasks.getTodoTask(9L, "t")).thenReturn(task);
        when(tasks.getPendingTasks("p")).thenReturn(List.of(new BpmPendingTaskRespDTO("t", APPEAL_TASK_DEFINITION_KEY, 9L, null)));
        var result = service.getReviewContext(1L, "t", 9L);
        assertEquals("t", result.getRecords().getFirst().getTaskId()); assertFalse(result.getCanSubmit());
        when(events.selectLatestInvalidId(2L)).thenReturn(4L);
        assertNull(service.getReviewContext(1L, "t", 9L).getRecords().getFirst().getTaskId());
    }
    @Test void completedTaskRemainsReadableWithoutDecisionAccess() {
        when(tasks.getDoneTask(9L, "t")).thenReturn(task); appeal.setStatus(APPEAL_STATUS_UPHELD);
        var result = service.getReviewContext(1L, "t", 9L);
        assertNull(result.getRecords().getFirst().getTaskId()); assertFalse(result.getCanSubmit());
        verify(tasks, never()).getTodoTask(anyLong(), anyString());
    }
    @Test void taskOnlyViewerGetsNoContactDetailsOrGeneralActions() {
        when(tasks.getDoneTask(9L, "t")).thenReturn(task);
        when(security.hasAnyPermissions(any(String[].class))).thenReturn(true);
        lead.setSubmittedMobile("private");
        var permission = mock(LeadObjectPermissionService.class);
        var management = mock(LeadManagementService.class);
        var detail = detailService(permission, management);
        var result = detail.getDetail(1L, "t", 9L).getLead();
        assertEquals(List.of("appeals"), result.getVisibleTabs()); assertTrue(result.getAvailableActions().isEmpty());
        assertEquals("KZ-TEST", result.getLeadNo()); assertNull(result.getSubmittedMobile());
        verifyNoInteractions(management);
    }
    @Test void normalFeaturePermissionIsRequiredInAdditionToObjectRelationship() {
        when(tasks.getDoneTask(9L, "t")).thenReturn(task);
        var permission = mock(LeadObjectPermissionService.class);
        var management = mock(LeadManagementService.class);
        var result = detailService(permission, management).getDetail(1L, "t", 9L).getLead();
        assertEquals(List.of("appeals"), result.getVisibleTabs());
        verifyNoInteractions(permission, management);
    }
    @Test void authorizedViewerKeepsExistingProjection() {
        when(tasks.getDoneTask(9L, "t")).thenReturn(task);
        when(security.hasAnyPermissions(any(String[].class))).thenReturn(true);
        var permission = mock(LeadObjectPermissionService.class); when(permission.canReadDetail(lead, 9L)).thenReturn(true);
        var management = mock(LeadManagementService.class); var projection = new LeadManagementRespVO();
        when(management.getLead(2L, 9L)).thenReturn(projection);
        assertSame(projection, detailService(permission, management).getDetail(1L, "t", 9L).getLead());
    }
    private LeadAppealReviewDetailService detailService(LeadObjectPermissionService permission, LeadManagementService management) {
        var detail = new LeadAppealReviewDetailService();
        ReflectionTestUtils.setField(detail, "appealService", service); ReflectionTestUtils.setField(detail, "leadMapper", leads);
        ReflectionTestUtils.setField(detail, "permissionService", permission); ReflectionTestUtils.setField(detail, "managementService", management);
        ReflectionTestUtils.setField(detail, "security", security);
        return detail;
    }
}
