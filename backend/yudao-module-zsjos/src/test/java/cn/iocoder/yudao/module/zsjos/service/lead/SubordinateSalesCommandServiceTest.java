package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesAuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubordinateSalesCommandServiceTest {
    @InjectMocks private SubordinateSalesCommandService service;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Mock private SubordinateSalesAuditLogMapper auditLogMapper;
    @Mock private LeadDispatchService dispatchService;
    @Mock private LeadObjectPermissionService permissionService;
    @Mock private LeadAgingPoolService agingPoolService;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void manualPublicSeaPreservesLeadOwnershipAndStates() {
        LeadDO lead = lead(1L, 20L); lead.setStatus("valid"); lead.setAssignmentStatus("owned");
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        when(publicSeaRecordMapper.selectByLeadId(1L)).thenReturn(null);

        service.releasePublicSeaOne(1L, 30L, 10L, "协同跟进");

        ArgumentCaptor<LeadPublicSeaRecordDO> record = ArgumentCaptor.forClass(LeadPublicSeaRecordDO.class);
        verify(publicSeaRecordMapper).insert(record.capture());
        assertEquals(20L, record.getValue().getOwnerUserId());
        assertEquals(30L, record.getValue().getCollaboratorUserId());
        assertEquals(20L, lead.getOwnerUserId());
        assertEquals("valid", lead.getStatus());
        assertEquals("owned", lead.getAssignmentStatus());
        verify(leadMapper, never()).updateById(any(LeadDO.class));
    }

    @Test
    void transferRejectsCurrentOwnerAsTarget() {
        LeadDO lead = lead(1L, 20L);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        assertThrows(ServiceException.class, () -> service.transferOne(1L, 20L, 10L, "无变化"));
        verifyNoInteractions(dispatchService);
    }

    @Test
    void manualPublicSeaRejectsActiveAgingPool() {
        LeadDO lead = lead(1L, 20L); lead.setStatus("valid"); lead.setAssignmentStatus("owned");
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        when(agingPoolService.getActiveCycle(1L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.releasePublicSeaOne(1L, 30L, 10L, "协同跟进"));

        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_COLLABORATION_POOL_CONFLICT.getCode(),
                error.getCode());
        verify(publicSeaRecordMapper, never()).insert(any(LeadPublicSeaRecordDO.class));
    }

    private static LeadDO lead(Long id, Long owner) {
        LeadDO lead = new LeadDO(); lead.setId(id); lead.setOwnerUserId(owner); return lead;
    }
}
