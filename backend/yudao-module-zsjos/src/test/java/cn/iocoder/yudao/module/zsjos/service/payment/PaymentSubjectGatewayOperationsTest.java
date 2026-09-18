package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentRefundDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.*;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpaySigner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.payment.PaymentSubjectTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSubjectGatewayOperationsTest {
    @Test
    void paymentQueryCloseAndRefundUseSameFrozenMerchantOverHttp() throws Exception {
        var received = new ArrayList<Map<String, String>>();
        var keyConfig = new AllinpayProperties();
        keyConfig.setMerchantPrivateKey(subject(10).getMerchantPrivateKey());
        keyConfig.setPlatformPublicKey(subject(10).getPlatformPublicKey());
        var signer = new AllinpaySigner(keyConfig);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway", exchange -> {
            var fields = new LinkedHashMap<String, String>();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            for (String part : body.split("&")) {
                String[] pair = part.split("=", 2);
                fields.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
            }
            received.add(fields);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("retcode", "SUCCESS"); response.put("trxstatus", "0000");
            response.put("trxamt", "100"); response.put("reqsn", fields.get("reqsn"));
            response.put("sign", signer.sign(response));
            byte[] bytes = JsonUtils.toJsonString(response).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        try {
            var props = new AllinpayProperties(); props.setCusid("wrong-global"); props.setAppid("wrong-global");
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/gateway";
            props.setUnitorderPayUrl(url); props.setQueryUrl(url); props.setCloseUrl(url);
            props.setRefundUrl(url); props.setRefundQueryUrl(url);
            var factory = factory(props);
            var payment = payment(subject(10)).setId(1L);
            var client = factory.create(payment);
            assertTrue(client.alipay("REQ1", 100, "测试").isSignatureValid());
            assertTrue(client.query("REQ1").isSignatureValid());
            assertTrue(client.close("REQ1").isSignatureValid());

            var service = new PaymentRefundService();
            var refunds = mock(PaymentRefundMapper.class);
            var payments = mock(PaymentIntentMapper.class);
            ReflectionTestUtils.setField(service, "gatewayFactory", factory);
            ReflectionTestUtils.setField(service, "refundMapper", refunds);
            ReflectionTestUtils.setField(service, "paymentMapper", payments);
            ReflectionTestUtils.setField(service, "eventMapper", mock(PaymentGatewayEventMapper.class));
            var refund = new PaymentRefundDO().setId(2L).setPaymentOrderId(1L).setRefundReqsn("RF1")
                    .setOriginalReqsn("REQ1").setOriginalTrxId("TRX1").setReason("测试")
                    .setRefundAmount(new BigDecimal("1.00")).setStatus("submitting");
            when(refunds.selectByIdForUpdate(2L)).thenReturn(refund);
            when(payments.selectById(1L)).thenReturn(payment);
            assertEquals("succeeded", service.submit(2L).getStatus());
            refund.setStatus("unknown");
            assertEquals("succeeded", service.refresh(2L).getStatus());
            assertEquals(5, received.size());
            for (var fields : received) {
                assertEquals("merchant-10", fields.get("cusid"));
                assertEquals("app-10", fields.get("appid"));
                assertTrue(signer.verify(fields, fields.get("sign")));
            }
            assertEquals("REQ1", received.get(3).get("oldreqsn"));
            assertEquals("RF1", received.get(4).get("reqsn"));

            payment.setSubjectSnapshotJson(null); refund.setStatus("submitting");
            clearInvocations(refunds);
            assertServiceException(() -> service.submit(2L), PAYMENT_SUBJECT_SNAPSHOT_INVALID);
            assertEquals("submitting", refund.getStatus());
            assertEquals(5, received.size());
            verify(refunds, never()).updateById(any(PaymentRefundDO.class));
        } finally { server.stop(0); }
    }
}
