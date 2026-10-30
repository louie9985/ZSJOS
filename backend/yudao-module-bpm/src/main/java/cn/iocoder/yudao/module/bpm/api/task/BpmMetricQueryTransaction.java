package cn.iocoder.yudao.module.bpm.api.task;

import jakarta.annotation.Resource;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;

/** Only metric queries require a shared JDBC deadline; ordinary BPM pages retain their existing contract. */
@Component
public class BpmMetricQueryTransaction {
    @Resource private ProcessEngine processEngine;
    @Resource private PlatformTransactionManager transactionManager;
    public void check() {
        if (!(processEngine.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration engine)
                || engine.getTransactionManager()!=transactionManager
                || !(transactionManager instanceof DataSourceTransactionManager jdbc)
                || unwrap(engine.getDataSource())!=unwrap(jdbc.getDataSource())
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !(TransactionSynchronizationManager.getResource(jdbc.getDataSource()) instanceof ConnectionHolder holder)
                || !holder.hasTimeout())
            throw new IllegalStateException("BPM metric query requires the same timed JDBC transaction manager and data source");
        holder.getTimeToLiveInMillis(); // Fails before querying if the shared deadline already expired.
    }
    private DataSource unwrap(DataSource source) {
        while(source instanceof TransactionAwareDataSourceProxy proxy) source=proxy.getTargetDataSource();
        return source;
    }
}
