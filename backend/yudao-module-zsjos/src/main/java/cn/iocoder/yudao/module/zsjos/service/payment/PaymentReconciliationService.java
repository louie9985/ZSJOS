package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentRefundDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentRefundMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PaymentReconciliationService {
    @Resource private PaymentRefundMapper refundMapper;
    @Resource private PaymentRefundService refundService;

    public int reconcile(int limit) {
        List<PaymentRefundDO> rows = refundMapper.selectDueForReconcile(Math.max(1, Math.min(limit, 500)));
        int count = 0;
        for (PaymentRefundDO row : rows) {
            try { refundService.refresh(row.getId()); count++; } catch (RuntimeException ignored) { }
        }
        return count;
    }
}
