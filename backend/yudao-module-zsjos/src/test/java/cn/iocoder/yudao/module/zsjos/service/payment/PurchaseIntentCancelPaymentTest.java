package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayClient;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PurchaseIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 覆盖「链接生成后、确认到账前」期间的取消支付链接状态机。
 * 使用 spy 覆盖 createAllinpayClient 注入模拟网关，不真实调用通联。
 */
class PurchaseIntentCancelPaymentTest {
    private PurchaseIntentService service;
    private PurchaseIntentMapper purchaseIntentMapper;
    private PaymentIntentMapper paymentIntentMapper;
    private PaymentGatewayEventMapper gatewayEventMapper;
    private AllinpayClient client;
    private PaymentIntentDO payment;
    private PurchaseIntentDO intent;

    @BeforeEach
    void setUp() {
        service = spy(new PurchaseIntentService());
        purchaseIntentMapper = mock(PurchaseIntentMapper.class);
        paymentIntentMapper = mock(PaymentIntentMapper.class);
        gatewayEventMapper = mock(PaymentGatewayEventMapper.class);
        client = mock(AllinpayClient.class);
        ReflectionTestUtils.setField(service, "purchaseIntentMapper", purchaseIntentMapper);
        ReflectionTestUtils.setField(service, "paymentIntentMapper", paymentIntentMapper);
        ReflectionTestUtils.setField(service, "gatewayEventMapper", gatewayEventMapper);
        ReflectionTestUtils.setField(service, "allinpayProperties", new AllinpayProperties());
        // 独立事务的待确认记录器在单元测试中直接落库到 mapper
        var recorder = new PaymentClosePendingRecorder();
        ReflectionTestUtils.setField(recorder, "paymentIntentMapper", paymentIntentMapper);
        ReflectionTestUtils.setField(service, "closePendingRecorder", recorder);
        doReturn(client).when(service).createAllinpayClient(any());

        intent = new PurchaseIntentDO().setId(9L).setOwnerUserId(1L).setInitiatorUserId(1L)
                .setSnapshotLocked(true).setCollectionMode("online_link");
        when(purchaseIntentMapper.selectByIdForUpdate(9L)).thenReturn(intent);

        payment = new PaymentIntentDO().setId(100L).setPurchaseIntentId(9L).setStatus("created")
                .setExpectedAmount(new BigDecimal("1280.00")).setReqsn(null).setVersion(0);
        when(paymentIntentMapper.selectLatestByPurchaseIntent(9L)).thenReturn(payment);
    }

    private AllinpayClient.GatewayResponse gateway(String retcode, String trxstatus, Integer trxamt, boolean valid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("retcode", retcode);
        if (trxstatus != null) payload.put("trxstatus", trxstatus);
        if (trxamt != null) payload.put("trxamt", String.valueOf(trxamt));
        return AllinpayClient.GatewayResponse.from(payload, valid);
    }

    @Test
    void createdPaymentIsVoidedAndUnlocksWithoutCallingGateway() {
        service.cancelPayment(9L, 1L);

        assertEquals("closed", payment.getStatus());
        assertNotNull(payment.getClosedAt());
        assertEquals(false, intent.getSnapshotLocked());
        // 保持线上支付模式，销售改价后才重新生成链接
        assertEquals("online_link", intent.getCollectionMode());
        verify(client, never()).query(any());
        verify(client, never()).close(any());
    }

    @Test
    void waitingUnpaidPaymentIsClosedAndUnlocks() {
        payment.setStatus("waiting").setReqsn("ZS100");
        when(client.query("ZS100")).thenReturn(gateway("SUCCESS", "2000", 128000, true));
        when(client.close("ZS100")).thenReturn(gateway("SUCCESS", "", null, true));

        service.cancelPayment(9L, 1L);

        assertEquals("closed", payment.getStatus());
        assertEquals(false, intent.getSnapshotLocked());
        assertEquals("online_link", intent.getCollectionMode());
        assertNull(payment.getCloseRequestedAt());
    }

    @Test
    void paidDuringCancelKeepsLockAndReportsAlreadyPaid() {
        payment.setStatus("waiting").setReqsn("ZS100");
        when(client.query("ZS100")).thenReturn(gateway("SUCCESS", "0000", 128000, true));

        assertServiceException(() -> service.cancelPayment(9L, 1L), PAYMENT_ALREADY_PAID);

        verify(client, never()).close(any());
        // 并发已到账：以支付平台结果为准，不得解除锁定
        assertTrue(intent.getSnapshotLocked());
    }

    @Test
    void untrustedQueryResultKeepsLockAndDoesNotClose() {
        payment.setStatus("waiting").setReqsn("ZS100");
        when(client.query("ZS100")).thenReturn(gateway("SUCCESS", "2000", 128000, false));

        assertServiceException(() -> service.cancelPayment(9L, 1L), PAYMENT_GATEWAY_UNAVAILABLE);

        verify(client, never()).close(any());
        assertTrue(intent.getSnapshotLocked());
        assertEquals("waiting", payment.getStatus());
    }

    @Test
    void closeFailureRecordsPendingCancellationAndKeepsLock() {
        payment.setStatus("waiting").setReqsn("ZS100");
        when(client.query("ZS100")).thenReturn(gateway("SUCCESS", "2000", 128000, true));
        when(client.close("ZS100")).thenReturn(gateway("FAIL", null, null, true));
        // 独立事务记录器会重新加锁读取支付单
        when(paymentIntentMapper.selectByIdForUpdate(100L)).thenReturn(payment);

        assertServiceException(() -> service.cancelPayment(9L, 1L), PAYMENT_CANCEL_PENDING);

        assertTrue(intent.getSnapshotLocked());
        assertNotNull(payment.getCloseRequestedAt());
        assertEquals(1, payment.getCloseAttempts());
    }

    @Test
    void alreadyClosedPaymentIsIdempotentAndUnlocks() {
        payment.setStatus("closed").setReqsn("ZS100");

        service.cancelPayment(9L, 1L);

        assertEquals(false, intent.getSnapshotLocked());
        verify(client, never()).close(any());
    }

    @Test
    void paidPaymentCannotBeCancelled() {
        payment.setStatus("paid");

        assertServiceException(() -> service.cancelPayment(9L, 1L), PAYMENT_ALREADY_PAID);
        assertTrue(intent.getSnapshotLocked());
    }
}
