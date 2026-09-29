package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Synthetic transfer, validity and source boundaries supplement read-only MySQL data equivalence. */
class PerformanceConversionSqlTest {
 @Test void fixedPeriodConversionPreservesReceiptReplacementAndSixtyDayBoundary() throws Exception {
  var ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:conversion-"+UUID.randomUUID()+";MODE=MySQL","sa","");
  var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
  config.setEnvironment(new Environment("conversion-fixture",new JdbcTransactionFactory(),ds));
  config.addMapper(PerformanceFactMapper.class);config.addMapper(PerformanceDetailMapper.class);
  try(var session=new SqlSessionFactoryBuilder().build(config).openSession()) {
   try(var sql=session.getConnection().createStatement()) {
    sql.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR,source_type VARCHAR,source_provider_user_id BIGINT,status VARCHAR,lead_category_label_snapshot VARCHAR,sales_stage_label_snapshot VARCHAR)");
    sql.execute("CREATE TABLE zsjos_order(id BIGINT,tenant_id BIGINT,deleted INT,order_no VARCHAR,lead_id BIGINT,formal_sales_user_id BIGINT,submitted_at TIMESTAMP,total_amount DECIMAL(12,2),order_type VARCHAR,status VARCHAR)");
    sql.execute("CREATE TABLE zsjos_lead_assignment_history(id BIGINT,tenant_id BIGINT,deleted INT,lead_id BIGINT,to_owner_user_id BIGINT,occurred_at TIMESTAMP,action_type VARCHAR)");
    sql.execute("CREATE TABLE zsjos_performance_attribution(id BIGINT,fact_type VARCHAR,fact_id BIGINT,tenant_id BIGINT,deleted INT,user_id BIGINT,user_name VARCHAR,received_at TIMESTAMP,assignment_id BIGINT,source_group VARCHAR,channel_label VARCHAR,channel_code VARCHAR,dept_id BIGINT,center_id BIGINT)");
    for(int id=1;id<=8;id++) sql.execute("INSERT INTO zsjos_lead VALUES("+id+",9,0,'LD-"+id+"','"+(id==6||id==7?"sales_self_sourced":"inbound")+"',"+(id==7?"99":"NULL")+",'"+(id==5?"invalid":"valid")+"','分类快照','阶段快照')");
    sql.execute("INSERT INTO zsjos_lead_assignment_history VALUES(1,9,0,1,1,'2025-01-02','accept'),(2,9,0,2,1,'2024-12-01','accept'),(3,9,0,3,1,'2025-01-02','accept'),(4,9,0,3,2,'2025-01-08','transfer'),(5,9,0,4,1,'2025-01-01','accept'),(6,9,0,5,1,'2025-01-05','accept'),(7,9,0,6,1,'2025-01-02','accept'),(8,9,0,7,1,'2025-01-02','accept'),(9,9,0,8,1,'2024-11-02','accept')");
    sql.execute("INSERT INTO zsjos_order VALUES(1,9,0,'O1',1,1,'2025-01-10',100,'first_purchase','effective'),(2,9,0,'O2',1,1,'2025-01-12',200,'first_purchase','effective'),(3,9,0,'O3',2,1,'2025-01-10',300,'first_purchase','effective'),(4,9,0,'O4',3,1,'2025-01-10',400,'first_purchase','effective'),(5,9,0,'O5',4,1,'2025-01-10',500,'first_purchase','effective'),(6,9,0,'O6',5,1,'2025-01-10',600,'first_purchase','effective'),(7,9,0,'O7',6,1,'2025-01-10',700,'first_purchase','effective'),(8,9,0,'O8',7,1,'2025-01-10',800,'first_purchase','effective'),(9,9,0,'O9',8,1,'2025-01-01',900,'first_purchase','effective'),(10,99,0,'FOREIGN',1,1,'2025-01-10',1,'first_purchase','effective'),(11,9,1,'DELETED',1,1,'2025-01-10',1,'first_purchase','effective')");
    sql.execute("INSERT INTO zsjos_performance_attribution SELECT id,'ASSIGNMENT',id,9,0,to_owner_user_id,'snapshot',occurred_at,id,'inbound','channel','channel',10,20 FROM zsjos_lead_assignment_history");
    sql.execute("INSERT INTO zsjos_performance_attribution SELECT id+100,'ORDER',id,tenant_id,0,formal_sales_user_id,'snapshot',NULL,NULL,'inbound','channel','channel',10,20 FROM zsjos_order");
    sql.execute("UPDATE zsjos_performance_attribution SET received_at='2025-01-03' WHERE fact_type='ORDER' AND fact_id=5");
   }
   TenantContextHolder.setTenantId(9L);
   var access=mock(PerformanceAccess.class);when(access.has(anyString())).thenReturn(true);when(access.historicalRowAllowed(any(),any())).thenReturn(true);
   when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(true,true,Set.of()));
   var current=new PerformanceStatisticsService();var reference=new PerformanceDetailReference();
   for(Object service:List.of(current,reference)){ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"facts",session.getMapper(PerformanceFactMapper.class));}
   ReflectionTestUtils.setField(current,"detailQueries",session.getMapper(PerformanceDetailMapper.class));
   for(String scope:List.of("USER","DEPT","CENTER")) {
    var q=new Query();q.setScopeType(scope);q.setScopeId(scope.equals("USER")?1L:scope.equals("DEPT")?10L:20L);q.setMetric("conversion");q.setStart(LocalDate.of(2025,1,1));q.setEnd(LocalDate.of(2025,1,31));q.setPageSize(100);
    var expected=reference.details(q);q.setPageSize(2);var actual=new ArrayList<Detail>();
    for(int page=1;page<=(expected.getTotal()+1)/2;page++){q.setPageNo(page);var result=current.details(q);assertEquals(expected.getTotal(),result.getTotal());actual.addAll(result.getList());}
    assertEquals(new HashSet<>(expected.getList()),new HashSet<>(actual),scope);assertEquals(expected.getList().size(),actual.size());
    assertFalse(actual.stream().anyMatch(x->x.id()==6L||x.id()==8L));
    assertEquals("往期接收有效期内成交",actual.stream().filter(x->x.id()==2L).findFirst().orElseThrow().label());
    assertEquals(LocalDate.of(2025,1,2).atStartOfDay(),actual.stream().filter(x->x.id()==3L).findFirst().orElseThrow().occurredAt());
   }
   var query=new PerformanceDetailQuery().setTenant(9L).setType("DEPT").setId(10L).setAllDepartments(true)
     .setStart(LocalDate.of(2025,1,1).atStartOfDay()).setEnd(LocalDate.of(2025,2,1).atStartOfDay())
     .setConversionLeadIds(java.util.stream.LongStream.rangeClosed(1,8).boxed().collect(java.util.stream.Collectors.toSet()));
   var mapper=session.getMapper(PerformanceDetailMapper.class);
   var small=mapper.conversionReceipts(query).stream().map(PerformanceFact::getId).toList();
   query.setConversionLeadIds(java.util.stream.LongStream.rangeClosed(1,600).boxed().collect(java.util.stream.Collectors.toSet()));
   assertEquals(small,mapper.conversionReceipts(query).stream().map(PerformanceFact::getId).toList(),"Large candidate sets use the equivalent relational fallback");
  } finally {TenantContextHolder.clear();}
 }
}
