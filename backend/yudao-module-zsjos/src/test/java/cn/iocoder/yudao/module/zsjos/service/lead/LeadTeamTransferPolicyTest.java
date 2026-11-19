package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadTeamTransferPolicyTest {
    @InjectMocks private LeadTeamTransferPolicy policy;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper historyMapper;
    @Test void recycledLeadUsesRecordedSalesIdentity() {
        var lead = new LeadDO().setId(1L).setAssignmentStatus("recycle_pending").setSourceType("partner").setDispatchMode("specified").setRecycleSourceOwnerUserId(20L);
        var history = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        history.setActionType("recycle"); history.setFromOwnerUserId(20L); history.setOwnerIdentitySnapshot("sales");
        when(historyMapper.selectByLeadId(1L)).thenReturn(java.util.List.of(history));
        assertEquals("sales", policy.resolveIdentity(lead));
        history.setOwnerIdentitySnapshot("education");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
    }
    @Test void retiredEducationIdentityIsUnknown() {
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                ()->policy.resolveIdentity(new LeadDO().setOwnerIdentity("education")));
        assertEquals("sales", policy.resolveIdentity(new LeadDO().setOwnerIdentity("sales")));
    }
    @Test void legacyRecycleUsesLastProvenOwnerWithoutCrossingNewOwnership() {
        var lead = new LeadDO().setId(1L).setRecycleSourceOwnerUserId(20L).setAssignmentStatus("recycle_pending");
        var recycle = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        recycle.setActionType("recycle"); recycle.setFromOwnerUserId(20L);
        var owner = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        owner.setActionType("accept"); owner.setToOwnerUserId(20L); owner.setOwnerIdentitySnapshot("sales");
        when(historyMapper.selectByLeadId(1L)).thenReturn(java.util.List.of(recycle,owner));
        assertEquals("sales",policy.resolveIdentity(lead));
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
        recycle.setActionType("recycle"); recycle.setOwnerIdentitySnapshot("sales");
        when(historyMapper.selectByLeadId(1L)).thenReturn(java.util.List.of(recycle));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
        recycle.setFromOwnerUserId(21L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
        recycle.setFromOwnerUserId(20L);recycle.setOwnerIdentitySnapshot(null);
        var release = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        release.setActionType("release_to_claim_pool");
        var old = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        old.setActionType("accept");old.setToOwnerUserId(20L);old.setOwnerIdentitySnapshot("sales");
        when(historyMapper.selectByLeadId(1L)).thenReturn(java.util.List.of(recycle,release,old));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
        release.setActionType("recycle");release.setFromOwnerUserId(20L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->policy.resolveIdentity(lead));
    }
}
