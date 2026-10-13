package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderListItemRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOwnershipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderPartnerOwnerSnapshotTest {
    @InjectMocks private SalesOrderServiceImpl service;
    @Mock private LeadMapper leadMapper;
    @Mock private PartnerMapper partnerMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PartnerOwnershipService partnerOwnershipService;

    private SalesOrderDO order() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(100L); order.setLeadId(10L); order.setStatus("effective");
        return order;
    }

    private void lead(String sourceType, Long partnerId) {
        LeadDO lead = new LeadDO();
        lead.setId(10L); lead.setLeadNo("LD-OWNER-TEST");
        lead.setOwnerUserId(20L); lead.setSourceUserId(21L);
        lead.setSourceType(sourceType); lead.setPartnerId(partnerId);
        when(leadMapper.selectById(10L)).thenReturn(lead);
    }

    private String capture() throws Exception {
        var type = Class.forName(SalesOrderServiceImpl.class.getName() + "$ValidatedSubmission");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object validated = constructor.newInstance(List.of(), List.of(), BigDecimal.ZERO, Map.of(), List.of());
        return ReflectionTestUtils.invokeMethod(service, "buildOrderSnapshot", order(), validated);
    }

    private SalesOrderListItemRespVO list(String json) {
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setOrderSnapshot(json);
        return ReflectionTestUtils.invokeMethod(service, "convertListItem", order(), round, null, List.of());
    }

    @Test void capturesCurrentOwnerAndBothProjectionsKeepItAfterTransferAndRename() throws Exception {
        lead("partner", 70L);
        PartnerOwnershipDO ownership = new PartnerOwnershipDO();
        ownership.setEmployeeUserId(80L); ownership.setEmployeeNameSnapshot("归属时旧姓名");
        when(partnerOwnershipService.getByPartnerId(70L)).thenReturn(ownership);
        AdminUserRespDTO employee = new AdminUserRespDTO();
        employee.setNickname("订单提交时姓名");
        when(adminUserApi.getUser(80L)).thenReturn(employee);
        String json = capture();
        ownership.setEmployeeUserId(90L); employee.setNickname("后来改名");
        clearInvocations(partnerOwnershipService, adminUserApi, leadMapper);
        SalesOrderRespVO detail = new SalesOrderRespVO();
        SalesOrderSnapshot.read(json).apply(detail);
        assertEquals(80L, detail.getLeadProfile().getPartnerOwnerSnapshot().getEmployeeUserId());
        assertEquals("订单提交时姓名", detail.getLeadProfile().getPartnerOwnerSnapshot().getEmployeeName());
        var row = list(json);
        assertEquals("partner", row.getLeadSourceType());
        assertEquals(detail.getLeadProfile().getPartnerOwnerSnapshot(), row.getPartnerOwnerSnapshot());
        verifyNoInteractions(partnerOwnershipService, adminUserApi, leadMapper);
    }

    @Test void newSuccessorSnapshotUsesNewOwnerWhileOriginalStaysFrozen() throws Exception {
        lead("partner", 70L);
        PartnerOwnershipDO ownership = new PartnerOwnershipDO();
        ownership.setEmployeeUserId(80L); ownership.setEmployeeNameSnapshot("原员工");
        when(partnerOwnershipService.getByPartnerId(70L)).thenReturn(ownership);
        String original = capture();
        ownership.setEmployeeUserId(90L); ownership.setEmployeeNameSnapshot("新员工");
        String successor = capture();
        assertEquals(80L, list(original).getPartnerOwnerSnapshot().getEmployeeUserId());
        assertEquals(90L, list(successor).getPartnerOwnerSnapshot().getEmployeeUserId());
    }

    @Test void unassignedSnapshotSurvivesJsonAndDiffersFromMissingHistory() throws Exception {
        lead("partner", 70L);
        var row = list(capture());
        assertNotNull(row.getPartnerOwnerSnapshot());
        assertNull(row.getPartnerOwnerSnapshot().getEmployeeUserId());
        assertNull(row.getPartnerOwnerSnapshot().getEmployeeName());
    }

    @Test void historicalReadDoesNotQueryCurrentOwnershipOrFillIt() {
        String json = "{\"snapshotVersion\":2,\"leadProfile\":{\"sourceType\":\"partner\"}}";
        assertNull(list(json).getPartnerOwnerSnapshot());
        assertNull(list(null).getPartnerOwnerSnapshot());
        SalesOrderRespVO detail = new SalesOrderRespVO();
        SalesOrderSnapshot.read(json).apply(detail);
        assertNull(detail.getLeadProfile().getPartnerOwnerSnapshot());
        verifyNoInteractions(partnerOwnershipService, adminUserApi, leadMapper);
    }

    @Test void nonPartnerSourceDoesNotUseCoincidentPartnerId() throws Exception {
        lead("internal_new_media", 70L);
        assertNull(list(capture()).getPartnerOwnerSnapshot());
        verifyNoInteractions(partnerOwnershipService);
    }

    @Test void missingPartnerIdDoesNotResolveAnEmployeeByAnotherIdentity() throws Exception {
        lead("partner", null);
        assertNull(list(capture()).getPartnerOwnerSnapshot().getEmployeeUserId());
        verifyNoInteractions(partnerOwnershipService);
    }
}
