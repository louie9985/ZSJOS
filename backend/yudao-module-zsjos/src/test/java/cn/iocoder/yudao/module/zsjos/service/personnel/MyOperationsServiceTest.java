package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyOperationsServiceTest {
    @InjectMocks private MyOperationsService service;
    @Mock private PermissionApi permissionApi;
    @Mock private LeadAssignmentService leadAssignmentService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private LeadMapper leadMapper;

    private void allow() {
        when(permissionApi.hasAnyPermissions(10L, MyOperationsService.QUERY_PERMISSION)).thenReturn(true);
        when(leadAssignmentService.getActiveTargetUserIds(MyOperationsService.RELATION_SCENE, 10L)).thenReturn(Set.of(20L));
    }
    private LeadDO lead(Long provider) {
        LeadDO row = new LeadDO(); row.setId(1L); row.setProviderOwnerType("system_user"); row.setProviderOwnerId(provider);
        return row;
    }
    @Test void missingPermissionFailsBeforeRelationshipsOrData() {
        assertFalse(service.canReadMember(10L, 20L));
        assertThrows(ServiceException.class, () -> service.members(10L));
        verifyNoInteractions(leadAssignmentService, leadMapper, adminUserApi);
    }
    @Test void relationRemovalImmediatelyRevokesScope() {
        allow(); assertTrue(service.canReadMember(10L, 20L));
        when(leadAssignmentService.getActiveTargetUserIds(MyOperationsService.RELATION_SCENE, 10L)).thenReturn(Set.of());
        assertFalse(service.canReadMember(10L, 20L));
        assertThrows(ServiceException.class, () -> service.checkMember(10L, 20L));
    }
    @Test void providerAttributionIncludesAssistedEntryButNotCreatorOrSalesOwner() {
        allow(); LeadDO row = lead(20L); row.setSourceUserId(99L); row.setCreator("99"); row.setOwnerUserId(88L);
        assertTrue(service.canReadLead(row, 10L));
        row.setProviderOwnerId(99L); row.setCreator("20"); row.setSourceUserId(20L); row.setOwnerUserId(20L);
        assertFalse(service.canReadLead(row, 10L));
    }
    @Test void partnerIdentityAndInconsistentPartnerRowsAreExcluded() {
        LeadDO row = lead(20L); row.setProviderOwnerType("partner"); row.setPartnerOwnerUserIdSnapshot(20L);
        assertFalse(service.canReadLead(row, 10L));
        row.setProviderOwnerType("system_user"); row.setPartnerId(90L);
        assertFalse(service.canReadLead(row, 10L));
        verifyNoInteractions(permissionApi, leadAssignmentService);
    }
    @Test void forgedMemberIsRejectedBeforeLeadLookup() {
        allow(); assertThrows(ServiceException.class, () -> service.checkLead(10L, 21L, 1L));
        verifyNoInteractions(leadMapper);
    }
    @Test void selectedMemberCannotReadAnotherMembersLead() {
        allow(); when(leadAssignmentService.getActiveTargetUserIds(MyOperationsService.RELATION_SCENE, 10L)).thenReturn(Set.of(20L, 21L));
        when(leadMapper.selectById(1L)).thenReturn(lead(21L));
        assertThrows(ServiceException.class, () -> service.checkLead(10L, 20L, 1L));
    }
    @Test void missingLeadIsRejectedAndValidAttributedLeadIsReadable() {
        allow(); assertThrows(ServiceException.class, () -> service.checkLead(10L, 20L, 1L));
        when(leadMapper.selectById(1L)).thenReturn(lead(20L));
        assertDoesNotThrow(() -> service.checkLead(10L, 20L, 1L));
    }
    @Test @SuppressWarnings({"rawtypes", "unchecked"}) void membersCountOnlyCurrentSystemProviderLeads() {
        allow();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), LeadDO.class);
        when(adminUserApi.getUser(20L)).thenReturn(new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO()
                .setId(20L).setNickname("运营").setStatus(1));
        when(leadMapper.selectCount(org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(7L);
        var row = service.members(10L).getFirst();
        assertEquals(20L, row.getUserId()); assertEquals(7L, row.getLeadCount()); assertEquals(1, row.getAccountStatus());
        var capture = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(leadMapper).selectCount(capture.capture());
        String sql = capture.getValue().getSqlSegment();
        assertTrue(sql.contains("provider_owner_type =")); assertTrue(sql.contains("provider_owner_id ="));
        assertTrue(sql.contains("partner_id IS NULL")); assertFalse(sql.contains("source_user_id"));
    }

    @Test void controllerRequiresIndependentQueryPermission() {
        var annotation = cn.iocoder.yudao.module.zsjos.controller.admin.personnel.MyOperationsController.class
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
        assertEquals("@ss.hasPermission('zsjos:my-operations:query')", annotation.value());
    }
}
