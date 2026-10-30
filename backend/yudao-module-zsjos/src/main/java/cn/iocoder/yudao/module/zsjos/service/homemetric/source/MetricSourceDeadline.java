package cn.iocoder.yudao.module.zsjos.service.homemetric.source;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.QueryTimeoutException;
import java.time.Duration;
import java.util.function.Supplier;

/** Uses the application transaction manager so MyBatis/Flowable JDBC statements inherit its deadline. */
@Component
public class MetricSourceDeadline {
    @Resource private PlatformTransactionManager transactionManager;

    public <T> T execute(Duration remaining, Supplier<T> query) {
        if (remaining == null || remaining.toSeconds() < 1) throw new QueryTimeoutException("指标查询预算已耗尽");
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setReadOnly(true);
        // JDBC timeout granularity is seconds: never round up beyond the remaining request budget.
        transaction.setTimeout((int) Math.min(5, remaining.toSeconds()));
        return transaction.execute(status -> query.get());
    }
}
