package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.*;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpaySigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.payment.PaymentSubjectTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSubjectCallbackTest {
    private final PurchaseIntentService service = new PurchaseIntentService();
    private final PaymentRefundService refunds = new PaymentRefundService();
    private final PaymentIntentMapper payments = mock(PaymentIntentMapper.class);
    private final PaymentRefundMapper refundMapper = mock(PaymentRefundMapper.class);
    private final PaymentTransactionMapper transactions = mock(PaymentTransactionMapper.class);
    private final PaymentGatewayEventMapper events = mock(PaymentGatewayEventMapper.class);
    private final PurchaseIntentMapper intents = mock(PurchaseIntentMapper.class);
    private final cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi notifications =
            mock(cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi.class);
    private final PaymentIntentDO payment = payment(subject(10)).setId(1L).setStatus("waiting")
            .setPaymentOrderNo("PAY1").setReqsn("REQ1").setExpectedAmount(new BigDecimal("1.00"));
    private final PaymentRefundDO refund = new PaymentRefundDO().setId(2L).setPaymentOrderId(1L)
            .setRefundReqsn("RF1").setRefundAmount(new BigDecimal("1.00")).setStatus("accepted");

    @BeforeEach
    void setUp() {
        var global = new AllinpayProperties(); global.setCusid("global"); global.setAppid("global");
        var factory = factory(global);
        ReflectionTestUtils.setField(service, "paymentIntentMapper", payments);
        ReflectionTestUtils.setField(service, "purchaseIntentMapper", intents);
        ReflectionTestUtils.setField(service, "notifyBusinessEventApi", notifications);
        payment.setPurchaseIntentId(3L);
        when(intents.selectById(3L)).thenReturn(new PurchaseIntentDO().setId(3L)
                .setOwnerUserId(8L).setPurchaseIntentNo("PI-TEST"));
        ReflectionTestUtils.setField(service, "transactionMapper", transactions);
        ReflectionTestUtils.setField(service, "gatewayEventMapper", events);
        ReflectionTestUtils.setField(service, "gatewayFactory", factory);
        ReflectionTestUtils.setField(service, "allinpayProperties", global);
        ReflectionTestUtils.setField(refunds, "paymentMapper", payments);
        ReflectionTestUtils.setField(refunds, "refundMapper", refundMapper);
        ReflectionTestUtils.setField(refunds, "eventMapper", events);
        ReflectionTestUtils.setField(refunds, "gatewayFactory", factory);
        when(payments.selectByReqsn("REQ1")).thenReturn(payment);
        when(payments.selectByIdForUpdate(1L)).thenReturn(payment);
        when(payments.selectById(1L)).thenReturn(payment);
        when(refundMapper.selectByRefundNo("RF1")).thenReturn(refund);
    }

    @Test
    void configuredMerchantCallbackConfirmsPaymentAndDuplicateDoesNotDoubleBook() {
        var payload = payload("REQ1"); sign(payload);
        service.notify(payload); service.notify(payload);
        assertEquals("paid", payment.getStatus());
        verify(transactions, times(1)).insert(any(PaymentTransactionDO.class));
        verify(events, times(1)).insert(any(PaymentGatewayEventDO.class));
        verify(notifications).publish(argThat(event -> PaymentNotifySceneProvider.PAID.equals(event.getSceneCode())
                && Long.valueOf(1L).equals(event.getTenantId())
                && "payment-paid:1".equals(event.getSourceEventKey())
                && Long.valueOf(8L).equals(event.getPayload().get("ownerUserId"))
                && "PI-TEST".equals(event.getPayload().get("purchase.no"))));
    }

    @Test
    void signedWrongMerchantOrWrongAmountCannotConfirmPayment() {
        for (String field : new String[]{"cusid", "appid", "trxamt"}) {
            var payload = payload("REQ1"); payload.put(field, field.equals("trxamt") ? "101" : "global"); sign(payload);
            assertServiceException(() -> service.notify(payload), PAYMENT_CALLBACK_INVALID);
        }
        verifyNoInteractions(transactions, events, notifications);
        assertEquals("waiting", payment.getStatus());
    }

    @Test
    void invalidSignatureCannotConfirmPayment() {
        var payload = payload("REQ1"); payload.put("sign", "invalid");
        assertServiceException(() -> service.notify(payload), PAYMENT_CALLBACK_INVALID);
        verifyNoInteractions(transactions, events, notifications);
    }

    @Test
    void refundUsesOriginalMerchantAndDoesNotRegressOnDuplicateNotification() {
        var payload = payload("RF1"); sign(payload);
        refunds.notify(payload); refunds.notify(payload);
        assertEquals("succeeded", refund.getStatus());
        verify(refundMapper, times(1)).updateById(refund);
        payload.put("trxstatus", "2000"); sign(payload);
        refunds.notify(payload);
        assertEquals("succeeded", refund.getStatus());
    }

    @Test
    void refundRejectsWrongMerchantAmountSignatureAndReference() {
        for (String field : new String[]{"cusid", "appid", "trxamt", "sign"}) {
            var payload = payload("RF1");
            payload.put(field, field.equals("trxamt") ? "101" : "wrong"); sign(payload);
            if (field.equals("sign")) payload.put("sign", "invalid");
            assertServiceException(() -> refunds.notify(payload), PAYMENT_CALLBACK_INVALID);
        }
        var payload = payload("RF1"); sign(payload); refund.setRefundReqsn("another-refund");
        assertServiceException(() -> refunds.notify(payload), PAYMENT_CALLBACK_INVALID);
        verify(refundMapper, never()).updateById(any(PaymentRefundDO.class));
        verifyNoInteractions(events);
    }

    private Map<String, Object> payload(String reqsn) {
        var result = new LinkedHashMap<String, Object>();
        result.put("cusid", "merchant-10"); result.put("appid", "app-10"); result.put("reqsn", reqsn);
        result.put("trxamt", "100"); result.put("retcode", "SUCCESS"); result.put("trxstatus", "0000");
        result.put("trxid", "TEST-TRX");
        return result;
    }

    private void sign(Map<String, Object> payload) {
        var props = new AllinpayProperties(); props.setMerchantPrivateKey(subject(10).getMerchantPrivateKey());
        payload.put("sign", new AllinpaySigner(props).sign(payload));
    }
}
