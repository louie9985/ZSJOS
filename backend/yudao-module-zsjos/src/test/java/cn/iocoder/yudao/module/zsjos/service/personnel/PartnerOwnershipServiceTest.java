package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOwnershipUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerOwnershipServiceTest {
    @InjectMocks private PartnerOwnershipService service;
    @Mock private PartnerOwnershipMapper ownershipMapper;
    @Mock private PartnerOwnershipLogMapper logMapper;
    @Mock private PartnerMapper partnerMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;

    @Test
    void permissionLossImmediatelyInvalidatesRetainedOwnership() {
        when(ownershipMapper.selectByPartnerId(10L)).thenReturn(ownership(20L));
        when(permissionApi.hasAnyPermissions(20L, PartnerOwnershipService.MANAGE_PERMISSION)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(20L, PartnerOwnershipService.QUERY_PERMISSION)).thenReturn(false);

        assertFalse(service.canRead(20L, 10L));
        verify(ownershipMapper, never()).deleteByIdAndVersion(anyLong(), anyInt(), anyLong());
    }

    @Test
    void enabledPermissionHolderCanReadCurrentPartner() {
        when(ownershipMapper.selectByPartnerId(10L)).thenReturn(ownership(20L));
        when(permissionApi.hasAnyPermissions(20L, PartnerOwnershipService.MANAGE_PERMISSION)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(20L, PartnerOwnershipService.QUERY_PERMISSION)).thenReturn(true);
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, "Owner"));

        assertTrue(service.canRead(20L, 10L));
        assertFalse(service.canRead(21L, 10L));
    }

    @Test
    void managerCanReadAnyExistingTenantPartner() {
        when(permissionApi.hasAnyPermissions(20L, PartnerOwnershipService.MANAGE_PERMISSION)).thenReturn(true);
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, "Manager"));
        when(partnerMapper.selectById(10L)).thenReturn(new PartnerDO().setId(10L));

        assertTrue(service.canRead(20L, 10L));
        verifyNoInteractions(ownershipMapper);
    }

    @Test
    void nullEmployeeUserIdCannotResolveSystemPermissions() {
        assertFalse(service.canQuery(null));
        assertFalse(service.canManage(null));
        assertFalse(service.canRead(null, 10L));
        verifyNoInteractions(permissionApi, adminUserApi, ownershipMapper, partnerMapper);
    }

    @Test
    void assignmentRequiresPermissionAndWritesAudit() {
        when(partnerMapper.selectById(10L)).thenReturn(new PartnerDO().setId(10L));
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, "Owner"));
        when(permissionApi.hasAnyPermissions(20L, PartnerOwnershipService.QUERY_PERMISSION)).thenReturn(true);
        doAnswer(invocation -> { invocation.<PartnerOwnershipDO>getArgument(0).setId(1L); return 1; })
                .when(ownershipMapper).insert(any(PartnerOwnershipDO.class));
        PartnerOwnershipUpdateReqVO request = new PartnerOwnershipUpdateReqVO();
        request.setAssignedUserId(20L); request.setReason("initial owner");

        service.update(10L, request, 99L);

        verify(ownershipMapper).insert(argThat((PartnerOwnershipDO value) -> value.getEmployeeUserId().equals(20L)
                && value.getEmployeeNameSnapshot().equals("Owner")));
        verify(logMapper).insert(argThat((cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipLogDO log) -> "assign".equals(log.getActionType())
                && log.getOperatorUserId().equals(99L) && "initial owner".equals(log.getReason())));
    }

    private PartnerOwnershipDO ownership(Long userId) {
        return new PartnerOwnershipDO().setId(1L).setPartnerId(10L).setEmployeeUserId(userId)
                .setEmployeeNameSnapshot("Owner").setVersion(0);
    }

    private AdminUserRespDTO user(Long id, String name) {
        return new AdminUserRespDTO().setId(id).setNickname(name).setStatus(CommonStatusEnum.ENABLE.getStatus());
    }
}
