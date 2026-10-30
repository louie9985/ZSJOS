package cn.iocoder.yudao.module.bpm.api.task;

import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmMetricQueryTransactionTest {
 @Test void acceptsOnlyMatchingTimedSpringJdbcTransaction(){
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:bpmMetricTx");
  var manager=new DataSourceTransactionManager(ds);
  var configuration=new SpringProcessEngineConfiguration();configuration.setDataSource(ds);configuration.setTransactionManager(manager);
  var engine=mock(ProcessEngine.class);when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
  var guard=new BpmMetricQueryTransaction();ReflectionTestUtils.setField(guard,"processEngine",engine);ReflectionTestUtils.setField(guard,"transactionManager",manager);
  assertThrows(IllegalStateException.class,guard::check);
  var tx=new TransactionTemplate(manager);tx.setTimeout(5);tx.execute(status->{assertDoesNotThrow(guard::check);return null;});
  configuration.setTransactionManager(new DataSourceTransactionManager(ds));
  tx.execute(status->{assertThrows(IllegalStateException.class,guard::check);return null;});
 }
 @Test void rejectsSameManagerButDifferentEngineDataSource(){
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:bpmMetricA");var other=new JdbcDataSource();other.setURL("jdbc:h2:mem:bpmMetricB");
  var manager=new DataSourceTransactionManager(ds);
  var configuration=new SpringProcessEngineConfiguration();configuration.setDataSource(other);configuration.setTransactionManager(manager);
  var engine=mock(ProcessEngine.class);when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
  var guard=new BpmMetricQueryTransaction();ReflectionTestUtils.setField(guard,"processEngine",engine);ReflectionTestUtils.setField(guard,"transactionManager",manager);
  var tx=new TransactionTemplate(manager);tx.setTimeout(5);tx.execute(status->{assertThrows(IllegalStateException.class,guard::check);return null;});
 }
}
