package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceOrgDO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** SELECT only in one read-only MySQL snapshot; failures suppress business values. */
@EnabledIfEnvironmentVariable(named="PERFORMANCE_TEST_MYSQL_URL",matches=".+")
class PerformanceReportMySqlTest {
 @Test void reportEquivalence() throws Exception {
  try(var c=DriverManager.getConnection(System.getenv("PERFORMANCE_TEST_MYSQL_URL"),System.getenv("PERFORMANCE_TEST_MYSQL_USER"),System.getenv("PERFORMANCE_TEST_MYSQL_PASSWORD"))){
   c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);
   var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
   config.setEnvironment(new Environment("report-read-only",new JdbcTransactionFactory(),new SingleConnectionDataSource(c,true)));
   config.addMapper(PerformanceFactMapper.class);config.addMapper(PerformanceDetailMapper.class);config.addMapper(PerformanceReportMapper.class);
   try(var session=new SqlSessionFactoryBuilder().build(config).openSession(c)){
    var access=mock(PerformanceAccess.class);var targets=mock(PerformanceTargetService.class);var batch=mock(PerformanceTargetService.Batch.class);
    var clock=Clock.fixed(LocalDateTime.of(2026,10,8,12,0).atZone(PerformancePeriods.ZONE).toInstant(),PerformancePeriods.ZONE);
    var current=new PerformanceStatisticsService();var previous=new PerformanceReportReference();
    for(var service:List.of(current,previous)){ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"targets",targets);ReflectionTestUtils.setField(service,"facts",session.getMapper(PerformanceFactMapper.class));ReflectionTestUtils.setField(service,"clock",clock);}
    ReflectionTestUtils.setField(current,"detailQueries",session.getMapper(PerformanceDetailMapper.class));ReflectionTestUtils.setField(current,"reports",session.getMapper(PerformanceReportMapper.class));
    when(access.has(anyString())).thenReturn(true);when(targets.batch(anyCollection(),anyCollection())).thenReturn(batch);
    org.mockito.stubbing.Answer<Target> target=call->new Target(null,call.getArgument(0),call.getArgument(1),"fixture",call.getArgument(2),call.getArgument(3),BigDecimal.ZERO,BigDecimal.ZERO,null,null,false,false,1,null);
    when(targets.resolve(anyString(),anyLong(),anyString(),any())).thenAnswer(target);when(batch.resolve(anyString(),anyLong(),anyString(),any())).thenAnswer(target);
    var users=new HashMap<Long,AdminUserRespDTO>();
    try(var sql=c.createStatement();var rs=sql.executeQuery("SELECT id,dept_id,nickname,status FROM system_users WHERE tenant_id=1 AND deleted=0")){while(rs.next()){var u=new AdminUserRespDTO();u.setId(rs.getLong(1));u.setDeptId(rs.getObject(2,Long.class));u.setNickname(rs.getString(3));u.setStatus(rs.getInt(4));users.put(u.getId(),u);}}
    var organizations=new ArrayList<PerformanceOrgDO>();try(var sql=c.createStatement();var rs=sql.executeQuery("SELECT dept_id,center_id,kind FROM zsjos_performance_org WHERE tenant_id=1 AND deleted=0")){while(rs.next()){var o=new PerformanceOrgDO();o.setDeptId(rs.getLong(1));o.setCenterId(rs.getLong(2));o.setKind(rs.getString(3));organizations.add(o);}}
    when(access.orgs()).thenReturn(organizations);when(access.mapping(any())).thenAnswer(call->organizations.stream().filter(o->Objects.equals(o.getDeptId(),call.getArgument(0))).findFirst().orElse(null));
    when(access.sales()).thenReturn(users.values().stream().filter(u->u.getStatus()==0).toList());when(access.user(any())).thenAnswer(call->users.get(call.getArgument(0)));
    when(access.users(anyCollection())).thenAnswer(call->((Collection<Long>)call.getArgument(0)).stream().map(users::get).filter(Objects::nonNull).toList());
    when(access.enabledUserIds(anyCollection())).thenAnswer(call->((Collection<Long>)call.getArgument(0)).stream().filter(id->users.containsKey(id)&&users.get(id).getStatus()==0).collect(Collectors.toSet()));
    TenantContextHolder.setTenantId(1L);int comparisons=0;
    for(String type:List.of("SELF","USER","DEPT","CENTER")){
     String column="DEPT".equals(type)?"dept_id":"CENTER".equals(type)?"center_id":"user_id";Long id;
     try(var sql=c.createStatement();var rs=sql.executeQuery("SELECT "+column+" FROM zsjos_performance_attribution WHERE tenant_id=1 AND deleted=0 AND "+column+" IS NOT NULL GROUP BY "+column+" ORDER BY COUNT(*) DESC LIMIT 1")){if(!rs.next())continue;id=rs.getLong(1);}
     boolean missing=Set.of("SELF","USER").contains(type);
     for(boolean denied:List.of(false,true)){
      when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(!denied,!denied&&missing,Set.of()));
      when(access.historicalRowAllowed(any(),any())).thenAnswer(call->!denied&&(missing||call.getArgument(1)!=null));when(access.departmentAllowed(any())).thenReturn(!denied);
      var oldOverview=previous.overview(query(type,id,null));
      session.clearCache();clearInvocations(access);var newOverview=current.overview(query(type,id,null));
      verify(access,times(1)).historicalScope(any());verify(access,never()).historicalRowAllowed(any(),any());
      same(oldOverview,newOverview,type+" overview denied="+denied);comparisons++;
      for(String period:List.of("today","month","lastMonth","last90","year")){
       var analysis=current.analysis(query(type,id,period));same(previous.analysis(query(type,id,period)),analysis,type+" analysis "+period+" denied="+denied);comparisons++;
       for(var source:analysis.sources()){
        var detail=query(type,id,period);detail.setMetric("orders");detail.setDimension("source");detail.setGroupKey(source.key());detail.setPageSize(1000000);
        var page=current.details(detail);assertEquals(source.count(),page.getTotal());
        same(source.amount(),page.getList().stream().map(Detail::amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add),"source drilldown amount");
        if("unknown|其他".equals(source.key())){detail.setGroupKey("unknown|历史来源缺失");same(page.getList(),current.details(detail).getList(),"legacy unknown source key");}
       }
      }
      for(int year:List.of(2025,2026,2027)){var q=query(type,id,null);q.setYear(year);same(previous.history(q),current.history(q),type+" history");comparisons++;}
      var q=query(type,id,null);q.setStart(LocalDate.of(2026,9,1));q.setEnd(LocalDate.of(2026,9,30));q.setCalendar(true);
      var oldLeads=previous.leads(q);var newLeads=current.leads(q);same(oldLeads,newLeads,type+" leads denied="+denied);comparisons++;
      var work=current.leadWorkload(q);var calendar=current.leadCalendar(q);
      same(newLeads.workload(),work.workload(),"workload split");same(newLeads.followUp(),work.followUp(),"follow split");same(newLeads.calendar(),calendar.calendar(),"calendar split");same(newLeads.funnel(),calendar.funnel(),"funnel split");comparisons+=4;
      if(!denied && "USER".equals(type)){
       var r=new PerformanceReportQuery();r.setTenant(1L);r.setType(type);r.setId(id);r.setAllDepartments(true);r.setMissingDepartment(true);r.setStart(LocalDateTime.of(2026,9,1,0,0));r.setEnd(LocalDateTime.of(2026,10,1,0,0));r.setDueEnd(r.getEnd());r.setNow(LocalDateTime.of(2026,10,8,12,0));r.setToday(LocalDateTime.of(2026,10,8,0,0));r.setTomorrow(r.getToday().plusDays(1));r.setIntervals(List.of(new PerformanceReportQuery.Interval("month",r.getStart(),r.getEnd())));
       for(String method:List.of("amounts","receipts","pending","activity","calendarTasks","cohortOrders"))profile(config,c,r,method);
       // Exercise the non-empty department predicate, not only all/empty authorization branches.
       Long allowed=organizations.isEmpty()?null:organizations.getFirst().getDeptId();
       if(allowed!=null){when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(false,false,Set.of(allowed)));when(access.historicalRowAllowed(any(),any())).thenAnswer(call->allowed.equals(call.getArgument(1)));same(previous.leads(q),current.leads(q),"restricted department report");comparisons++;}
      }
     }
    }
    TenantContextHolder.setTenantId(991991L);when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(true,true,Set.of()));
    var empty=current.leadWorkload(query("USER",1L,"month"));assertTrue(empty.workload().values().stream().allMatch(x->x==0));comparisons++;
    assertTrue(comparisons>80);System.out.println("Report read-only equivalence comparisons="+comparisons);
   } finally {TenantContextHolder.clear();if(!c.isClosed())c.rollback();}
  }
 }
 private static void profile(Configuration config,Connection c,PerformanceReportQuery q,String method)throws Exception{
  var statement=config.getMappedStatement(PerformanceReportMapper.class.getName()+"."+method);var bound=statement.getBoundSql(q);
  try(var explain=c.prepareStatement("EXPLAIN ANALYZE "+bound.getSql())){
   new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement,q,bound).setParameters(explain);
   try(var result=explain.executeQuery()){if(result.next()){var match=java.util.regex.Pattern.compile("actual time=[0-9.]+\\.\\.([0-9.]+) rows=([0-9.]+)").matcher(result.getString(1));if(match.find())System.out.printf("report plan query=%s serverMs=%s resultRows=%s%n",method,match.group(1),match.group(2));else fail("Missing EXPLAIN ANALYZE result");}}
  }
 }
 private static Query query(String type,Long id,String period){var q=new Query();q.setScopeType(type);q.setScopeId(id);q.setPeriodKey(period);q.setGrain("month");return q;}
 private static void same(Object expected,Object actual,String label){assertTrue(canonical(expected).equals(canonical(actual)),label+" mismatch fields="+differences(expected,actual));}
 private static String differences(Object a,Object b){
  if(a==null||b==null)return "presence";
  if(a.getClass().isRecord())return Arrays.stream(a.getClass().getRecordComponents()).filter(field->{try{return !canonical(field.getAccessor().invoke(a)).equals(canonical(field.getAccessor().invoke(b)));}catch(Exception e){throw new AssertionError(e);}}).map(field->{try{return field.getName()+"."+differences(field.getAccessor().invoke(a),field.getAccessor().invoke(b));}catch(Exception e){throw new AssertionError(e);}}).collect(Collectors.joining(","));
  if(a instanceof List<?> x && b instanceof List<?> y){if(x.size()!=y.size())return "size";for(int i=0;i<x.size();i++)if(!canonical(x.get(i)).equals(canonical(y.get(i))))return "item"+i+"."+differences(x.get(i),y.get(i));}
  return "value";
 }
 private static String canonical(Object value){
  if(value==null)return "null";
  if(value instanceof BigDecimal n)return n.stripTrailingZeros().toPlainString();
  if(value instanceof Map<?,?> map)return map.entrySet().stream().map(e->canonical(e.getKey())+":"+canonical(e.getValue())).sorted().collect(Collectors.joining(",","{","}"));
  if(value instanceof Collection<?> list)return list.stream().map(PerformanceReportMySqlTest::canonical).sorted().collect(Collectors.joining(",","[","]"));
  if(value.getClass().isRecord())return Arrays.stream(value.getClass().getRecordComponents()).map(component->{try{return component.getName()+":"+canonical(component.getAccessor().invoke(value));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}).collect(Collectors.joining(",","{","}"));
  return value.toString();
 }
}
