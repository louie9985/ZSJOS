package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderVoidReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.OrderPaymentAllocationMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderVoidServiceTest {
    @InjectMocks SalesOrderVoidService service;
    @Mock SalesOrderMapper orders;
    @Mock LeadMapper leads;
    @Mock PersonMapper persons;
    @Mock OpportunityMapper opportunities;
    @Mock CashbackMapper cashbacks;
    @Mock GiftPurchaseMapper gifts;
    @Mock OrderPaymentAllocationMapper allocations;
    @Mock SalesOrderObjectPermissionService permissions;
    @Mock SalesOrderCommandService commands;
    SalesOrderDO order;
    LeadDO lead;
    SalesOrderVoidReqVO request;

    @BeforeAll static void tables() {
        for (Class<?> type : List.of(cn.iocoder.yudao.module.zsjos.dal.dataobject.giftpurchase.GiftPurchaseDO.class, cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.OrderPaymentAllocationDO.class, cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "void-test"), type);
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        order = new SalesOrderDO(); order.setId(10L); order.setLeadId(20L); order.setPersonId(30L);
        order.setStatus("effective"); order.setVersion(4); order.setEffectiveAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        lead = new LeadDO(); lead.setId(20L); lead.setStatus("won"); lead.setOwnerUserId(7L);
        request = new SalesOrderVoidReqVO(); request.setReason("  部分退费后重新录单  "); request.setOrderVersion(4); request.setIdempotencyKey("void-one");
        when(orders.selectById(10L)).thenReturn(order);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    void authorized() {
        when(permissions.canVoid(order, 8L)).thenReturn(true);
        when(leads.selectByIdForUpdate(20L, 1L)).thenReturn(lead);
        when(orders.selectByIdForUpdate(10L, 1L)).thenReturn(order);
    }

    @Test void voidsHistoricalOrderWithoutBpmAndRestoresEntryWithoutChangingOwner() {
        authorized();
        service.voidOrder(10L, 8L, request);
        assertEquals("voided", order.getStatus()); assertEquals(5, order.getVersion());
        assertEquals("部分退费后重新录单", order.getVoidReason()); assertEquals(8L, order.getVoidedByUserId());
        assertNotNull(order.getVoidedAt()); assertEquals(LocalDateTime.of(2026,9,1,10,0), order.getEffectiveAt());
        assertEquals("valid", lead.getStatus()); assertEquals(7L, lead.getOwnerUserId());
        verify(opportunities).insert(argThat((cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO o) -> o.getOwnerUserId().equals(7L) && o.getPersonId().equals(30L) && o.getStatus().equals("following")));
        verify(leads).updateById(lead); verify(gifts).update(isNull(), any()); verify(allocations).update(isNull(), any());
        verify(commands).register(eq("void-one"), argThat(c -> c.roundId() == null && c.processInstanceId() == null && c.commandType().equals("void")));
    }
    @Test void lastCustomerRepurchaseCanRestoreItsExistingLead() {
        order.setLeadId(null); when(leads.selectByPersonId(30L)).thenReturn(lead);
        authorized(); service.voidOrder(10L,8L,request);
        assertEquals("valid", lead.getStatus()); assertNotNull(lead.getLastActivityAt());
    }

    @Test void otherEffectiveOrderPreservesConvertedCustomer() {
        authorized(); when(orders.hasEffectiveOrder(30L)).thenReturn(true);
        service.voidOrder(10L, 8L, request);
        assertEquals("won", lead.getStatus()); verify(leads, never()).updateById(any(LeadDO.class));
        verifyNoInteractions(opportunities);
    }
    @Test void repeatedCommandDoesNotInvalidateOrReopenAgain() {
        authorized(); order.setStatus("voided"); when(commands.replay(eq("void-one"), any())).thenReturn(true);
        service.voidOrder(10L, 8L, request);
        verify(orders, never()).updateById(any(SalesOrderDO.class)); verifyNoInteractions(cashbacks, gifts, allocations, opportunities);
    }
    @Test void unauthorizedCannotLockOrMutate() {
        assertThrows(RuntimeException.class, () -> service.voidOrder(10L, 8L, request));
        verifyNoInteractions(leads, persons, cashbacks, gifts, allocations, commands);
    }
    @Test void staleRequestCannotRegisterOrInvalidate() {
        authorized(); request.setOrderVersion(3);
        assertThrows(RuntimeException.class, () -> service.voidOrder(10L, 8L, request));
        verify(commands, never()).register(any(), any()); verifyNoInteractions(cashbacks, gifts, allocations);
    }
    @ParameterizedTest @ValueSource(strings = {"pending_approval", "revision_required", "terminated", "superseded", "voided"})
    void onlyEffectiveOrdersCanBeVoided(String status) {
        authorized(); order.setStatus(status);
        assertThrows(RuntimeException.class, () -> service.voidOrder(10L, 8L, request));
        verify(commands, never()).register(any(), any()); verifyNoInteractions(cashbacks, gifts, allocations);
    }
    @ParameterizedTest @ValueSource(strings = {"pending_settlement", "available", "blocked", "withdrawing", "withdrawn"})
    void invalidatesEveryRebateStateWithoutErasingPaidHistory(String status) {
        authorized(); var cashback = new CashbackDO(); cashback.setId(40L); cashback.setVersion(2); cashback.setStatus(status);
        var settled = LocalDateTime.of(2026,9,2,10,0); cashback.setSettledAt(settled);
        when(cashbacks.selectByOrderIdForUpdate(10L,1L)).thenReturn(List.of(cashback));
        when(cashbacks.cancel(eq(40L),eq(2),eq(status),any(),any())).thenReturn(1);
        service.voidOrder(10L,8L,request);
        verify(cashbacks).cancel(eq(40L),eq(2),eq(status),any(),contains("订单作废")); assertEquals(settled,cashback.getSettledAt());
    }
}
