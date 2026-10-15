package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadComplaintManagedScopeTest {
    @InjectMocks private LeadComplaintService service;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadObjectPermissionService permissionService;
    @Mock private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    @Test void historicalEmployeeAndCurrentOwnerAreIndependentScopeRelationships() {
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        var row = new LeadComplaintDO(); row.setLeadId(1L); row.setSalesUserId(20L);
        assertDoesNotThrow(() -> service.checkManagedComplaint(row, 10L));
        row.setSalesUserId(30L);
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setOwnerUserId(20L));
        assertDoesNotThrow(() -> service.checkManagedComplaint(row, 10L));
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setOwnerUserId(30L));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.checkManagedComplaint(row, 10L));
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO()); row.setSalesUserId(null);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.checkManagedComplaint(row, 10L));
    }
    @Test void tenantReadAllKeepsAdministratorAccess() {
        when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
        assertDoesNotThrow(() -> service.checkManagedComplaint(new LeadComplaintDO(), 1L));
        verifyNoInteractions(leadMapper, permissionService);
    }
}
