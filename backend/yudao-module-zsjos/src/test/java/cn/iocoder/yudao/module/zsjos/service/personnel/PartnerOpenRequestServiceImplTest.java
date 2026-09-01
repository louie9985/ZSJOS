package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessNodeStatusRespDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestCreateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOpenRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOpenRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerOpenRequestServiceImplTest {

    @InjectMocks private PartnerOpenRequestServiceImpl service;
    @Mock private PartnerOpenRequestMapper requestMapper;
    @Mock private PartnerInvitationService invitationService;
    @Mock private PartnerAccountService partnerAccountService;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private PermissionApi permissionApi;
    @Mock private RoleApi roleApi;
    @Mock private PartnerOpenRequestNotifyPublisher notifyPublisher;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(9L);
    }

    @Test
    void createStartsBpmWithReviewers() {
        mockUsersAndOperator();
        when(permissionApi.getEnabledUserIdsByPermission(PARTNER_OPEN_REQUEST_PERMISSION_REVIEW))
                .thenReturn(Set.of(30L, 20L));
        when(processInstanceApi.createProcessInstance(eq(7L), any())).thenReturn("process-1");
        doAnswer(invocation -> {
            PartnerOpenRequestDO row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(requestMapper).insert(any(PartnerOpenRequestDO.class));

        Long id = service.create(new PartnerOpenRequestCreateReqVO()
                .setPartnerName(" 张三 ")
                .setPartnerMobile(" 13800138000 ")
                .setAssignedEmployeeUserId(11L)
                .setIdempotencyKey("idem-1"), 7L);

        assertEquals(100L, id);
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> processCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(7L), processCaptor.capture());
        assertEquals(PARTNER_OPEN_REQUEST_PROCESS_DEFINITION_KEY, processCaptor.getValue().getProcessDefinitionKey());
        assertEquals(PARTNER_OPEN_REQUEST_BUSINESS_KEY_PREFIX + 100L, processCaptor.getValue().getBusinessKey());
        assertEquals(List.of(20L, 30L),
                processCaptor.getValue().getStartUserSelectAssignees().get(PARTNER_OPEN_REQUEST_TASK_DEFINITION_KEY));
        verify(requestMapper).insert(org.mockito.ArgumentMatchers.<PartnerOpenRequestDO>argThat(row ->
                "张三".equals(row.getPartnerName())
                        && "13800138000".equals(row.getPartnerMobile())
                        && "13800138000".equals(row.getActiveMobileKey())
                        && PARTNER_OPEN_REQUEST_STATUS_PENDING.equals(row.getStatus())));
        verify(notifyPublisher).publish(eq(PARTNER_OPEN_REQUEST_SCENE_SUBMITTED), eq(100L),
                eq("partner-open-request-submitted:100"), eq(7L), any());
    }

    @Test
    void createFailsWhenNoReviewer() {
        mockUsersAndOperator();
        when(permissionApi.getEnabledUserIdsByPermission(PARTNER_OPEN_REQUEST_PERMISSION_REVIEW)).thenReturn(Set.of());

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(new PartnerOpenRequestCreateReqVO()
                .setPartnerName("张三")
                .setPartnerMobile("13800138000")
                .setAssignedEmployeeUserId(11L), 7L));

        assertEquals(PARTNER_OPEN_REQUEST_PROCESS_UNAVAILABLE.getCode(), error.getCode());
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
    }

    @Test
    void createRejectsDuplicateActiveMobile() {
        when(requestMapper.selectActiveByMobileForUpdate("13800138000", 9L)).thenReturn(new PartnerOpenRequestDO());

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(new PartnerOpenRequestCreateReqVO()
                .setPartnerName("张三")
                .setPartnerMobile("13800138000")
                .setAssignedEmployeeUserId(11L), 7L));

        assertEquals(PARTNER_OPEN_REQUEST_DUPLICATE.getCode(), error.getCode());
    }

    @Test
    void approveProcessCreatesInvitationAndMarksOpened() {
        PartnerOpenRequestDO row = request("process-1");
        when(requestMapper.selectByProcessInstanceId("process-1")).thenReturn(row);
        when(requestMapper.selectByIdForUpdate(100L, 9L)).thenReturn(row);
        when(processTaskApi.getProcessNodeStatuses("process-1", Set.of(PARTNER_OPEN_REQUEST_TASK_DEFINITION_KEY)))
                .thenReturn(List.of(new BpmProcessNodeStatusRespDTO().setReviewerUserId(20L)));
        when(invitationService.create(any(PartnerInvitationCreateCommand.class), eq(20L)))
                .thenReturn(new PartnerInvitationRespVO().setId(501L).setInviteCode("ABCD1234")
                        .setExpiresAt(LocalDateTime.now().plusDays(7)));

        service.handleProcessResult("process-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "同意");

        verify(requestMapper).updateById(org.mockito.ArgumentMatchers.<PartnerOpenRequestDO>argThat(updated ->
                PARTNER_OPEN_REQUEST_STATUS_OPENED.equals(updated.getStatus())
                        && updated.getActiveMobileKey() == null
                        && Long.valueOf(501L).equals(updated.getInvitationId())
                        && "ABCD1234".equals(updated.getInviteCodeSnapshot())));
        verify(notifyPublisher).publish(eq(PARTNER_OPEN_REQUEST_SCENE_OPENED), eq(100L),
                eq("partner-open-request-opened:100"), eq(20L), any());
    }

    @Test
    void approveProcessMarksOpenFailedWhenAccountAlreadyExists() {
        PartnerOpenRequestDO row = request("process-1");
        when(requestMapper.selectByProcessInstanceId("process-1")).thenReturn(row);
        when(requestMapper.selectByIdForUpdate(100L, 9L)).thenReturn(row);
        when(processTaskApi.getProcessNodeStatuses("process-1", Set.of(PARTNER_OPEN_REQUEST_TASK_DEFINITION_KEY)))
                .thenReturn(List.of(new BpmProcessNodeStatusRespDTO().setReviewerUserId(20L)));
        when(partnerAccountService.getByMobile("13800138000")).thenReturn(new PartnerAccountDO());

        service.handleProcessResult("process-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "同意");

        verify(invitationService, never()).create(any(PartnerInvitationCreateCommand.class), anyLong());
        verify(requestMapper).updateById(org.mockito.ArgumentMatchers.<PartnerOpenRequestDO>argThat(updated ->
                PARTNER_OPEN_REQUEST_STATUS_OPEN_FAILED.equals(updated.getStatus())
                        && updated.getActiveMobileKey() == null
                        && "手机号已存在兼职账号".equals(updated.getFailureReason())));
    }

    @Test
    void rejectProcessMarksRejectedAndDoesNotCreateInvitation() {
        PartnerOpenRequestDO row = request("process-1");
        when(requestMapper.selectByProcessInstanceId("process-1")).thenReturn(row);
        when(requestMapper.selectByIdForUpdate(100L, 9L)).thenReturn(row);
        when(processTaskApi.getProcessNodeStatuses("process-1", Set.of(PARTNER_OPEN_REQUEST_TASK_DEFINITION_KEY)))
                .thenReturn(List.of(new BpmProcessNodeStatusRespDTO().setReviewerUserId(20L)));

        service.handleProcessResult("process-1", BpmProcessInstanceStatusEnum.REJECT.getStatus(), "资料不完整");

        verify(invitationService, never()).create(any(PartnerInvitationCreateCommand.class), anyLong());
        verify(requestMapper).updateById(org.mockito.ArgumentMatchers.<PartnerOpenRequestDO>argThat(updated ->
                PARTNER_OPEN_REQUEST_STATUS_REJECTED.equals(updated.getStatus())
                        && updated.getActiveMobileKey() == null
                        && "资料不完整".equals(updated.getReviewReason())));
    }

    @Test
    void cancelRequiresApplicantAndPendingState() {
        PartnerOpenRequestDO row = request("process-1");
        when(requestMapper.selectByIdForUpdate(100L, 9L)).thenReturn(row);
        when(requestMapper.updateById(org.mockito.ArgumentMatchers.<PartnerOpenRequestDO>any())).thenReturn(1);

        service.cancel(100L, 7L);

        verify(processInstanceApi).cancelProcessInstanceByStartUser(7L, "process-1", "发起人撤回代开通兼职账号申请");
        verify(requestMapper).updateById(org.mockito.ArgumentMatchers.<PartnerOpenRequestDO>argThat(updated ->
                PARTNER_OPEN_REQUEST_STATUS_CANCELLED.equals(updated.getStatus())
                        && updated.getActiveMobileKey() == null));
    }

    private void mockUsersAndOperator() {
        when(roleApi.getRoleByCode("new_media_operator")).thenReturn(new RoleRespDTO()
                .setId(3L).setCode("new_media_operator").setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(permissionApi.getUserRoleIdListByRoleIds(Set.of(3L))).thenReturn(Set.of(11L));
        when(adminUserApi.getUser(11L)).thenReturn(user(11L, "运营A", 101L));
        when(adminUserApi.getUser(7L)).thenReturn(user(7L, "申请人", 102L));
        when(deptApi.getDept(101L)).thenReturn(new DeptRespDTO().setId(101L).setName("运营部"));
        when(deptApi.getDept(102L)).thenReturn(new DeptRespDTO().setId(102L).setName("业务部"));
    }

    private AdminUserRespDTO user(Long id, String name, Long deptId) {
        return new AdminUserRespDTO().setId(id).setNickname(name).setDeptId(deptId)
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    private PartnerOpenRequestDO request(String processInstanceId) {
        return new PartnerOpenRequestDO()
                .setId(100L)
                .setRequestNo("POR001")
                .setPartnerName("张三")
                .setPartnerMobile("13800138000")
                .setActiveMobileKey("13800138000")
                .setAssignedEmployeeUserId(11L)
                .setAssignedEmployeeNameSnapshot("运营A")
                .setApplicantUserId(7L)
                .setApplicantNameSnapshot("申请人")
                .setStatus(PARTNER_OPEN_REQUEST_STATUS_PENDING)
                .setProcessInstanceId(processInstanceId)
                .setSubmittedAt(LocalDateTime.now())
                .setVersion(0);
    }
}
