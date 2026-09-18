package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerStudentLinkDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AccountPartnerMetricsTest {
    @InjectMocks MediaAccountProfileService service;
    @Mock MediaAccountService accounts;
    @Mock cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountProfileEntryMapper entries;
    @Mock MediaAccountFieldConfigService configs;
    @Mock MediaAccountObjectPermissionProvider objects;
    @Mock PermissionApi permissionApi;
    @Mock cn.iocoder.yudao.module.system.api.user.AdminUserApi users;
    @Mock PersonMapper people;
    @Mock PartnerStudentLinkMapper partnerLinks;
    @Mock PartnerMapper partners;
    @Mock LeadMapper leadMapper;
    @Mock SalesOrderMapper orders;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(accounts.require(anyLong())).thenAnswer(i -> new MediaAccountDO().setId(i.getArgument(0)).setStudentPersonId(30L));
        var config = new MediaAccountFieldConfigRespVO.VersionVO();
        config.setFields(List.of("total_leads","month_leads","total_conversion","month_conversion","total_amount","month_amount").stream().map(k -> {
            var f = new MediaAccountFieldConfigRespVO.FieldVO(); f.setKey(k); f.setType("number"); f.setGroup("METRICS"); f.setOwnerType("AUTO"); f.setEnabled(true); return f;
        }).toList());
        when(configs.getPublished()).thenReturn(config);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    void bind() {
        when(partnerLinks.selectActiveByStudent(30L)).thenReturn(new PartnerStudentLinkDO().setPartnerId(40L));
        when(partners.selectById(40L)).thenReturn(new PartnerDO().setId(40L));
    }
    @Test void unboundIsUnavailableAndNeverQueriesBusinessTotals() {
        var result = service.get(10L, 1L);
        assertTrue(result.getValues().isEmpty());
        assertEquals("WAITING_PARTNER_ACCOUNT", result.getPartnerMetrics().getSourceStatus());
        verifyNoInteractions(leadMapper, orders);
    }
    @Test void boundEmptyIsZeroAndReady() {
        bind();
        var result = service.get(10L, 1L);
        assertEquals(0L,result.getValues().get("total_leads"));
        assertEquals(BigDecimal.ZERO,result.getValues().get("month_conversion"));
        assertEquals("READY",result.getPartnerMetrics().getSourceStatus());
    }
    @Test void accountIdsUseTheSameStudentPartnerAndCohortRates() {
        bind();
        when(leadMapper.aggregatePartnerValidCohort(eq(1L),eq(40L),isNull(),any())).thenReturn(Map.of("leads",8L,"deals",3L));
        when(leadMapper.aggregatePartnerValidCohort(eq(1L),eq(40L),notNull(),any())).thenReturn(Map.of("leads",4L,"deals",1L));
        when(orders.sumPartnerEffectiveGross(eq(1L),eq(40L),isNull(),any())).thenReturn(new BigDecimal("1234.50"));
        when(orders.sumPartnerEffectiveGross(eq(1L),eq(40L),notNull(),any())).thenReturn(new BigDecimal("234.50"));
        var first = service.get(10L,1L);
        assertEquals(new BigDecimal("0.2500"),first.getValues().get("month_conversion"));
        assertEquals(new BigDecimal("0.3750"),first.getValues().get("total_conversion"));
        assertEquals(new BigDecimal("1234.50"),first.getValues().get("total_amount"));
        assertEquals(first.getValues(),service.get(11L,1L).getValues());
        verify(leadMapper,times(2)).aggregatePartnerValidCohort(eq(1L),eq(40L),argThat(t -> t != null && t.getDayOfMonth()==1 && t.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)),any());
    }
}
