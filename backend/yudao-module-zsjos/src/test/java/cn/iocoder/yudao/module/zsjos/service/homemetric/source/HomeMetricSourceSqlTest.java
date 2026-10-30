package cn.iocoder.yudao.module.zsjos.service.homemetric.source;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.service.homemetric.*;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementServiceImpl;
import cn.iocoder.yudao.module.zsjos.service.order.*;
import cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceReadService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackSearchService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.MetricSourceMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes actual adapter SQL in H2/MySQL mode; this does not replace target-MySQL acceptance. */
@ExtendWith(MockitoExtension.class)
class HomeMetricSourceSqlTest {
 @InjectMocks HomeMetricSourcesImpl service;
 @Mock PermissionApi permissions; @Mock AdvancedFilterService filters;
 @Mock LeadManagementServiceImpl leads; @Mock SalesOrderObjectPermissionService orders;
 @Mock StudentServiceReadService students; @Mock CashbackSearchService cashbacks;
 @Mock MetricSourceMapper mapper; @Mock ExistingPageMetricSources existing; @Mock BuiltinMetricSources builtin;
 Connection connection;
 @BeforeEach void setup() throws Exception {
  TenantContextHolder.setTenantId(1L);
  connection=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=VALUE");
  lenient().when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
  when(mapper.aggregate(any())).thenAnswer(call->execute(call.getArgument(0)));
  sql("CREATE TABLE zsjos_order(id BIGINT,order_no VARCHAR,tenant_id BIGINT,deleted INT,submitter_user_id BIGINT,total_amount DECIMAL(18,2),status VARCHAR,submitted_at TIMESTAMP,effective_at TIMESTAMP,lead_id BIGINT)");
  sql("INSERT INTO zsjos_order VALUES(1,'O1',1,0,7,100,'effective','2026-10-28 00:00:00',NULL,NULL),(2,'O2',1,0,7,100,'effective','2026-10-28 12:00:00',NULL,NULL),(3,'O3',1,0,8,200,'effective','2026-10-28 12:00:00',NULL,NULL),(4,'O4',2,0,7,400,'effective','2026-10-28 12:00:00',NULL,NULL),(5,'O5',1,1,7,500,'effective','2026-10-28 12:00:00',NULL,NULL),(6,'O6',1,0,7,600,'effective','2026-10-29 00:00:00',NULL,NULL)");
 }
 @AfterEach void cleanup() throws Exception {connection.close();TenantContextHolder.clear();}
 void sql(String text)throws Exception{try(var statement=connection.createStatement()){statement.execute(text);}}
 BigDecimal execute(AdvancedFilterQuery query)throws Exception{
  var matcher=Pattern.compile("#\\{query.parameters.([^}]+)}").matcher(query.getWhereSql());
  var values=new ArrayList<Object>();var sql=new StringBuffer();
  while(matcher.find()){values.add(query.getParameters().get(matcher.group(1)));matcher.appendReplacement(sql,"?");}matcher.appendTail(sql);
  try(var statement=connection.prepareStatement(sql.toString())){for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));try(var rows=statement.executeQuery()){assertTrue(rows.next());return rows.getBigDecimal(1);}}
 }
 MetricFormula.Result aggregate(String source,MetricAggregate aggregate,String field,MetricPeriod.Range range){
  var op=new MetricEvaluation.Operand("a",source,aggregate,field,range==null?MetricPeriod.Kind.ALL:MetricPeriod.Kind.DAY,false,null,MetricPeriod.Comparison.CURRENT);
  var item=new HomeMetricSpec.Operand(op,new AdvancedFilterGroupReqVO(),"student".equals(source)?"activatedAt":"submittedAt");
  var spec=new HomeMetricSpec("t","测试","","",0,"t:read",List.of(),List.of(),"MODAL","a",false,2,List.of(item));
  return service.bind(spec,7L).aggregate(op,range);
 }
 @Test void sameAmountDifferentOrdersBothContributeAndEndIsExclusive(){
  when(orders.resolveManagementScope(7L)).thenReturn(new SalesOrderManagementScope(false,true,Set.of(),Set.of(7L)));
  var range=new MetricPeriod.Range(LocalDateTime.parse("2026-10-28T00:00:00"),LocalDateTime.parse("2026-10-29T00:00:00"));
  assertEquals(0,new BigDecimal("200").compareTo(aggregate("order",MetricAggregate.SUM,"totalAmount",range).value()));
  assertEquals(0,new BigDecimal("2").compareTo(aggregate("order",MetricAggregate.COUNT,null,range).value()));
 }
 @Test void emptyOrderScopeReturnsZeroRatherThanTenantTotal(){
  when(orders.resolveManagementScope(7L)).thenReturn(SalesOrderManagementScope.empty());
  assertEquals(BigDecimal.ZERO,aggregate("order",MetricAggregate.SUM,"totalAmount",null).value());
 }
 @Test void leadScopeIncludesOwnedRecycledAndSubmittedButNotOtherOwnerOrTenant()throws Exception{
  sql("CREATE TABLE zsjos_lead(id BIGINT,lead_no VARCHAR,tenant_id BIGINT,deleted INT,provider_owner_type VARCHAR,provider_owner_id BIGINT,owner_user_id BIGINT,assignment_status VARCHAR,recycle_source_owner_user_id BIGINT,status VARCHAR,follow_up_count INT,submitted_at TIMESTAMP)");
  sql("INSERT INTO zsjos_lead VALUES(1,'L1',1,0,'system_user',9,8,'assigned',NULL,'submitted',1,NULL),(2,'L2',1,0,'partner',9,7,'assigned',NULL,'submitted',1,NULL),(3,'L3',1,0,'partner',9,NULL,'recycle_pending',7,'submitted',1,NULL),(4,'L4',1,0,'partner',9,8,'assigned',NULL,'submitted',1,NULL),(5,'L5',2,0,'system_user',9,7,'assigned',NULL,'submitted',1,NULL)");
  when(leads.metricVisibilityScope(7L)).thenReturn(new LeadManagementServiceImpl.LeadVisibilityScope(List.of(9L),List.of(7L),false));
  assertEquals(0,new BigDecimal("3").compareTo(aggregate("lead",MetricAggregate.COUNT,null,null).value()));
  when(leads.metricVisibilityScope(7L)).thenReturn(new LeadManagementServiceImpl.LeadVisibilityScope(List.of(),List.of(),false));
  assertEquals(0,aggregate("lead",MetricAggregate.COUNT,null,null).value().signum());
 }
 @Test void multipleVisibleServicesCountOnePersonAndHiddenServiceDoesNotGrantVisibility()throws Exception{
  sql("CREATE TABLE zsjos_service_relation(id BIGINT,person_id BIGINT,tenant_id BIGINT,deleted INT,teaching_delivery INT,order_id BIGINT,order_item_id BIGINT,class_id BIGINT,owner_user_id BIGINT,activated_at TIMESTAMP)");
  sql("CREATE TABLE zsjos_person(id BIGINT,person_no VARCHAR,tenant_id BIGINT,deleted INT)");
  sql("CREATE TABLE zsjos_order_item(id BIGINT,order_id BIGINT,tenant_id BIGINT,deleted INT)");
  sql("CREATE TABLE zsjos_lead(id BIGINT,lead_no VARCHAR,tenant_id BIGINT,deleted INT)");
  sql("CREATE TABLE zsjos_delivery_class(id BIGINT,tenant_id BIGINT,deleted INT)");
  sql("INSERT INTO zsjos_person VALUES(1,'P1',1,0),(2,'P2',1,0),(3,'P3',1,0),(4,'P4',2,0)");
  sql("INSERT INTO zsjos_service_relation VALUES(1,1,1,0,1,NULL,NULL,NULL,7,NULL),(2,1,1,0,1,NULL,NULL,NULL,7,NULL),(3,2,1,0,1,NULL,NULL,NULL,7,NULL),(4,3,1,0,1,NULL,NULL,NULL,8,NULL),(5,4,2,0,1,NULL,NULL,NULL,7,NULL)");
  when(students.metricQuery(eq(7L),any())).thenReturn(new AdvancedFilterQuery("sr.owner_user_id=#{query.parameters.subject}",Map.of("subject",7L,"tenantId",1L)));
  assertEquals(0,new BigDecimal("2").compareTo(aggregate("student",MetricAggregate.COUNT,null,null).value()));
 }
 @Test void withdrawalPersonalScopeCannotSeeAnotherApplicantButFinanceCan()throws Exception{
  sql("CREATE TABLE zsjos_withdrawal(id BIGINT,withdrawal_no VARCHAR,tenant_id BIGINT,deleted INT,applicant_user_id BIGINT,status VARCHAR,application_amount DECIMAL,approved_amount DECIMAL,submitted_at TIMESTAMP)");
  sql("INSERT INTO zsjos_withdrawal VALUES(1,'W1',1,0,7,'pending',100,NULL,NULL),(2,'W2',1,0,8,'pending',200,NULL,NULL),(3,'W3',2,0,7,'pending',400,NULL,NULL)");
  when(permissions.hasAnyPermissions(7L,"zsjos:withdrawal:finance-query","zsjos:withdrawal:admin-query")).thenReturn(false);
  assertEquals(0,new BigDecimal("100").compareTo(aggregate("withdrawal",MetricAggregate.SUM,"applicationAmount",null).value()));
  when(permissions.hasAnyPermissions(7L,"zsjos:withdrawal:finance-query","zsjos:withdrawal:admin-query")).thenReturn(true);
  assertEquals(0,new BigDecimal("300").compareTo(aggregate("withdrawal",MetricAggregate.SUM,"applicationAmount",null).value()));
 }
 @Test void cashbackUsesRealBeneficiaryScopeAndFinanceReadContract()throws Exception{
  sql("CREATE TABLE zsjos_cashback(id BIGINT,cashback_no VARCHAR,tenant_id BIGINT,deleted INT,beneficiary_user_id BIGINT,status VARCHAR,amount DECIMAL,base_amount DECIMAL,generated_at TIMESTAMP)");
  sql("INSERT INTO zsjos_cashback VALUES(1,'C1',1,0,7,'available',20,100,NULL),(2,'C2',1,0,8,'available',30,100,NULL),(3,'C3',2,0,7,'available',90,100,NULL),(4,'C4',1,1,7,'available',90,100,NULL)");
  var real=new CashbackSearchService();
  org.springframework.test.util.ReflectionTestUtils.setField(real,"metricPermissions",permissions);
  org.springframework.test.util.ReflectionTestUtils.setField(real,"filters",filters);
  when(cashbacks.metricQuery(any(),eq(7L))).thenAnswer(call->real.metricQuery(call.getArgument(0),7L));
  when(permissions.hasAnyPermissions(7L,"zsjos:cashback:finance-query")).thenReturn(false);
  assertEquals(0,new BigDecimal("20").compareTo(aggregate("cashback",MetricAggregate.SUM,"amount",null).value()));
  when(permissions.hasAnyPermissions(7L,"zsjos:cashback:finance-query")).thenReturn(true);
  assertEquals(0,new BigDecimal("50").compareTo(aggregate("cashback",MetricAggregate.SUM,"amount",null).value()));
 }
}
