package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentIntentDO;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpaySigner;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.payment.PaymentSubjectTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentSubjectGatewayFactoryTest {
    private final AllinpayProperties global = new AllinpayProperties();
    private final PaymentSubjectGatewayFactory factory = factory(global);

    @Test
    void generatedFormUsesFrozenMerchantAndKeyEvenAfterConfigurationChanges() {
        global.setCusid("global-merchant"); global.setAppid("global-app");
        global.setMerchantPrivateKey("invalid-global-key");
        global.setNotifyUrl("https://example.invalid/notify");
        var current = subject(10);
        var frozen = payment(current);
        current.setCusid("changed"); current.setAppid("changed"); current.setStatus(1);
        var fields = factory.create(frozen).unionOrder("REQ1", 100, "测试", "", LocalDateTime.now().plusHours(1));
        assertEquals("merchant-10", fields.get("cusid"));
        assertEquals("app-10", fields.get("appid"));
        assertEquals(global.getNotifyUrl(), fields.get("notify_url"));
        var verification = new AllinpayProperties();
        verification.setPlatformPublicKey(subject(10).getPlatformPublicKey());
        assertTrue(new AllinpaySigner(verification).verify(fields, fields.get("sign")));
        assertTrue(factory.matchesMerchant(frozen, Map.of("cusid", "merchant-10", "appid", "app-10")));
        assertFalse(factory.matchesMerchant(frozen, Map.of("cusid", "global-merchant", "appid", "global-app")));
    }

    @Test
    void absentMalformedAndNullSnapshotsNeverUseGlobalCredentials() {
        assertServiceException(() -> factory.create(null), PAYMENT_SUBJECT_SNAPSHOT_INVALID);
        for (String value : new String[]{null, "", " ", "{broken", "null"}) {
            var payment = new PaymentIntentDO().setSubjectSnapshotJson(value);
            assertServiceException(() -> factory.create(payment), PAYMENT_SUBJECT_SNAPSHOT_INVALID);
        }
    }

    @Test
    void incompleteSnapshotNeverUsesGlobalKeysOrMerchant() {
        var validGlobal = subject(99);
        global.setCusid(validGlobal.getCusid()); global.setAppid(validGlobal.getAppid());
        global.setMerchantPrivateKey(validGlobal.getMerchantPrivateKey());
        global.setPlatformPublicKey(validGlobal.getPlatformPublicKey());
        for (int field = 0; field < 5; field++) {
            var broken = subject(10);
            switch (field) {
                case 0 -> broken.setCusid(null);
                case 1 -> broken.setAppid("");
                case 2 -> broken.setMerchantPrivateKey(null);
                case 3 -> broken.setPlatformPublicKey(" ");
                case 4 -> broken.setPlatformPublicKey("bad-key");
            }
            assertServiceException(() -> factory.create(payment(broken)), PAYMENT_SUBJECT_CONFIG_INVALID);
        }
    }

    @Test
    void snapshotFromAnotherTenantIsRejected() {
        var payment = payment(subject(10)); payment.setTenantId(2L);
        assertServiceException(() -> factory.create(payment), PAYMENT_SUBJECT_SNAPSHOT_INVALID);
    }

    @Test
    void historicalSubjectStatusDoesNotBlockOriginalMerchantOperations() {
        var historical = subject(10); historical.setStatus(1);
        assertNotNull(factory.create(payment(historical)));
        assertServiceException(() -> factory.validateForNewPayment(historical), PAYMENT_SUBJECT_DISABLED);
    }
}
