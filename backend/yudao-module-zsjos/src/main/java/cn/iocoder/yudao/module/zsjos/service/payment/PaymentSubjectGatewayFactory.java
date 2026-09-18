package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayClient;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpaySigner;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Component
public class PaymentSubjectGatewayFactory {
    @Resource private AllinpayProperties properties;

    public void validateForNewPayment(PaymentSubjectDO subject) {
        if (subject == null) throw exception(PAYMENT_SUBJECT_NOT_EXISTS);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(subject.getStatus())) {
            throw exception(PAYMENT_SUBJECT_DISABLED);
        }
        validateConfiguration(subject);
    }

    public AllinpayClient create(PaymentIntentDO payment) {
        return new AllinpayClient(toProperties(readSnapshot(payment)));
    }

    public boolean matchesMerchant(PaymentIntentDO payment, Map<String, ?> payload) {
        PaymentSubjectDO snapshot = readSnapshot(payment);
        return Objects.equals(snapshot.getCusid(), text(payload.get("cusid")))
                && Objects.equals(snapshot.getAppid(), text(payload.get("appid")));
    }

    private PaymentSubjectDO readSnapshot(PaymentIntentDO payment) {
        if (payment == null || StrUtil.isBlank(payment.getSubjectSnapshotJson())) {
            throw exception(PAYMENT_SUBJECT_SNAPSHOT_INVALID);
        }
        // 普通 JSON 解析器失败时会打印输入，主体快照含密钥，必须使用不记录原文的入口。
        PaymentSubjectDO snapshot = JsonUtils.parseObjectQuietly(payment.getSubjectSnapshotJson(), PaymentSubjectDO.class);
        if (snapshot == null || (snapshot.getTenantId() != null
                && !Objects.equals(snapshot.getTenantId(), payment.getTenantId()))) {
            throw exception(PAYMENT_SUBJECT_SNAPSHOT_INVALID);
        }
        // 存量交易不重新读取主体状态或当前配置，停用主体后仍须能查单、关单和原路退款。
        validateConfiguration(snapshot);
        return snapshot;
    }

    private void validateConfiguration(PaymentSubjectDO subject) {
        if (StrUtil.hasBlank(subject.getCusid(), subject.getAppid(), subject.getMerchantPrivateKey(),
                subject.getPlatformPublicKey())) throw exception(PAYMENT_SUBJECT_CONFIG_INVALID);
        try {
            new AllinpaySigner(toProperties(subject)).validateKeys();
        } catch (RuntimeException ex) {
            throw exception(PAYMENT_SUBJECT_CONFIG_INVALID);
        }
    }

    private AllinpayProperties toProperties(PaymentSubjectDO subject) {
        AllinpayProperties result = new AllinpayProperties();
        result.setEnabled(properties.isEnabled());
        result.setCusid(subject.getCusid());
        result.setAppid(subject.getAppid());
        result.setOrgid(subject.getOrgid());
        result.setMerchantPrivateKey(subject.getMerchantPrivateKey());
        result.setPlatformPublicKey(subject.getPlatformPublicKey());
        // 只继承通用连接配置，禁止继承全局商户身份或密钥文件路径。
        result.setUnionorderUrl(properties.getUnionorderUrl());
        result.setUnitorderPayUrl(properties.getUnitorderPayUrl());
        result.setQueryUrl(properties.getQueryUrl());
        result.setCloseUrl(properties.getCloseUrl());
        result.setRefundUrl(properties.getRefundUrl());
        result.setRefundQueryUrl(properties.getRefundQueryUrl());
        result.setRefundVersion(properties.getRefundVersion());
        result.setNotifyUrl(properties.getNotifyUrl());
        result.setRefundNotifyUrl(properties.getRefundNotifyUrl());
        result.setReturnUrl(properties.getReturnUrl());
        result.setConnectTimeoutSeconds(properties.getConnectTimeoutSeconds());
        result.setReadTimeoutSeconds(properties.getReadTimeoutSeconds());
        return result;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
