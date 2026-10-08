package cn.iocoder.yudao.module.zsjos.service.order;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RepurchaseDuplicateGuardTest {
 @InjectMocks RepurchaseDuplicateGuard guard;
 @Mock SalesOrderMapper orderMapper;
 @Mock SalesOrderItemMapper itemMapper;
 private SalesOrderSubmitReqVO req(){var r=new SalesOrderSubmitReqVO();r.setCustomerPaidAt(LocalDateTime.of(2026,9,1,10,0));var i=new SalesOrderSubmitReqVO.Item();i.setSpuRef("p");i.setSkuRef("s");i.setActualAmount(new BigDecimal("100"));r.setItems(List.of(i));return r;}
 private SalesOrderDO order(){var o=new SalesOrderDO();o.setId(8L);o.setPersonId(10L);o.setStatus("effective");o.setCustomerPaidAt(req().getCustomerPaidAt());return o;}
 private SalesOrderItemDO item(){var i=new SalesOrderItemDO();i.setOrderId(8L);i.setProductRef("p");i.setSkuRef("s");i.setPayableAmount(new BigDecimal("100.00"));return i;}
 @Test void exactPurchaseWithNewKeyStillBlocked(){when(orderMapper.selectByPersonId(10L)).thenReturn(List.of(order()));when(itemMapper.selectListByOrderIds(any())).thenReturn(List.of(item()));assertThrows(RuntimeException.class,()->guard.check(10L,req()));}
 @Test void laterRealPurchaseOfSameCourseAllowed(){var o=order();o.setCustomerPaidAt(o.getCustomerPaidAt().minusDays(1));when(orderMapper.selectByPersonId(10L)).thenReturn(List.of(o));assertDoesNotThrow(()->guard.check(10L,req()));verifyNoInteractions(itemMapper);}
 @Test void reusedVoucherBlockedEvenWhenTimeChanged(){var o=order();o.setCustomerPaidAt(o.getCustomerPaidAt().minusDays(1));o.setPaymentVoucherRefs("[{\"infraFileId\":123}]");when(orderMapper.selectByPersonId(10L)).thenReturn(List.of(o));var r=req();var a=new SalesOrderSubmitReqVO.Attachment();a.setInfraFileId(123L);r.setPaymentVouchers(List.of(a));assertThrows(RuntimeException.class,()->guard.check(10L,r));}
 @Test void terminatedPurchaseDoesNotBlock(){var o=order();o.setStatus("terminated");when(orderMapper.selectByPersonId(10L)).thenReturn(List.of(o));assertDoesNotThrow(()->guard.check(10L,req()));}
 @Test void historicalStringVoucherDoesNotBreakNewPurchase(){var o=order();o.setCustomerPaidAt(o.getCustomerPaidAt().minusDays(1));o.setPaymentVoucherRefs("[\"https://example.test/legacy.pdf\"]");when(orderMapper.selectByPersonId(10L)).thenReturn(List.of(o));var r=req();var a=new SalesOrderSubmitReqVO.Attachment();a.setInfraFileId(123L);r.setPaymentVouchers(List.of(a));assertDoesNotThrow(()->guard.check(10L,r));}
}
