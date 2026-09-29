package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFactMapper;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceMapperTest {
 @Test void automaticProvenanceIsAppliedByActualQueriesInEveryScope() throws Exception {
  var dataSource=new org.apache.ibatis.datasource.unpooled.UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:performance"+UUID.randomUUID()+";MODE=MySQL","sa","");
  var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
  config.setEnvironment(new org.apache.ibatis.mapping.Environment("performance-auto",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),dataSource));
  config.addMapper(PerformanceFactMapper.class);
  try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()) {
   try(var sql=session.getConnection().createStatement()) {
    sql.execute("CREATE ALIAS JSON_EXTRACT FOR 'cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyQueryTest.jsonExtract'");
    sql.execute("CREATE ALIAS JSON_UNQUOTE FOR 'cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyQueryTest.jsonUnquote'");
    sql.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR,owner_user_id BIGINT,ownership_started_at TIMESTAMP,lead_category_label_snapshot VARCHAR,sales_stage_label_snapshot VARCHAR,current_assignment_history_id BIGINT,qualification_round_no INT)");
    sql.execute("INSERT INTO zsjos_lead VALUES (1,9,0,'LD-SQL',1,CURRENT_TIMESTAMP,'分类快照','阶段快照',1,1)");
    sql.execute("CREATE TABLE zsjos_performance_attribution(fact_type VARCHAR,fact_id BIGINT,tenant_id BIGINT,deleted INT,dept_id BIGINT,center_id BIGINT,completed_at TIMESTAMP,outcome VARCHAR,assignment_id BIGINT)");
    for(String table:List.of("zsjos_lead_follow_up_record","zsjos_opportunity_follow_up_record")) {
     sql.execute("CREATE TABLE "+table+"(id BIGINT,tenant_id BIGINT,deleted INT,lead_id BIGINT,operator_user_id BIGINT,occurred_at TIMESTAMP,result_label_snapshot VARCHAR)");
     sql.execute("INSERT INTO "+table+" VALUES(1,9,0,1,1,CURRENT_TIMESTAMP,'意向'),(2,9,0,1,1,CURRENT_TIMESTAMP,'意向')");
    }
    sql.execute("CREATE TABLE zsjos_business_event(tenant_id BIGINT,deleted INT,aggregate_type VARCHAR,aggregate_id BIGINT,event_type VARCHAR,related_object_refs VARCHAR)");
    sql.execute("INSERT INTO zsjos_business_event VALUES(9,0,'lead',1,'lead_follow_up_recorded','{\"generationSource\":\"sales_self_sourced_auto\",\"followUpRecordId\":1}')");
    sql.execute("CREATE TABLE zsjos_business_task(id BIGINT,tenant_id BIGINT,deleted INT,biz_id BIGINT,assignee_id BIGINT,create_time TIMESTAMP,due_at TIMESTAMP,completed_at TIMESTAMP,cancelled_at TIMESTAMP,idempotency_key VARCHAR,status VARCHAR,task_type VARCHAR,cancel_reason VARCHAR,payload VARCHAR)");
    sql.execute("INSERT INTO zsjos_business_task VALUES(1,9,0,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'lead-qualification:1:1','completed','lead_qualification',NULL,'{\"generationSource\":\"sales_self_sourced_auto\"}'),(2,9,0,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,NULL,'reminder','pending','lead_follow_up_reminder',NULL,'{}')");
    for(String type:List.of("FOLLOW_UP","OPPORTUNITY_FU","TASK"))for(int id=1;id<=2;id++)sql.execute("INSERT INTO zsjos_performance_attribution VALUES('"+type+"',"+id+",9,0,20,30,NULL,NULL,1)");
   }
   var mapper=session.getMapper(PerformanceFactMapper.class);
   for(String scope:List.of("SELF","USER","DEPT","CENTER")) {
    long id=scope.equals("DEPT")?20L:scope.equals("CENTER")?30L:1L;
    var follow=mapper.followUps(9L,scope,id);assertEquals(3,follow.size(),scope);
    assertEquals(1,follow.stream().filter(row->row.getId()==1L).count(),"Opportunity collision remains manual");
    var tasks=mapper.tasks(9L,scope,id);assertEquals(2,tasks.size(),scope);
    assertEquals(1,tasks.stream().filter(row->"sales_self_sourced_auto".equals(row.getGenerationSource())).count());
    assertTrue(mapper.followUps(99L,scope,id).isEmpty());assertTrue(mapper.tasks(99L,scope,id).isEmpty());
   }
  }
 }
 @Test void statementsRetainScopeAndTenant(){var config=new Configuration();new MapperAnnotationBuilder(config,PerformanceFactMapper.class).parse();for(String scope:List.of("SELF","USER","DEPT","CENTER"))for(String method:List.of("orders","receipts","products","tasks","assignments","followUps")){var sql=config.getMappedStatement(PerformanceFactMapper.class.getName()+"."+method).getBoundSql(Map.of("tenant",991L,"type",scope,"id",12L,"users",List.of(12L))).getSql();assertTrue(sql.contains("tenant_id"),sql);assertFalse(sql.contains("IN ()"),sql);if(List.of("orders","receipts","products").contains(method)){if("DEPT".equals(scope))assertTrue(sql.contains("a.dept_id=?"));if("CENTER".equals(scope))assertTrue(sql.contains("a.center_id=?"));}}}

 @Test void conversionSourceFieldsAreProjectedWithoutFilteringFinancialFacts() {
  var config=new Configuration();new MapperAnnotationBuilder(config,PerformanceFactMapper.class).parse();
  for(String scope:List.of("SELF","USER","DEPT","CENTER")) for(String method:List.of("orders","receipts")) {
   var sql=config.getMappedStatement(PerformanceFactMapper.class.getName()+"."+method).getBoundSql(Map.of("tenant",991L,"type",scope,"id",12L)).getSql();
   assertTrue(sql.contains("l.source_type,l.source_provider_user_id"));
   assertFalse(sql.contains("source_provider_user_id IS NOT NULL"));
   if(method.equals("orders")) assertTrue(sql.contains("LEFT JOIN zsjos_lead l ON l.id=o.lead_id AND l.tenant_id=o.tenant_id AND l.deleted=0"));
  }
 }
}
