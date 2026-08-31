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
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerActivateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerInvitationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerInvitationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_ACTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_INVITATION_STATUS_USED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_INVITATION_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerInvitationServiceImplTest {

    @InjectMocks private PartnerInvitationServiceImpl service;
    @Mock private PartnerInvitationMapper invitationMapper;
    @Mock private PartnerMapper partnerMapper;
    @Mock private PartnerOwnershipMapper ownershipMapper;
    @Mock private PartnerOwnershipLogMapper ownershipLogMapper;
    @Mock private PartnerAccountService partnerAccountService;
    @Mock private RoleApi roleApi;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;

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
        mockOperator();
        PartnerInvitationDO invitation = invitation();
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);
        when(partnerAccountService.create(any(), org.mockito.ArgumentMatchers.eq("13800138000"),
                org.mockito.ArgumentMatchers.eq("Password123"))).thenReturn(account());

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
    void activateExpiredInviteReturnsStableError() {
        PartnerInvitationDO invitation = invitation().setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(invitationMapper.selectActiveByMobileAndCodeForUpdate("13800138000", "ABCD1234"))
                .thenReturn(invitation);

        AssertUtils.assertServiceException(() -> service.activate(new PartnerActivateReqVO()
                .setMobile("13800138000").setPassword("Password123")
                .setConfirmPassword("Password123").setInviteCode("ABCD1234")), PARTNER_INVITATION_EXPIRED);
    }

    private void mockOperator() {
        when(roleApi.getRoleByCode("new_media_operator")).thenReturn(new RoleRespDTO()
                .setId(3L).setCode("new_media_operator").setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(permissionApi.getUserRoleIdListByRoleIds(Set.of(3L))).thenReturn(Set.of(9L));
        when(adminUserApi.getUser(9L)).thenReturn(new AdminUserRespDTO()
                .setId(9L).setNickname("运营A").setStatus(CommonStatusEnum.ENABLE.getStatus()));
    }

    private PartnerInvitationDO invitation() {
        return new PartnerInvitationDO().setId(1L).setInviteCode("ABCD1234").setName("张三")
                .setMobile("13800138000").setAssignedOperatorUserId(9L)
                .setAssignedOperatorNameSnapshot("运营A").setStatus(PARTNER_INVITATION_STATUS_ACTIVE)
                .setExpiresAt(LocalDateTime.now().plusDays(1)).setCreatedByUserId(1L).setVersion(0);
    }

    private PartnerAccountDO account() {
        return new PartnerAccountDO().setId(20L).setPartnerId(10L).setMobile("13800138000")
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
    }
}
