package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.PaymentSubjectCodes.*;

@Service
public class PaymentSubjectResolver {
    @Resource private PaymentSubjectService paymentSubjectService;
    @Resource private ProductPaymentSubjectService productPaymentSubjectService;
    @Resource private PaymentSubjectGatewayFactory gatewayFactory;

    public PaymentSubjectDO resolve(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty() || productIds.stream().anyMatch(java.util.Objects::isNull)) throw exception(PRODUCT_SKU_INVALID);
        Map<Long, Long> configured = productPaymentSubjectService.getPaymentSubjectIdsByProductIds(
                productIds.stream().distinct().toList());
        Map<Long, PaymentSubjectDO> resolved = new LinkedHashMap<>();
        PaymentSubjectDO schoolSubject = null;
        for (Long productId : productIds.stream().distinct().toList()) {
            Long subjectId = configured.get(productId);
            PaymentSubjectDO subject;
            if (subjectId == null) {
                if (schoolSubject == null) schoolSubject = requireSchool();
                subject = schoolSubject;
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
        // 所有参与主体先通过校验；任何主体冲突均由公司整笔收款，不能掩盖失效关联。
        PaymentSubjectDO company = paymentSubjectService.getPaymentSubjectByCode(COMPANY);
        if (company == null) throw exception(PAYMENT_COMPANY_SUBJECT_MISSING);
        gatewayFactory.validateForNewPayment(company);
        return company;
    }

    private PaymentSubjectDO requireSchool() {
        PaymentSubjectDO subject = paymentSubjectService.getPaymentSubjectByCode(SCHOOL);
        if (subject == null) throw exception(PAYMENT_SCHOOL_SUBJECT_MISSING);
        gatewayFactory.validateForNewPayment(subject);
        return subject;
    }
}
