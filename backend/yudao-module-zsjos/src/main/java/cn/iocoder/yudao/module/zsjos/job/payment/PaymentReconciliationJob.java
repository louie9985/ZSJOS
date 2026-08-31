package cn.iocoder.yudao.module.zsjos.job.payment;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.zsjos.service.payment.PaymentReconciliationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationJob implements JobHandler {
    @Resource private PaymentReconciliationService service;
    @Override @TenantJob
    public String execute(String param) {
        int limit = 100; try { if (param != null && !param.isBlank()) limit = Integer.parseInt(param.trim()); } catch (NumberFormatException ignored) { }
        return "支付退款对账：处理 " + service.reconcile(limit) + " 条";
    }
}
