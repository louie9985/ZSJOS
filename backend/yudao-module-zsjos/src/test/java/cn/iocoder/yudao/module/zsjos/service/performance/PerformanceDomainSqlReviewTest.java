package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceDomainSqlReviewTest {
 @Test void legacySalesReadsOverlapUnknownDiagnosticsAndKeepDeliveryExcluded() throws Exception {
  var ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:domain"+UUID.randomUUID()+";MODE=MySQL","sa","");
  var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
  config.setEnvironment(new Environment("domain",new JdbcTransactionFactory(),ds));config.addMapper(PerformanceDetailMapper.class);
  try(var session=new SqlSessionFactoryBuilder().build(config).openSession()){
   var sql=session.getConnection().createStatement();
   sql.execute("CREATE TABLE zsjos_performance_org(tenant_id BIGINT,deleted INT,dept_id BIGINT,business_domain VARCHAR,create_time TIMESTAMP,update_time TIMESTAMP)");
   sql.execute("CREATE TABLE zsjos_performance_attribution(tenant_id BIGINT,deleted INT,user_id BIGINT,user_name VARCHAR,business_domain VARCHAR,org_source VARCHAR,dept_id BIGINT,create_time TIMESTAMP)");
   sql.execute("INSERT INTO zsjos_performance_org VALUES(1,0,10,'sales','2020-01-01','2020-01-01'),(1,0,20,'delivery','2020-01-01','2026-02-01')");
   sql.execute("INSERT INTO zsjos_performance_attribution VALUES(1,0,1,'one','sales','frozen',NULL,'2026-01-01'),(1,0,2,'two','delivery','frozen',10,'2026-01-01'),(1,0,3,'three',NULL,'frozen',10,'2026-01-01'),(1,0,4,'four',NULL,'frozen',20,'2026-01-01'),(1,0,5,'five',NULL,'current',10,'2026-01-01'),(2,0,6,'other','sales','frozen',10,'2026-01-01')");
   var mapper=session.getMapper(PerformanceDetailMapper.class);
   var q=new PerformanceDetailQuery().setTenant(1L).setBusinessDomain("sales").setAllDepartments(true).setMissingDepartment(true);
   assertEquals(Set.of(1L,3L,4L,5L),ids(mapper.historicalPeople(q)));
   q.setBusinessDomain("delivery");assertEquals(Set.of(2L),ids(mapper.historicalPeople(q)));
   q.setUnclassified(true);assertEquals(Set.of(4L,5L),ids(mapper.historicalPeople(q)));
   q.setUnclassified(false).setBusinessDomain("sales").setMissingDepartment(false);assertEquals(Set.of(3L,4L,5L),ids(mapper.historicalPeople(q)));
   q.setAllDepartments(false).setDepartments(Set.of(20L));assertEquals(Set.of(4L),ids(mapper.historicalPeople(q)));
   q.setDepartments(Set.of(99L));assertTrue(mapper.historicalPeople(q).isEmpty());
  }
 }
 private Set<Long> ids(List<PerformanceFact> rows){return rows.stream().map(PerformanceFact::getUserId).collect(java.util.stream.Collectors.toSet());}
}
