package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.SubordinatePartnerPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOwnershipMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.SubordinatePartnerRow;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubordinatePartnerServiceTest {
    @InjectMocks private SubordinatePartnerService service;
    @Mock private PartnerOwnershipService ownershipService;
    @Mock private PartnerOwnershipMapper ownershipMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadManagementService leadManagementService;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(9L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void disabledQueryPermissionReturnsEmptyWithoutDatabaseAccess() {
        when(ownershipService.canQuery(20L)).thenReturn(false);

        var result = service.getPage(request(1, ""), 20L);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        verifyNoInteractions(ownershipMapper);
    }

    @Test
    void outOfRangePageReturnsCountWithoutLoadingRows() {
        when(ownershipService.canQuery(20L)).thenReturn(true);
        when(ownershipMapper.selectSubordinateCount(9L, 20L, null, "张三")).thenReturn(20L);

        var result = service.getPage(request(Integer.MAX_VALUE, "  张三  "), 20L);

        assertTrue(result.getList().isEmpty());
        assertEquals(20L, result.getTotal());
        verify(ownershipMapper, never()).selectSubordinatePage(anyLong(), anyLong(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void pageUsesLongOffsetAndMapsOwnershipProjection() {
        when(ownershipService.canQuery(20L)).thenReturn(true);
        when(ownershipMapper.selectSubordinateCount(9L, 20L, null, null)).thenReturn(41L);
        SubordinatePartnerRow row = new SubordinatePartnerRow();
        row.setId(10L); row.setPartnerNo("P-10"); row.setName("兼职甲"); row.setStatus("enabled");
        row.setAssignedEmployeeUserId(20L); row.setAssignedEmployeeName("员工甲"); row.setAssignmentVersion(3);
        when(ownershipMapper.selectSubordinatePage(9L, 20L, null, null, 40L, 20)).thenReturn(List.of(row));
        when(ownershipService.canRead(20L, 10L)).thenReturn(true);

        var result = service.getPage(request(3, null), 20L);

        assertEquals(41L, result.getTotal());
        assertEquals("P-10", result.getList().getFirst().getPartnerNo());
        assertEquals("员工甲", result.getList().getFirst().getAssignedEmployeeName());
        assertTrue(result.getList().getFirst().getAssignmentEffective());
    }

    @Test
    void managerUsesTenantWidePage() {
        when(ownershipService.canQuery(20L)).thenReturn(true);
        when(ownershipService.canManage(20L)).thenReturn(true);
        when(ownershipMapper.selectManagedCount(9L, null, null)).thenReturn(1L);
        SubordinatePartnerRow row = new SubordinatePartnerRow();
        row.setId(10L); row.setPartnerNo("P-10"); row.setName("兼职甲"); row.setStatus("enabled");
        when(ownershipMapper.selectManagedPage(9L, null, null, 0L, 20)).thenReturn(List.of(row));

        var result = service.getPage(request(1, null), 20L);

        assertEquals(1L, result.getTotal());
        assertEquals("P-10", result.getList().getFirst().getPartnerNo());
        verify(ownershipMapper, never()).selectSubordinatePage(anyLong(), anyLong(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void leadDetailUsesPartnerScopedReadAfterOwnershipCheck() {
        LeadDO lead = new LeadDO().setId(30L).setPartnerId(10L);
        LeadManagementRespVO expected = new LeadManagementRespVO().setId(30L);
        when(leadMapper.selectById(30L)).thenReturn(lead);
        when(leadManagementService.getPartnerLead(30L, 10L)).thenReturn(expected);

        LeadManagementRespVO result = service.getLead(30L, 20L);

        assertSame(expected, result);
        assertEquals(List.of(), result.getAvailableActions());
        verify(ownershipService).checkRead(20L, 10L);
        verify(leadManagementService, never()).getLead(anyLong(), anyLong());
    }

    private SubordinatePartnerPageReqVO request(int pageNo, String keyword) {
        SubordinatePartnerPageReqVO req = new SubordinatePartnerPageReqVO();
        req.setPageNo(pageNo); req.setPageSize(20); req.setKeyword(keyword);
        return req;
    }
}
