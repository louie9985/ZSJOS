package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesAuditLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesCommandMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubordinateSalesCommandServiceTest {
    @InjectMocks private SubordinateSalesCommandService service;
    @Mock private LeadMapper leadMapper;
    @Mock private SubordinateSalesAuditLogMapper auditLogMapper;
    @Mock private SubordinateSalesCommandMapper commandMapper;
    @Mock private LeadDispatchService dispatchService;
    @Mock private LeadObjectPermissionService permissionService;
    @Mock private LeadAgingPoolService agingPoolService;
    @Mock private LeadQualificationService qualificationService;

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        lenient().when(commandMapper.insertIgnore(anyLong(), any())).thenReturn(1);
        lenient().when(commandMapper.complete(anyLong(), anyLong(), anyString(), anyString())).thenReturn(1);
    }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void manualPublicSeaPreservesLeadOwnershipAndStates() {
        LeadDO lead = lead(1L, 20L); lead.setStatus("valid"); lead.setAssignmentStatus("owned");
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        service.releasePublicSeaOne(1L, 30L, 10L, "协同跟进");

        verify(agingPoolService).enterManually(eq(1L), eq(30L), eq(10L), eq("协同跟进"),
                startsWith("supervisor-public-sea:"));
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
    void manualPublicSeaRejectsUnqualifiedLead() {
        LeadDO lead = lead(1L, 20L); lead.setStatus("submitted"); lead.setAssignmentStatus("owned");
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.releasePublicSeaOne(1L, 30L, 10L, "协同跟进"));

        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SUBORDINATE_LEAD_STATE_INVALID.getCode(),
                error.getCode());
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("无法执行“释放至公海池”"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("已提交 / 已归属"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("有效或已转化 / 已归属且未关闭"));
        verifyNoInteractions(agingPoolService);
    }

    @Test
    void idempotentSingleCommandsKeepTheRowLockAndAuditInOneTransaction() {
        assertRequiresNew("transferOne", 5);
        assertRequiresNew("restoreOne", 4);
        assertRequiresNew("recycleOne", 4);
        assertRequiresNew("releaseClaimPoolOne", 4);
        assertRequiresNew("releasePublicSeaOne", 5);
    }

    private static void assertRequiresNew(String methodName, int parameterCount) {
        Method method = Set.of(SubordinateSalesCommandService.class.getDeclaredMethods()).stream()
                .filter(candidate -> candidate.getName().equals(methodName)
                        && candidate.getParameterCount() == parameterCount)
                .findFirst().orElseThrow();
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    private static LeadDO lead(Long id, Long owner) {
        LeadDO lead = new LeadDO(); lead.setId(id); lead.setOwnerUserId(owner);
        lead.setStatus("submitted"); lead.setAssignmentStatus("owned"); return lead;
    }
}
