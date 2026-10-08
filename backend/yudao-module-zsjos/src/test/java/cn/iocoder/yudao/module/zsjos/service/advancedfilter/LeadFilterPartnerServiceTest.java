package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadIdentityMaskingService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_OWNED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeadFilterPartnerServiceTest {
    private final LeadMapper leads = mock(LeadMapper.class);
    private final PartnerMapper partners = mock(PartnerMapper.class);
    private final LeadObjectPermissionService permissions = mock(LeadObjectPermissionService.class);
    private final SecurityFrameworkService security = mock(SecurityFrameworkService.class);
    private final LeadFilterPartnerService service = new LeadFilterPartnerService();

    LeadFilterPartnerServiceTest() {
        ReflectionTestUtils.setField(service, "leadMapper", leads);
        ReflectionTestUtils.setField(service, "partnerMapper", partners);
        ReflectionTestUtils.setField(service, "permissions", permissions);
        ReflectionTestUtils.setField(service, "security", security);
        ReflectionTestUtils.setField(service, "masking", new LeadIdentityMaskingService(permissions));
    }

    @Test void emptyScopeDoesNotReadPartnerProfiles() {
        when(permissions.getRelatedAndManagedUserIds(50L)).thenReturn(Set.of(50L));
        when(leads.selectPartnerSubmitterCandidates(List.of(), List.of(), false)).thenReturn(List.of());
        assertEquals(List.of(), service.options(50L));
        verifyNoInteractions(partners);
    }

    @Test void ownedScopeUsesCanonicalPartnerAndMasksSalesCounterparty() {
        when(permissions.getRelatedAndManagedUserIds(50L)).thenReturn(Set.of(50L, 60L));
        when(security.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(true);
        LeadDO row = new LeadDO();
        row.setId(7L); row.setPartnerId(48L); row.setProviderOwnerType("partner");
        row.setProviderOwnerId(48L); row.setOwnerUserId(50L); row.setAssignmentStatus("owned");
        row.setDispatchMode("specified");
        when(leads.selectPartnerSubmitterCandidates(List.of(), List.of(50L,60L), false))
                .thenReturn(List.of(row, row));
        PartnerDO partner = new PartnerDO(); partner.setId(48L); partner.setName("高珊");
        when(partners.selectListByIds(Set.of(48L))).thenReturn(List.of(partner));
        var result = service.options(50L);
        assertEquals(1, result.size());
        assertEquals("48", result.get(0).value());
        assertEquals("高*", result.get(0).label());
    }

    @Test void tenantReadAllReusesConfiguredBypassAndUnmaskedNames() {
        when(permissions.hasTenantReadAll(9L)).thenReturn(true);
        LeadDO row = new LeadDO(); row.setId(1L); row.setPartnerId(48L);
        row.setProviderOwnerType("partner"); row.setProviderOwnerId(48L);
        when(leads.selectPartnerSubmitterCandidates(List.of(), List.of(), true)).thenReturn(List.of(row));
        when(permissions.canViewUnmaskedIdentity(9L, row)).thenReturn(true);
        PartnerDO partner = new PartnerDO(); partner.setId(48L); partner.setName("高珊");
        when(partners.selectListByIds(Set.of(48L))).thenReturn(List.of(partner));
        assertEquals("高珊", service.options(9L).get(0).label());
        verify(permissions, never()).getRelatedAndManagedUserIds(any());
    }
}
