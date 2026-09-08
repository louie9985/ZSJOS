package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeadIdentityMaskingServiceTest {

    @Test
    void masksAllCounterpartyReferencesForOwner() {
        LeadDO lead = lead();
        LeadObjectPermissionService permission = mock(LeadObjectPermissionService.class);
        when(permission.canViewUnmaskedIdentity(20L, lead)).thenReturn(false);
        LeadIdentityMaskingService service = new LeadIdentityMaskingService(permission);
        LeadIdentityMaskingService.LeadIdentityViewContext context = service.resolve(20L, lead);
        Map<Long, AdminUserRespDTO> users = Map.of(10L, user(10L, "李风"), 20L, user(20L, "王五"));

        assertTrue(context.counterpartyMaskingEnabled());
        assertEquals("李*", service.employeeName(context, users, 10L, LeadIdentityRole.SOURCE));
        assertEquals("李*", service.employeeName(context, users, 10L, LeadIdentityRole.OPERATOR));
        assertEquals("李*", service.employeeName(context, users, 10L, LeadIdentityRole.OWNER));
        assertEquals("王五", service.employeeName(context, users, 20L, LeadIdentityRole.OPERATOR));
    }

    @Test
    void submitterOnlySeesOwnerMaskedAndThirdPartySeesBothFull() {
        LeadDO lead = lead();
        LeadObjectPermissionService permission = mock(LeadObjectPermissionService.class);
        when(permission.canViewUnmaskedIdentity(10L, lead)).thenReturn(false);
        when(permission.canViewUnmaskedIdentity(99L, lead)).thenReturn(false);
        LeadIdentityMaskingService service = new LeadIdentityMaskingService(permission);
        Map<Long, AdminUserRespDTO> users = Map.of(
                10L, user(10L, "李风"), 20L, user(20L, "王五"), 30L, user(30L, "赵六"));

        var submitter = service.resolve(10L, lead);
        assertEquals("李风", service.employeeName(submitter, users, 10L, LeadIdentityRole.SOURCE));
        assertEquals("王*", service.employeeName(submitter, users, 20L, LeadIdentityRole.OWNER));
        assertEquals("赵六", service.employeeName(submitter, users, 30L, LeadIdentityRole.OPERATOR));

        var thirdParty = service.resolve(99L, lead);
        assertTrue(thirdParty.counterpartyMaskingEnabled());
        assertEquals("李风", service.employeeName(thirdParty, users, 10L, LeadIdentityRole.SOURCE));
        assertEquals("王五", service.employeeName(thirdParty, users, 20L, LeadIdentityRole.OWNER));
    }

    @Test
    void partnerAccountIdNeverMatchesEmployeeIdentity() {
        LeadDO lead = lead();
        lead.setProviderOwnerType("partner");
        lead.setProviderOwnerId(30L);
        lead.setSourceUserId(30L);
        LeadObjectPermissionService permission = mock(LeadObjectPermissionService.class);
        when(permission.canViewUnmaskedIdentity(20L, lead)).thenReturn(false);
        LeadIdentityMaskingService service = new LeadIdentityMaskingService(permission);
        Map<Long, AdminUserRespDTO> users = Map.of(30L, user(30L, "碰撞员工"));

        var context = service.resolve(20L, lead);

        assertEquals(LeadIdentityMaskingService.SourceIdentityType.PARTNER, context.sourceIdentityType());
        assertEquals(null, context.sourceEmployeeUserId());
        assertEquals(30L, context.sourcePartnerId());
        assertEquals("碰撞员工", service.employeeName(context, users, 30L, LeadIdentityRole.OPERATOR));
        assertEquals("兼****", service.partnerName(context, "兼职提交人"));
    }

    @Test
    void formatsSystemUnknownAndBlankEmployeeNames() {
        LeadIdentityMaskingService service = new LeadIdentityMaskingService(mock(LeadObjectPermissionService.class));
        var context = new LeadIdentityMaskingService.LeadIdentityViewContext(99L,
                LeadIdentityMaskingService.SourceIdentityType.NONE, null, null, 20L,
                false, false, false, false);
        Map<Long, AdminUserRespDTO> users = Map.of(30L, user(30L, " "));

        assertEquals("系统", service.employeeName(context, users, 0L, LeadIdentityRole.OPERATOR));
        assertEquals("未知账号", service.employeeName(context, users, 31L, LeadIdentityRole.OPERATOR));
        assertEquals("未知账号", service.employeeName(context, users, 30L, LeadIdentityRole.OPERATOR));
    }

    @Test
    void preservesFullNamesForAuthorizedManagerAndSpecifiedDispatch() {
        LeadDO lead = lead();
        LeadObjectPermissionService permission = mock(LeadObjectPermissionService.class);
        when(permission.canViewUnmaskedIdentity(99L, lead)).thenReturn(true);
        LeadIdentityMaskingService service = new LeadIdentityMaskingService(permission);
        Map<Long, AdminUserRespDTO> users = Map.of(10L, user(10L, "李风"));

        assertFalse(service.resolve(99L, lead).counterpartyMaskingEnabled());
        assertEquals("李风", service.employeeName(service.resolve(99L, lead), users, 10L, LeadIdentityRole.OPERATOR));
        lead.setDispatchMode("specified");
        when(permission.canViewUnmaskedIdentity(20L, lead)).thenReturn(false);
        assertFalse(service.resolve(20L, lead).counterpartyMaskingEnabled());
    }

    private static LeadDO lead() {
        return new LeadDO().setSourceUserId(10L).setOwnerUserId(20L)
                .setProviderOwnerType("system_user").setProviderOwnerId(10L)
                .setDispatchMode("auto").setAssignmentStatus("owned");
    }

    private static AdminUserRespDTO user(Long id, String nickname) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }
}
