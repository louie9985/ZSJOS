package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.enums.LeadConstants;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadAppealServiceImplTest {

    @InjectMocks private LeadAppealServiceImpl service;
    @Mock private LeadAppealMapper appealMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock private LeadAttachmentService attachmentService;
    @Mock private FileApi fileApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private RoleApi roleApi;
    @Mock private PermissionApi permissionApi;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private LeadIntendedProductMapper intendedProductMapper;
    @Mock private CashbackService cashbackService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void submitSnapshotsDirectDepartmentSupervisor() {
        LeadDO lead = invalidLead(20L);
        lead.setInvalidReason("duplicate_lead");
        lead.setInvalidReasonLabelSnapshot(null);
        AdminUserRespDTO owner = user(20L, 100L);
        AdminUserRespDTO supervisor = user(30L, 10L);
        DeptRespDTO dept = dept(100L, 10L, 30L);
        when(leadMapper.selectByIdForUpdate(8L, 1L)).thenReturn(lead);
        when(appealMapper.selectBySubmissionIdempotencyKey("key-1")).thenReturn(null);
        when(appealMapper.selectLatestByLeadId(8L)).thenReturn(null);
        when(adminUserApi.getUser(20L)).thenReturn(owner);
        when(adminUserApi.getUser(30L)).thenReturn(supervisor);
        when(deptApi.getDept(100L)).thenReturn(dept);
        when(permissionApi.hasAnyPermissions(30L, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        when(processInstanceApi.createProcessInstance(eq(7L), any())).thenReturn("process-1");
        doAnswer(invocation -> {
            ((LeadAppealDO) invocation.getArgument(0)).setId(55L);
            return 1;
        }).when(appealMapper).insert(any(LeadAppealDO.class));

        LeadAppealSubmitReqVO request = new LeadAppealSubmitReqVO();
        request.setReason("申诉原因");
        request.setIdempotencyKey("key-1");

        assertEquals(55L, service.submit(8L, 7L, request));

        ArgumentCaptor<LeadAppealDO> captor = ArgumentCaptor.forClass(LeadAppealDO.class);
        verify(appealMapper).insert(captor.capture());
        LeadAppealDO appeal = captor.getValue();
        assertEquals(20L, appeal.getOwnerUserIdSnapshot());
        assertEquals(100L, appeal.getOwnerDeptIdSnapshot());
        assertEquals(100L, appeal.getReviewerDeptIdSnapshot());
        assertEquals("[30]", appeal.getReviewerUserIdsSnapshot());
        assertNull(appeal.getInvalidReasonSnapshot());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> processCaptor = ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(7L), processCaptor.capture());
        BpmProcessInstanceCreateReqDTO processRequest = processCaptor.getValue();
        assertEquals(List.of(30L), processRequest.getStartUserSelectAssignees().get("appealReview"));
        assertDoesNotThrow(() -> processRequest.getVariables().remove("appealId"));
    }

    @Test
    void submitWalksToNearestEnabledSupervisorWhenDirectLeaderUnavailable() {
        LeadDO lead = invalidLead(20L);
        when(leadMapper.selectByIdForUpdate(8L, 1L)).thenReturn(lead);
        when(appealMapper.selectBySubmissionIdempotencyKey("key-2")).thenReturn(null);
        when(appealMapper.selectLatestByLeadId(8L)).thenReturn(null);
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 100L));
        when(adminUserApi.getUser(30L)).thenReturn(user(30L, 100L));
        when(adminUserApi.getUser(40L)).thenReturn(user(40L, 10L));
        when(deptApi.getDept(100L)).thenReturn(dept(100L, 10L, 30L));
        when(deptApi.getDept(10L)).thenReturn(dept(10L, 0L, 40L));
        when(permissionApi.hasAnyPermissions(30L, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(40L, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        when(processInstanceApi.createProcessInstance(eq(7L), any())).thenReturn("process-2");
        doAnswer(invocation -> {
            ((LeadAppealDO) invocation.getArgument(0)).setId(55L);
            return 1;
        }).when(appealMapper).insert(any(LeadAppealDO.class));

        LeadAppealSubmitReqVO request = new LeadAppealSubmitReqVO();
        request.setReason("申诉原因");
        request.setIdempotencyKey("key-2");

        service.submit(8L, 7L, request);

        verify(processInstanceApi).createProcessInstance(eq(7L), argThat(req ->
                req.getStartUserSelectAssignees().get("appealReview").equals(List.of(40L))));
    }

    @Test
    void submitWalksToParentWhenDepartmentLeaderIsOwner() {
        LeadDO lead = invalidLead(20L);
        prepareFirstRoundSubmit(lead, "key-owner");
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 100L));
        when(adminUserApi.getUser(40L)).thenReturn(user(40L, 10L));
        when(deptApi.getDept(100L)).thenReturn(dept(100L, 10L, 20L));
        when(deptApi.getDept(10L)).thenReturn(dept(10L, 0L, 40L));
        when(permissionApi.hasAnyPermissions(40L, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        when(processInstanceApi.createProcessInstance(eq(7L), any())).thenReturn("process-owner");

        service.submit(8L, 7L, submitRequest("key-owner"));

        verify(processInstanceApi).createProcessInstance(eq(7L), argThat(req ->
                req.getStartUserSelectAssignees().get("appealReview").equals(List.of(40L))));
        verify(permissionApi, never()).hasAnyPermissions(20L, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER);
    }

    @Test
    void submitWalksToParentWhenDirectLeaderIsDisabled() {
        LeadDO lead = invalidLead(20L);
        prepareFirstRoundSubmit(lead, "key-disabled");
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 100L));
        AdminUserRespDTO disabledLeader = user(30L, 100L);
        disabledLeader.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(adminUserApi.getUser(30L)).thenReturn(disabledLeader);
        when(adminUserApi.getUser(40L)).thenReturn(user(40L, 10L));
        when(deptApi.getDept(100L)).thenReturn(dept(100L, 10L, 30L));
        when(deptApi.getDept(10L)).thenReturn(dept(10L, 0L, 40L));
        when(permissionApi.hasAnyPermissions(40L, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        when(processInstanceApi.createProcessInstance(eq(7L), any())).thenReturn("process-disabled");

        service.submit(8L, 7L, submitRequest("key-disabled"));

        verify(processInstanceApi).createProcessInstance(eq(7L), argThat(req ->
                req.getStartUserSelectAssignees().get("appealReview").equals(List.of(40L))));
        verify(permissionApi, never()).hasAnyPermissions(30L, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER);
    }

    @Test
    void submitFailsSafelyWhenDepartmentHierarchyContainsCycle() {
        LeadDO lead = invalidLead(20L);
        when(leadMapper.selectByIdForUpdate(8L, 1L)).thenReturn(lead);
        when(appealMapper.selectBySubmissionIdempotencyKey("key-cycle")).thenReturn(null);
        when(appealMapper.selectLatestByLeadId(8L)).thenReturn(null);
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 100L));
        when(deptApi.getDept(100L)).thenReturn(dept(100L, 10L, null));
        when(deptApi.getDept(10L)).thenReturn(dept(10L, 100L, null));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submit(8L, 7L, submitRequest("key-cycle")));

        assertEquals(1_900_003_039, error.getCode());
        verify(appealMapper, never()).insert(any(LeadAppealDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void submitMapsBpmStartupFailureToStableBusinessError() {
        LeadDO lead = invalidLead(20L);
        prepareFirstRoundSubmit(lead, "key-bpm-error");
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 100L));
        when(adminUserApi.getUser(30L)).thenReturn(user(30L, 100L));
        when(deptApi.getDept(100L)).thenReturn(dept(100L, 0L, 30L));
        when(permissionApi.hasAnyPermissions(30L, LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        when(processInstanceApi.createProcessInstance(eq(7L), any()))
                .thenThrow(new IllegalStateException("BPM startup failed"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submit(8L, 7L, submitRequest("key-bpm-error")));

        assertEquals(1_900_003_043, error.getCode());
        verify(appealMapper).insert(any(LeadAppealDO.class));
        verify(appealMapper, never()).updateById(any(LeadAppealDO.class));
        verifyNoInteractions(eventMapper, notifyEventPublisher);
    }

    @Test
    void canReviewUsesReviewerSnapshotAfterLeadOwnerChanges() {
        LeadAppealDO appeal = new LeadAppealDO();
        appeal.setReviewStage(LeadConstants.APPEAL_STAGE_SALES_MANAGER);
        appeal.setReviewerUserIdsSnapshot("[40]");
        when(adminUserApi.getUser(30L)).thenReturn(user(30L, 100L));
        when(adminUserApi.getUser(40L)).thenReturn(user(40L, 10L));

        assertFalse(invokeCanReview(appeal, 30L));
        assertTrue(invokeCanReview(appeal, 40L));
        verifyNoInteractions(deptApi, permissionApi);
    }

    @Test
    void canReviewAllowsOnlyNullLegacySnapshotAndEnabledBpmAssignee() {
        LeadAppealDO legacyAppeal = new LeadAppealDO();
        when(adminUserApi.getUser(40L)).thenReturn(user(40L, 10L));

        assertTrue(invokeCanReview(legacyAppeal, 40L));
        legacyAppeal.setReviewerUserIdsSnapshot("[]");
        assertFalse(invokeCanReview(legacyAppeal, 40L));
        legacyAppeal.setReviewerUserIdsSnapshot("not-json");
        assertFalse(invokeCanReview(legacyAppeal, 40L));

        AdminUserRespDTO disabledUser = user(50L, 10L);
        disabledUser.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(adminUserApi.getUser(50L)).thenReturn(disabledUser);
        legacyAppeal.setReviewerUserIdsSnapshot(null);
        assertFalse(invokeCanReview(legacyAppeal, 50L));
    }

    private void prepareFirstRoundSubmit(LeadDO lead, String idempotencyKey) {
        when(leadMapper.selectByIdForUpdate(8L, 1L)).thenReturn(lead);
        when(appealMapper.selectBySubmissionIdempotencyKey(idempotencyKey)).thenReturn(null);
        when(appealMapper.selectLatestByLeadId(8L)).thenReturn(null);
        doAnswer(invocation -> {
            ((LeadAppealDO) invocation.getArgument(0)).setId(55L);
            return 1;
        }).when(appealMapper).insert(any(LeadAppealDO.class));
    }

    private LeadAppealSubmitReqVO submitRequest(String idempotencyKey) {
        LeadAppealSubmitReqVO request = new LeadAppealSubmitReqVO();
        request.setReason("申诉原因");
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private boolean invokeCanReview(LeadAppealDO appeal, Long userId) {
        return (Boolean) ReflectionTestUtils.invokeMethod(service, "canReview", appeal, userId);
    }

    private LeadDO invalidLead(Long ownerUserId) {
        LeadDO lead = new LeadDO();
        lead.setId(8L);
        lead.setStatus(STATUS_INVALID);
        lead.setSourceUserId(7L);
        lead.setOwnerUserId(ownerUserId);
        return lead;
    }

    private AdminUserRespDTO user(Long id, Long deptId) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setDeptId(deptId);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return user;
    }

    private DeptRespDTO dept(Long id, Long parentId, Long leaderId) {
        DeptRespDTO dept = new DeptRespDTO();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setLeaderUserId(leaderId);
        dept.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return dept;
    }
}
