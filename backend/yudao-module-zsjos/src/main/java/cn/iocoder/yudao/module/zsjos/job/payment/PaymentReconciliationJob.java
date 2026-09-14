package cn.iocoder.yudao.module.zsjos.job.payment;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.zsjos.service.payment.PaymentReconciliationService;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import cn.iocoder.yudao.module.zsjos.service.audit.AuditActionCatalog;
import java.util.Map;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationJob implements JobHandler {
    @Resource private PaymentReconciliationService service;
    @Resource private BusinessAuditService auditService;
    @Override @TenantJob
    public String execute(String param) {
        int limit = 100; try { if (param != null && !param.isBlank()) limit = Integer.parseInt(param.trim()); } catch (NumberFormatException ignored) { }
        int count = service.reconcile(limit);
        auditService.recordExecution(AuditActionCatalog.EXECUTION_QUARTZ, "payment-reconciliation", Map.of("limit", limit, "processed", count));
        return "支付退款对账：处理 " + count + " 条";
    }
}
