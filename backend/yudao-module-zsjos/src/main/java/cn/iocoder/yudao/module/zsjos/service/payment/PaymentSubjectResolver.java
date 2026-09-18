package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PaymentSubjectResolver {
    @Resource private PaymentSubjectService paymentSubjectService;
    @Resource private ProductPaymentSubjectService productPaymentSubjectService;
    @Resource private PaymentSubjectGatewayFactory gatewayFactory;

    public PaymentSubjectDO resolve(Collection<Long> productIds) {
        if (productIds.isEmpty() || productIds.stream().anyMatch(java.util.Objects::isNull)) throw exception(PRODUCT_SKU_INVALID);
        Map<Long, Long> configured = productPaymentSubjectService.getPaymentSubjectIdsByProductIds(
                productIds.stream().distinct().toList());
        Map<Long, PaymentSubjectDO> resolved = new LinkedHashMap<>();
        PaymentSubjectDO defaultSubject = null;
        for (Long productId : productIds.stream().distinct().toList()) {
            Long subjectId = configured.get(productId);
            PaymentSubjectDO subject;
            if (subjectId == null) {
                if (defaultSubject == null) defaultSubject = requireDefault();
                subject = defaultSubject;
            } else {
                subject = resolved.get(subjectId);
                if (subject == null) {
                    subject = paymentSubjectService.getPaymentSubject(subjectId);
                    // 有关联但目标失效，不等于未配置，禁止降级至默认主体。
                    gatewayFactory.validateForNewPayment(subject);
                }
            }
            resolved.put(subject.getId(), subject);
        }
        if (resolved.size() == 1) return resolved.values().iterator().next();
        // 多产品最终主体不同按业务约定使用管理员设置的默认主体，不硬编码学校编码。
        return defaultSubject != null ? defaultSubject : requireDefault();
    }

    private PaymentSubjectDO requireDefault() {
        PaymentSubjectDO subject = paymentSubjectService.getDefaultPaymentSubject();
        if (subject == null) throw exception(PAYMENT_DEFAULT_SUBJECT_MISSING);
        gatewayFactory.validateForNewPayment(subject);
        return subject;
    }
}
