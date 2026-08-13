package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashbackServiceImplTest {
    private final CashbackServiceImpl service = new CashbackServiceImpl();
    @Mock CashbackMapper mapper; @Mock LeadMapper leadMapper; @Mock LeadIntendedProductMapper intendedMapper;
    @Mock PartnerMapper partnerMapper; @Mock ZsjosProductMapper productMapper;
    @Mock ZsjosProductCategoryMapper categoryMapper; @Mock SalesOrderMapper orderMapper; @Mock ConfigApi configApi;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "mapper", mapper); ReflectionTestUtils.setField(service, "leadMapper", leadMapper);
        ReflectionTestUtils.setField(service, "intendedProductMapper", intendedMapper); ReflectionTestUtils.setField(service, "partnerMapper", partnerMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper); ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper); ReflectionTestUtils.setField(service, "configApi", configApi);
    }

    @Test void nonPartnerDoesNotGenerate() {
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setId(1L).setSourceType("new_media"));
        assertNull(service.ensureValidCashback(1L));
        verifyNoInteractions(intendedMapper, partnerMapper);
    }

    @Test void validCashbackUsesProductRuleAndIsIdempotent() {
        eligibleLead();
        when(intendedMapper.selectPrimaryByLeadId(1L)).thenReturn(new LeadIntendedProductDO().setProductRef("P1").setProductNameSnapshot("课程"));
        when(productMapper.selectByProductRef("P1")).thenReturn(new ZsjosProductDO().setId(4L).setProductRef("P1")
                .setCategoryId(5L).setValidCashbackAmount(new BigDecimal("12.345")).setDealCashbackRate(new BigDecimal("0.2")));
        when(categoryMapper.selectById(5L)).thenReturn(new ZsjosProductCategoryDO().setId(5L).setParentId(0L));
        doAnswer(invocation -> { invocation.<CashbackDO>getArgument(0).setId(9L); return 1; }).when(mapper).insert(any(CashbackDO.class));
        assertEquals(9L, service.ensureValidCashback(1L));
        verify(mapper).insert(argThat((CashbackDO row) -> new BigDecimal("12.35").equals(row.getAmount())));
        when(mapper.selectByBusinessKey("valid:1")).thenReturn(new CashbackDO().setId(9L).setStatus("pending_settlement"));
        assertEquals(9L, service.ensureValidCashback(1L));
    }

    @Test void dealUsesHalfUpAndSettlementRequiresEffectiveOrder() {
        eligibleLead();
        doAnswer(invocation -> { invocation.<CashbackDO>getArgument(0).setId(10L); return 1; }).when(mapper).insert(any(CashbackDO.class));
        assertEquals(10L, service.ensureDealCashback(new CashbackService.DealCashbackCommand(
                1L, 2L, 3L, "P1", "课程", new BigDecimal("10.05"), new BigDecimal("0.15"))));
        verify(mapper).insert(argThat((CashbackDO row) -> new BigDecimal("1.51").equals(row.getAmount())));

        CashbackDO matured = new CashbackDO().setId(10L).setType("deal").setOrderId(2L).setStatus("pending_settlement")
                .setAvailableAt(LocalDateTime.now().minusMinutes(1)).setVersion(0);
        when(mapper.selectMatured(any())).thenReturn(List.of(matured));
        when(orderMapper.selectById(2L)).thenReturn(new SalesOrderDO().setStatus("pending_approval"));
        assertEquals(0, service.settleMatured());
        when(orderMapper.selectById(2L)).thenReturn(new SalesOrderDO().setStatus("effective"));
        when(mapper.transition(eq(10L), eq(0), eq("pending_settlement"), eq("available"), any())).thenReturn(1);
        assertEquals(1, service.settleMatured());
    }

    private void eligibleLead() {
        when(leadMapper.selectById(1L)).thenReturn(new LeadDO().setId(1L).setSourceType("partner").setSourceUserId(7L).setPartnerId(8L));
        when(partnerMapper.selectById(8L)).thenReturn(new PartnerDO().setId(8L).setBoundSystemUserId(7L).setStatus("enabled"));
    }
}
