package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentIntentMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付单「取消待确认」状态的独立事务写入。
 * <p>
 * 取消支付链接的主流程在关单失败时会抛业务异常，异常会回滚同事务内的写操作；
 * 而「已发起取消、结果待确认」这一事实必须落库，销售才能在界面上看到并重试，
 * 因此这里用 {@code REQUIRES_NEW} 单独提交，不受主流程回滚影响。
 */
@Service
public class PaymentClosePendingRecorder {
    @Resource private PaymentIntentMapper paymentIntentMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(PaymentIntentDO payment, String reason, String error) {
        PaymentIntentDO fresh = paymentIntentMapper.selectByIdForUpdate(payment.getId());
        if (fresh == null || "closed".equals(fresh.getStatus()) || "paid".equals(fresh.getStatus())) return;
        String message = StrUtil.blankToDefault(error, "关单结果未知");
        if (fresh.getCloseRequestedAt() == null) fresh.setCloseRequestedAt(LocalDateTime.now());
        fresh.setCloseAttempts((fresh.getCloseAttempts() == null ? 0 : fresh.getCloseAttempts()) + 1);
        fresh.setCloseLastError(cut(message, 480));
        fresh.setCloseReason(reason);
        paymentIntentMapper.updateById(fresh);
    }

    private static String cut(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
