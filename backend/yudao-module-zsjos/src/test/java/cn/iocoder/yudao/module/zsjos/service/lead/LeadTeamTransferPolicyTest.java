package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadTeamTransferPolicyTest {
    @InjectMocks private LeadTeamTransferPolicy policy;
    @Mock private LeadSubmissionIdentityService identityService;
    @Mock private LeadObjectPermissionService permissionService;
    @Mock private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper historyMapper;
    @Test void educationTransferRequiresScopeEligibilityAndConfiguredCapability() {
        var lead = new LeadDO().setOwnerIdentity("education").setSourceType("education_self_sourced");
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        when(identityService.isEligibleEducationOwner(20L)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L, "zsjos:lead-follow-up:create")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L, "zsjos:lead:qualify")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L, "zsjos:sales-order:create")).thenReturn(true);
        assertDoesNotThrow(() -> policy.requireTarget(lead, 20L, 10L));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> policy.requireTarget(lead, 30L, 10L));
        when(permissionApi.hasAnyPermissions(20L, "zsjos:lead:qualify")).thenReturn(false);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> policy.requireTarget(lead, 20L, 10L));
    }
    @Test void recycledSpecifiedEducationUsesRecordedIdentity() {
        var lead = new LeadDO().setId(1L).setAssignmentStatus("recycle_pending").setSourceType("partner").setDispatchMode("specified").setRecycleSourceOwnerUserId(20L);
        var history = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        history.setActionType("recycle"); history.setFromOwnerUserId(20L); history.setOwnerIdentitySnapshot("education");
        when(historyMapper.selectByLeadId(1L)).thenReturn(java.util.List.of(history));
        assertTrue(policy.isEducation(lead));
    }
    @Test void legacyRecycleUsesLastProvenOwnerWithoutCrossingNewOwnership() {
        var lead = new LeadDO().setId(1L).setRecycleSourceOwnerUserId(20L).setAssignmentStatus("recycle_pending");
        var recycle = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        recycle.setActionType("recycle"); recycle.setFromOwnerUserId(20L);
        var owner = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        owner.setActionType("accept"); owner.setToOwnerUserId(20L); owner.setOwnerIdentitySnapshot("education");
        when(historyMapper.selectByLeadId(1L)).thenReturn(java.util.List.of(recycle,owner));
        assertEquals("education",policy.resolveIdentity(lead));
        owner.setToOwnerUserId(30L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
    }
    @Test void missingIdentityNeverDefaultsToSales() {
        var lead = new LeadDO().setId(1L).setAssignmentStatus("recycle_pending");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
    }
    @Test void recycleCannotBorrowAcrossMissingOwnerOrReleaseBoundary() {
        var lead = new LeadDO().setId(1L).setRecycleSourceOwnerUserId(20L).setAssignmentStatus("recycle_pending");
        var recycle = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        recycle.setActionType("recycle"); recycle.setOwnerIdentitySnapshot("education");
        when(historyMapper.selectByLeadId(1L)).thenReturn(java.util.List.of(recycle));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
        recycle.setFromOwnerUserId(21L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
        recycle.setFromOwnerUserId(20L);recycle.setOwnerIdentitySnapshot(null);
        var release = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        release.setActionType("release_to_claim_pool");
        var old = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        old.setActionType("accept");old.setToOwnerUserId(20L);old.setOwnerIdentitySnapshot("education");
        when(historyMapper.selectByLeadId(1L)).thenReturn(java.util.List.of(recycle,release,old));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
        release.setActionType("recycle");release.setFromOwnerUserId(20L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
    }
    @Test void educationSourcesRequireCompleteEvidence() {
        var lead=new LeadDO().setSourceType("education_self_sourced");
        assertTrue(EducationOwnershipPolicy.supports(lead));
        lead.setSourceType("internal_new_media").setSubmissionSourceType("education_self_sourced").setDispatchMode("self");
        assertTrue(EducationOwnershipPolicy.supports(lead));
        lead.setDispatchMode("specified");assertFalse(EducationOwnershipPolicy.supports(lead));
        lead.setSourceType("partner");assertTrue(EducationOwnershipPolicy.supports(lead));
        lead.setSourceType("sales_self_sourced");assertFalse(EducationOwnershipPolicy.supports(lead));
    }
}
