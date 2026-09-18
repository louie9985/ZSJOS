package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

final class PaymentSubjectTestData {
    static final KeyPair KEYS = keys();

    static PaymentSubjectDO subject(long id) {
        var subject = new PaymentSubjectDO();
        subject.setId(id); subject.setTenantId(1L); subject.setStatus(0);
        subject.setSubjectCode("subject-" + id); subject.setSubjectName("测试主体" + id);
        subject.setCusid("merchant-" + id); subject.setAppid("app-" + id);
        subject.setMerchantPrivateKey(Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded()));
        subject.setPlatformPublicKey(Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
        return subject;
    }

    static PaymentIntentDO payment(PaymentSubjectDO subject) {
        var payment = new PaymentIntentDO().setSubjectSnapshotJson(JsonUtils.toJsonString(subject));
        payment.setTenantId(1L);
        return payment;
    }

    static PaymentSubjectGatewayFactory factory(AllinpayProperties properties) {
        var factory = new PaymentSubjectGatewayFactory();
        ReflectionTestUtils.setField(factory, "properties", properties);
        return factory;
    }

    private static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
