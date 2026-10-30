package cn.iocoder.yudao.module.zsjos.service.homemetric.source;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.h2.jdbcx.JdbcDataSource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class MetricSourceDeadlineTest {
 @Test void jdbcStatementsSeeDeadlineBeforeExecutionAndTransactionIsReleased(){
  var dataSource=new JdbcDataSource();dataSource.setURL("jdbc:h2:mem:metricDeadline");
  var manager=new DataSourceTransactionManager(dataSource);var deadline=new MetricSourceDeadline();
  ReflectionTestUtils.setField(deadline,"transactionManager",manager);
  // Flowable's Spring configuration wraps its data source with this same Spring proxy.
  var proxy=new TransactionAwareDataSourceProxy(dataSource);
  var value=deadline.execute(Duration.ofMillis(2900),()->{
   assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
   try(var connection=proxy.getConnection();var statement=connection.prepareStatement("SELECT 1")){
    assertTrue(statement.getQueryTimeout()>0);assertTrue(statement.getQueryTimeout()<=2);
    try(var rows=statement.executeQuery()){assertTrue(rows.next());return rows.getInt(1);}
   }catch(java.sql.SQLException e){throw new IllegalStateException(e);}
  });
  assertEquals(1,value);assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
 }
 @Test void exhaustedBudgetDoesNotStartAnySourceWork(){
  var called=new AtomicBoolean();
  assertThrows(QueryTimeoutException.class,()->new MetricSourceDeadline().execute(Duration.ofMillis(999),()->{called.set(true);return 1;}));
  assertFalse(called.get());
 }
}
