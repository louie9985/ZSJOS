package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CashbackNameSearchTest {
    @org.junit.jupiter.api.BeforeAll static void initializeMapperMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "cashback-name-test"),
                CashbackDO.class);
    }
    @Test void sourceNamesOnlyUseVisibleIdentitiesAndHandleMissingReferences() {
        var trace = spy(new FinanceTraceService());
        var leads = mock(LeadMapper.class); var orders = mock(SalesOrderMapper.class); var cashbacks = mock(CashbackMapper.class);
        ReflectionTestUtils.setField(trace, "leads", leads); ReflectionTestUtils.setField(trace, "orders", orders);
        ReflectionTestUtils.setField(trace, "cashbacks", cashbacks);
        doReturn(Set.of(1L, 2L)).when(trace).matchIdentityIds("cashback", "contains", "姓名");
        doReturn(true).when(trace).canQuerySource(anyString());
        doReturn(Set.of(11L)).when(trace).visibleSourceIds("lead_identity");
        doReturn(Set.of(12L)).when(trace).visibleSourceIds("order");
        when(leads.selectBatchIds(Set.of(11L))).thenReturn(List.of(new LeadDO().setId(11L).setSubmittedName("客户姓名")));
        when(orders.selectBatchIds(Set.of(12L))).thenReturn(List.of(new SalesOrderDO().setId(12L).setStudentName("学员姓名")));
        when(cashbacks.selectList(org.mockito.ArgumentMatchers.<Wrapper<CashbackDO>>any())).thenReturn(List.of(
            new CashbackDO().setId(3L).setLeadId(11L), new CashbackDO().setId(4L).setOrderId(12L),
            new CashbackDO().setId(5L).setLeadId(99L), new CashbackDO().setId(6L)));
        assertEquals(Set.of(1L,2L,3L,4L), trace.matchCashbackNameIds("  姓名  "));
        doReturn(false).when(trace).canQuerySource(anyString());
        clearInvocations(leads, orders, cashbacks);
        assertEquals(Set.of(1L,2L), trace.matchCashbackNameIds("姓名"));
        verifyNoInteractions(leads, orders, cashbacks);
        assertTrue(trace.matchCashbackNameIds("  ").isEmpty());
    }

    @Test void sourceVisibilityRequiresObjectAccessAndUnmaskedCustomerIdentity() {
        var trace = spy(new FinanceTraceService()); var mapper = mock(CashbackMapper.class);
        var leads = mock(LeadMapper.class); var orders = mock(SalesOrderMapper.class);
        var leadAccess = mock(cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService.class);
        var orderAccess = mock(cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService.class);
        var permissions = mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
        ReflectionTestUtils.setField(trace,"cashbacks",mapper); ReflectionTestUtils.setField(trace,"leads",leads);
        ReflectionTestUtils.setField(trace,"orders",orders); ReflectionTestUtils.setField(trace,"leadAccess",leadAccess);
        ReflectionTestUtils.setField(trace,"orderAccess",orderAccess); ReflectionTestUtils.setField(trace,"permissions",permissions);
        doReturn(true).when(trace).canQuerySource(anyString());
        when(mapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<CashbackDO>>any())).thenReturn(List.of(
            new CashbackDO().setId(1L).setLeadId(11L).setOrderId(21L),
            new CashbackDO().setId(2L).setLeadId(12L).setOrderId(22L), new CashbackDO().setId(3L).setLeadId(13L)));
        var visible = new LeadDO().setId(11L); var masked = new LeadDO().setId(12L); var denied = new LeadDO().setId(13L);
        when(leads.selectBatchIds(anyCollection())).thenReturn(List.of(visible,masked,denied));
        when(leadAccess.canReadDetail(visible,null)).thenReturn(true);
        when(leadAccess.canViewUnmaskedIdentity(null,visible)).thenReturn(true);
        when(leadAccess.canReadDetail(masked,null)).thenReturn(true);
        assertEquals(Set.of(11L),trace.visibleSourceIds("lead_identity"));
        var visibleOrder = new SalesOrderDO().setId(21L); var deniedOrder = new SalesOrderDO().setId(22L);
        when(orders.selectBatchIds(anyCollection())).thenReturn(List.of(visibleOrder,deniedOrder));
        when(permissions.hasAnyPermissions(null,"zsjos:sales-order:query-management")).thenReturn(true);
        when(orderAccess.canReadManagement(visibleOrder,null)).thenReturn(true);
        assertEquals(Set.of(21L),trace.visibleSourceIds("order"));
    }

    @Test void partnerAndLegacyBeneficiaryNamesFollowDisplayPrecedence() {
        var trace = new FinanceTraceService(); var mapper = mock(CashbackMapper.class);
        var partners = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper.class);
        var users = mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        ReflectionTestUtils.setField(trace,"cashbacks",mapper); ReflectionTestUtils.setField(trace,"partners",partners);
        ReflectionTestUtils.setField(trace,"users",users);
        when(mapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<CashbackDO>>any())).thenReturn(List.of(
            new CashbackDO().setId(1L).setPartnerId(11L).setBeneficiaryUserId(21L),
            new CashbackDO().setId(2L).setBeneficiaryUserId(21L), new CashbackDO().setId(3L).setPartnerId(99L)));
        var partner = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO().setId(11L).setName("兼职姓名");
        when(partners.selectBatchIds(anyCollection())).thenReturn(List.of(partner));
        var user = new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO(); user.setId(21L); user.setNickname("员工姓名");
        when(users.getUserMap(anyCollection())).thenReturn(new HashMap<>(Map.of(21L,user)));
        assertEquals(Set.of(1L),trace.matchIdentityIds("cashback","contains","兼职姓名"));
        assertEquals(Set.of(1L),trace.matchIdentityIds("cashback","contains","兼职"));
        assertEquals(Set.of(2L),trace.matchIdentityIds("cashback","contains","员工"));
        assertEquals(Set.of(1L,2L),trace.matchIdentityIds("cashback","contains","姓名"));
        assertTrue(trace.matchIdentityIds("cashback","contains","历史归属信息缺失").isEmpty());
    }

    @Test void keywordMatchesIntersectWithAdvancedAndPersonalScopeBeforePaging() {
        var service = new CashbackServiceImpl(); var mapper = mock(CashbackMapper.class);
        var trace = mock(FinanceTraceService.class); var filters = mock(AdvancedFilterService.class);
        ReflectionTestUtils.setField(service,"mapper",mapper); ReflectionTestUtils.setField(service,"financeTraceService",trace);
        ReflectionTestUtils.setField(service,"advancedFilterService",filters);
        var req = new CashbackPageReqVO(); req.setKeyword("  姓名  "); req.setAdvancedFilter(new AdvancedFilterGroupReqVO());
        when(trace.matchCashbackNameIds("姓名")).thenReturn(Set.of(1L,2L));
        when(filters.matchFinanceIds("cashback",req.getAdvancedFilter())).thenReturn(List.of(2L));
        when(mapper.selectCashbackPage(req,7L,List.of(2L),Set.of(1L,2L))).thenReturn(new PageResult<>(List.of(),0L));
        assertEquals(0L,service.getPage(req,7L).getTotal());
        verify(mapper).selectCashbackPage(req,7L,List.of(2L),Set.of(1L,2L));
    }
}
