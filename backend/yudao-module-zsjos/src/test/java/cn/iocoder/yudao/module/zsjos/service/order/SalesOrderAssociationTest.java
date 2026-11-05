package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SalesOrderAssociationTest {
    private LeadDO lead(long id) { var lead = new LeadDO(); lead.setId(id); lead.setStatus("closed"); return lead; }
    @Test void noMatchContinuesAndUniqueMatchIncludesHistoricalClosedLead() {
        assertNull(SalesOrderServiceImpl.selectAssociation(List.of(), null));
        var lead = lead(10);
        assertSame(lead, SalesOrderServiceImpl.selectAssociation(List.of(lead), null));
        assertEquals("closed", lead.getStatus());
    }
    @Test void multipleRequiresChoiceAndRejectsStaleOrForeignSelection() {
        var leads = List.of(lead(10), lead(20));
        assertThrows(RuntimeException.class, () -> SalesOrderServiceImpl.selectAssociation(leads, null));
        assertSame(leads.get(1), SalesOrderServiceImpl.selectAssociation(leads, 20L));
        assertThrows(RuntimeException.class, () -> SalesOrderServiceImpl.selectAssociation(leads, 30L));
        assertThrows(RuntimeException.class, () -> SalesOrderServiceImpl.selectAssociation(List.of(), 10L));
    }
    @Test void associationRoundTripDoesNotBecomeTransactionLeadOrChangeOrderOwner() {
        var snapshot = new SalesOrderSnapshot(); snapshot.setSnapshotVersion(2); snapshot.setAssociatedLeadId(10L);
        var profile = new SalesOrderRespVO.LeadProfileVO(); profile.setLeadNo("L-TEST"); profile.setSubmittedName("录单时姓名");
        snapshot.setLeadProfile(profile);
        var read = SalesOrderSnapshot.read(JsonUtils.toJsonString(snapshot));
        var response = new SalesOrderRespVO(); response.setSubmitterUserId(5L); response.setFormalSalesUserId(5L);
        read.apply(response);
        assertEquals(10L, response.getAssociatedLeadId()); assertNull(response.getLeadId());
        assertEquals(5L, response.getFormalSalesUserId()); assertEquals("录单时姓名", response.getLeadProfile().getSubmittedName());
        var order = new SalesOrderDO(); order.setPersonId(3L); order.setSubmitterUserId(5L); order.setFormalSalesUserId(5L);
        assertNull(read.project(order).getLeadId());
        assertNull(SalesOrderSnapshot.read("{}").getAssociatedLeadId());
    }

    @Test void customerReadRequiresIdentityAndQueriesOnlyMatchedTenantCustomer() {
        var service = new SalesOrderServiceImpl();
        var customers = org.mockito.Mockito.mock(RepurchaseCustomerService.class);
        var mapper = org.mockito.Mockito.mock(cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "repurchaseCustomers", customers);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "leadMapper", mapper);
        var req = new cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.RepurchaseCustomerCheckReqVO();
        var checked = new cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.RepurchaseCustomerCheckRespVO();
        org.mockito.Mockito.when(customers.checkCustomer(5L, req)).thenReturn(checked);
        assertThrows(RuntimeException.class, () -> service.customerLeadCandidates(5L, req));
        org.mockito.Mockito.verifyNoInteractions(mapper);
        checked.setMatchStatus("MULTIPLE_MATCH");
        assertEquals(1900006081, assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.customerLeadCandidates(5L, req)).getCode());
        checked.setMatchStatus("REPURCHASE_BLOCKED");
        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_CUSTOMER_ACTIVE_REPURCHASE.getCode(),
                assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                        () -> service.customerLeadCandidates(5L, req)).getCode());
        checked.setCanRepurchase(true);
        assertTrue(service.customerLeadCandidates(5L, req).isEmpty());
        org.mockito.Mockito.verifyNoInteractions(mapper);
        checked.setPersonId(3L);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            service.customerLeadCandidates(5L, req);
            org.mockito.Mockito.verify(mapper).selectOrderAssociationCandidates(3L, 1L);
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }
    @Test void candidatesBatchEnrichmentAndRetainMissingEmployeeSnapshot() {
        var service = new SalesOrderServiceImpl();
        var leads = org.mockito.Mockito.mock(cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper.class);
        var partners = org.mockito.Mockito.mock(cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper.class);
        var ownerships = org.mockito.Mockito.mock(cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOwnershipService.class);
        var users = org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "leadMapper", leads);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "partnerMapper", partners);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "partnerOwnershipService", ownerships);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adminUserApi", users);
        var first = lead(10); first.setSourceType("partner"); first.setPartnerId(7L); first.setOwnerUserId(5L);
        var second = lead(20); second.setSourceType("partner"); second.setPartnerId(7L); second.setOwnerUserId(5L);
        var third = lead(30); third.setSourceType("sales_self_sourced"); third.setOwnerUserId(5L);
        var partner = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO(); partner.setId(7L); partner.setName("兼职");
        var owner = new cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO();
        owner.setPartnerId(7L); owner.setEmployeeUserId(8L); owner.setEmployeeNameSnapshot("原归属员工");
        var user = new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO(); user.setId(5L); user.setNickname("负责人");
        org.mockito.Mockito.when(leads.selectOrderAssociationCandidates(3L, 1L)).thenReturn(List.of(first, second, third));
        org.mockito.Mockito.when(partners.selectBatchIds(java.util.Set.of(7L))).thenReturn(List.of(partner));
        org.mockito.Mockito.when(ownerships.getByPartnerIds(java.util.Set.of(7L))).thenReturn(List.of(owner));
        org.mockito.Mockito.when(users.getUserMap(java.util.Set.of(5L, 8L))).thenReturn(java.util.Map.of(5L, user));
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            List<cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.OrderLeadCandidateRespVO> result =
                    org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "associationCandidates", 3L);
            assertEquals(3, result.size());
            assertEquals("兼职", result.getFirst().profile().getSourceUserName());
            assertEquals("原归属员工", result.getFirst().profile().getPartnerOwnerSnapshot().getEmployeeName());
            assertEquals("负责人", result.get(2).profile().getOwnerUserName());
            org.mockito.Mockito.verify(users).getUserMap(java.util.Set.of(5L, 8L));
            org.mockito.Mockito.verifyNoMoreInteractions(users);
            org.mockito.Mockito.verify(partners).selectBatchIds(java.util.Set.of(7L));
            org.mockito.Mockito.verifyNoMoreInteractions(partners);
            org.mockito.Mockito.verify(ownerships).getByPartnerIds(java.util.Set.of(7L));
            org.mockito.Mockito.verifyNoMoreInteractions(ownerships);
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }

    @Test void convertedCandidateUsesExistingEffectiveStatusLabel() {
        var candidate = new cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.OrderLeadCandidateRespVO(1L, "converted", null);
        assertEquals("有效", candidate.getStatusLabel());
    }

}
