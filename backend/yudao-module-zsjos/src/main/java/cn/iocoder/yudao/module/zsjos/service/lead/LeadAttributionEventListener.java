package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.service.performance.PerformanceSnapshotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在业务事务提交后落绩效快照。
 *
 * <p>提交后再执行，埋点失败只影响统计口径，不会回滚客资流转；异常在此吞掉并告警，
 * 与 {@link LeadAssignmentRealtimeListener} 的处理方式保持一致。
 */
@Component
@Slf4j
public class LeadAttributionEventListener {

    @Resource private PerformanceSnapshotService performanceSnapshotService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onActivity(LeadAttributionEvent event) {
        try {
            performanceSnapshotService.activity(event.factType(), event.factId(), event.leadId(), event.userId());
        } catch (Exception exception) {
            log.warn("[onActivity][leadId({}) factType({}) factId({}) attribution snapshot failed]",
                    event.leadId(), event.factType(), event.factId(), exception);
        }
    }
}
