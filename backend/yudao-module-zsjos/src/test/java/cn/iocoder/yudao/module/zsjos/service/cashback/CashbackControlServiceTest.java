package cn.iocoder.yudao.module.zsjos.service.cashback;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackControlReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CashbackControlServiceTest {
    @InjectMocks CashbackControlService service;
    @Mock CashbackMapper mapper;
    @Mock SalesOrderMapper orders;
    @Mock BusinessAuditService audit;
    CashbackDO row;
    @BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), CashbackDO.class);
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(9L);
        row = new CashbackDO().setId(1L).setVersion(3).setType("valid").setStatus("available")
                .setAmount(new BigDecimal("20.00")).setAvailableAt(LocalDateTime.now().minusDays(1));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    CashbackControlReqVO request(String reason) { return new CashbackControlReqVO().setVersion(3).setReason(reason); }
    void lock() { when(mapper.selectByIdForUpdate(1L, 9L)).thenReturn(row); }
    @SuppressWarnings("unchecked") void writable() { lock(); when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1); }
    @ParameterizedTest @ValueSource(strings={"available","pending_settlement"})
    void blockPreservesAmountAndAuditsTrimmedPublicReason(String status) {
        row.setStatus(status); writable(); service.block(1L, request("  核实中  "));
        assertEquals(new BigDecimal("20.00"), row.getAmount());
        verify(audit).record(eq("cashback"), eq("cashback.block"), eq("cashback"), eq("1"), eq("finance"),
                argThat(m -> "核实中".equals(m.get("reason")) && "blocked".equals(m.get("toStatus")) && status.equals(m.get("fromStatus"))));
    }
    @ParameterizedTest @ValueSource(strings={"withdrawing","withdrawn","cancelled","blocked"})
    void cannotBlockUnavailableSourceStates(String status) {
        row.setStatus(status); lock(); assertThrows(ServiceException.class, () -> service.block(1L, request("原因")));
        verifyNoInteractions(audit);
    }
    @ParameterizedTest @ValueSource(strings={"", "   "})
    void rejectsBlankReasonBeforeAccess(String reason) {
        assertThrows(ServiceException.class, () -> service.block(1L, request(reason))); verifyNoInteractions(mapper, audit);
    }
    @Test void rejectsOverlongReason() { assertThrows(ServiceException.class, () -> service.block(1L, request("字".repeat(501)))); verifyNoInteractions(mapper); }
    @Test void rejectsMissingAndCrossTenantRecord() { assertThrows(ServiceException.class, () -> service.block(1L, request("原因"))); verify(mapper).selectByIdForUpdate(1L,9L); verifyNoInteractions(audit); }
    @Test void rejectsStaleVersionWithoutWrite() { lock(); assertThrows(ServiceException.class, () -> service.block(1L, request("原因").setVersion(2))); verifyNoInteractions(audit); }
    @Test void cancelledCannotBeRestored() { row.setStatus("cancelled").setBlockedFromStatus("available"); lock(); assertThrows(ServiceException.class, () -> service.unblock(1L,request("恢复"))); }
    @Test void restoringMaturedValidCashbackMakesItAvailable() { restore("valid", "pending_settlement", -1, null, "available"); }
    @Test void restoringUnmaturedCashbackRetainsObservationPeriod() { restore("valid", "pending_settlement", 1, null, "pending_settlement"); }
    @Test void restoringEffectiveDealCanSettle() { restore("deal", "pending_settlement", -1, "effective", "available"); }
    @Test void restoringUnapprovedDealDoesNotSettle() { restore("deal", "pending_settlement", -1, "pending_review", "pending_settlement"); }
    @Test void restoringPreviouslyAvailableDoesNotRecalculate() { restore("valid", "available", -1, null, "available"); }
    void restore(String type, String original, int days, String orderStatus, String expected) {
        row.setStatus("blocked").setType(type).setBlockedFromStatus(original).setAvailableAt(LocalDateTime.now().plusDays(days));
        if (orderStatus != null) { row.setOrderId(7L); when(orders.selectById(7L)).thenReturn(new SalesOrderDO().setStatus(orderStatus)); }
        writable(); service.unblock(1L, request("允许恢复"));
        verify(audit).record(eq("cashback"),eq("cashback.unblock"),eq("cashback"),eq("1"),eq("finance"),argThat(m -> expected.equals(m.get("toStatus"))));
        assertEquals(new BigDecimal("20.00"),row.getAmount());
    }
    @Test void updateConflictDoesNotWriteAudit() { lock(); assertThrows(ServiceException.class, () -> service.block(1L,request("原因"))); verifyNoInteractions(audit); }
}
