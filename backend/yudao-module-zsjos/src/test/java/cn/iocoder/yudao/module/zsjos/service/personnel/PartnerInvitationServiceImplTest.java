package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.util.AssertUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStudentInvitationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerActivateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerInvitationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipLogDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerInvitationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_SCENE_STUDENT;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_ACTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_USED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_INVITATION_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerInvitationServiceImplTest {

    @InjectMocks private PartnerInvitationServiceImpl service;
    @Mock private PartnerInvitationMapper invitationMapper;
    @Mock private PartnerMapper partnerMapper;
    @Mock private PartnerOwnershipMapper ownershipMapper;
    @Mock private PartnerOwnershipLogMapper ownershipLogMapper;
    @Mock private PartnerAccountService partnerAccountService;
    @Mock private PartnerStudentLinkService partnerStudentLinkService;
    @Mock private PersonMapper personMapper;
    @Mock private ServiceRelationMapper serviceRelationMapper;
    @Mock private RoleApi roleApi;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PlatformTransactionManager transactionManager;

    @Test
    void createVoidsOldActiveInvitationAndReturnsGeneratedCode() {
        mockOperator();
        PartnerInvitationCreateReqVO reqVO = new PartnerInvitationCreateReqVO()
                .setName("张三").setMobile(" 13800138000 ").setAssignedOperatorUserId(9L);

        var result = service.create(reqVO, 1L);

        assertTrue(result.getInviteCode().matches("^[A-Z]{4}\\d{4}$"));
        assertEquals("张三", result.getName());
        verify(invitationMapper).voidActiveByMobile(org.mockito.ArgumentMatchers.eq("13800138000"), any());
        verify(invitationMapper).insert(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(row ->
                "13800138000".equals(row.getMobile())
                        && PARTNER_INVITATION_STATUS_ACTIVE.equals(row.getStatus())
                        && row.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6))));
    }

    @Test
    void activateCreatesPartnerAccountOwnershipAndConsumesInvitation() {
        stubTransaction();
        mockOperator();
        PartnerInvitationDO invitation = invitation();
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);
        when(partnerAccountService.create(any(), org.mockito.ArgumentMatchers.eq("13800138000"),
                org.mockito.ArgumentMatchers.eq("Password123"))).thenReturn(account());
        when(invitationMapper.updateById(any(PartnerInvitationDO.class))).thenReturn(1);

        PartnerAccountDO account = service.activate(new PartnerActivateReqVO()
                .setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("abcd1234"));

        assertEquals(20L, account.getId());
        verify(partnerMapper).insert(org.mockito.ArgumentMatchers.<PartnerDO>argThat(row ->
                row.getPartnerNo().matches("^PT\\d{18}$") && "张三".equals(row.getName())));
        verify(ownershipMapper).insert(any(PartnerOwnershipDO.class));
        verify(ownershipLogMapper).insert(any(PartnerOwnershipLogDO.class));
        verify(invitationMapper).updateById(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(row ->
                PARTNER_INVITATION_STATUS_USED.equals(row.getStatus()) && row.getUsedAt() != null));
    }

    @Test
    void createStudentInvitationKeepsOriginalSnapshotAndEditableRegistrationProfile() {
        PersonDO student = new PersonDO().setId(88L).setName("学员原姓名").setMobile("13900000000");
        ServiceRelationDO relation = new ServiceRelationDO().setId(66L).setPersonId(88L)
                .setContentDirectorUserId(7L).setOperatorUserId(9L).setOrderId(55L);
        when(personMapper.selectById(88L)).thenReturn(student);
        when(serviceRelationMapper.selectActiveByContentDirectorAndPerson(7L, 88L)).thenReturn(List.of(relation));

        var result = service.createStudentInvitation(new PartnerStudentInvitationCreateReqVO()
                .setStudentPersonId(88L).setName("兼职注册名").setMobile(" 13800138000 "), 7L);

        assertEquals(PARTNER_INVITATION_SCENE_STUDENT, result.getInvitationScene());
        assertEquals("学员原姓名", result.getStudentNameSnapshot());
        assertEquals("13900000000", result.getStudentMobileSnapshot());
        assertEquals("兼职注册名", result.getName());
        assertEquals("13800138000", result.getMobile());
        verify(invitationMapper).insert(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(row ->
                row.getAssignedOperatorUserId() == null && row.getInitiatedByDirectorUserId().equals(7L)
                        && row.getAssignmentContextJson().contains("66")));
        verify(personMapper, never()).updateById(any(PersonDO.class));
    }

    @Test
    void activateStudentInvitationBindsStudentWithoutCreatingOperatorOwnership() {
        stubTransaction();
        PartnerInvitationDO invitation = invitation()
                .setInvitationScene(PARTNER_INVITATION_SCENE_STUDENT)
                .setStudentPersonId(88L)
                .setStudentNameSnapshot("学员原姓名")
                .setStudentMobileSnapshot("13900000000")
                .setInitiatedByDirectorUserId(7L)
                .setAssignedOperatorUserId(null)
                .setAssignedOperatorNameSnapshot(null)
                .setName("兼职注册名");
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);
        when(partnerAccountService.create(any(), org.mockito.ArgumentMatchers.eq("13800138000"),
                org.mockito.ArgumentMatchers.eq("Password123"))).thenReturn(account());
        when(invitationMapper.updateById(any(PartnerInvitationDO.class))).thenReturn(1);

        service.activate(new PartnerActivateReqVO().setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("ABCD1234"));

        verify(partnerMapper).insert(org.mockito.ArgumentMatchers.<PartnerDO>argThat(row ->
                "兼职注册名".equals(row.getName()) && "13800138000".equals(row.getMobile())));
        verify(partnerStudentLinkService).bind(any(), org.mockito.ArgumentMatchers.eq(88L),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(7L));
        verify(ownershipMapper, never()).insert(any(PartnerOwnershipDO.class));
        verify(ownershipLogMapper, never()).insert(any(PartnerOwnershipLogDO.class));
        verify(personMapper, never()).updateById(any(PersonDO.class));
    }

    @Test
    void activateExpiredInviteReturnsStableError() {
        stubTransaction();
        PartnerInvitationDO invitation = invitation().setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);
        when(invitationMapper.updateById(invitation)).thenReturn(1);

        AssertUtils.assertServiceException(() -> service.activate(new PartnerActivateReqVO()
                .setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("ABCD1234")), PARTNER_INVITATION_EXPIRED);
        verify(invitationMapper).updateById(org.mockito.ArgumentMatchers.<PartnerInvitationDO>argThat(row ->
                cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_EXPIRED
                        .equals(row.getStatus()) && row.getVoidedAt() != null));
        verify(transactionManager).commit(any());
    }

    private void mockOperator() {
        when(roleApi.getRoleByCode("new_media_operator")).thenReturn(new RoleRespDTO()
                .setId(3L).setCode("new_media_operator").setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(permissionApi.getUserRoleIdListByRoleIds(Set.of(3L))).thenReturn(Set.of(9L));
        when(adminUserApi.getUser(9L)).thenReturn(new AdminUserRespDTO()
                .setId(9L).setNickname("运营A").setStatus(CommonStatusEnum.ENABLE.getStatus()));
    }

    private void stubTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private PartnerInvitationDO invitation() {
        return new PartnerInvitationDO().setId(1L).setInviteCode("ABCD1234").setName("张三")
                .setInvitationScene("GENERAL")
                .setMobile("13800138000").setAssignedOperatorUserId(9L)
                .setAssignedOperatorNameSnapshot("运营A").setStatus(PARTNER_INVITATION_STATUS_ACTIVE)
                .setExpiresAt(LocalDateTime.now().plusDays(1)).setCreatedByUserId(1L).setVersion(0);
    }

    private PartnerAccountDO account() {
        return new PartnerAccountDO().setId(20L).setPartnerId(10L).setMobile("13800138000")
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
    }
}
