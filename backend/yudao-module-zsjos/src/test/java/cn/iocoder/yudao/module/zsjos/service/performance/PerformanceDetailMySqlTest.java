package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
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
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual MySQL, one read-only snapshot; emits counts/timings, never business rows. */
@EnabledIfEnvironmentVariable(named="PERFORMANCE_TEST_MYSQL_URL", matches=".+")
class PerformanceDetailMySqlTest {
    @Test void pagedSqlMatchesPreviousAuthorizedPopulation() throws Exception {
        try (var connection=DriverManager.getConnection(System.getenv("PERFORMANCE_TEST_MYSQL_URL"),
                System.getenv("PERFORMANCE_TEST_MYSQL_USER"),System.getenv("PERFORMANCE_TEST_MYSQL_PASSWORD"))) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true); connection.setAutoCommit(false);
            var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("pagination-read-only",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            config.addMapper(PerformanceFactMapper.class);config.addMapper(PerformanceDetailMapper.class);
            config.addMapper(MediaLeadFactMapper.class);config.addMapper(MediaLeadQueryMapper.class);
            try(var session=new SqlSessionFactoryBuilder().build(config).openSession(connection)) {
                var facts=session.getMapper(PerformanceFactMapper.class);var pages=session.getMapper(PerformanceDetailMapper.class);
                var access=mock(PerformanceAccess.class);
                when(access.has(anyString())).thenReturn(true);
                var current=new PerformanceStatisticsService();var reference=new PerformanceDetailReference();
                for(Object service:List.of(current,reference)){
                    ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"facts",facts);
                }
                ReflectionTestUtils.setField(current,"detailQueries",pages);
                long tenant=1;TenantContextHolder.setTenantId(tenant);
                int comparisons=0;
                for(String type:List.of("SELF","USER","DEPT","CENTER")) {
                    String column=type.equals("DEPT")?"dept_id":type.equals("CENTER")?"center_id":"user_id";
                    Long scope;
                    try(var sql=connection.prepareStatement("SELECT "+column+" FROM zsjos_performance_attribution WHERE tenant_id=? AND deleted=0 AND "+column+" IS NOT NULL GROUP BY "+column+" ORDER BY COUNT(*) DESC LIMIT 1")){
                        sql.setLong(1,tenant);try(var result=sql.executeQuery()){scope=result.next()?result.getLong(1):null;}
                    }
                    if(scope==null)continue;
                    boolean missing=type.equals("SELF")||type.equals("USER");
                    when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(true,missing,Set.of()));
                    when(access.historicalRowAllowed(any(),any())).thenAnswer(call->missing||call.getArgument(1)!=null);
                    for(String metric:List.of("orders","leads","valid","conversion","assigned","missed","followUps","tasks","accept","qualification","todayFollowUp","overdueFollowUp")){
                        Query q=query(type,scope,metric);q.setPageSize(1000000);
                        long before=System.nanoTime();var expected=reference.details(q);long referenceNanos=System.nanoTime()-before;
                        q.setPageSize(20);var actual=new ArrayList<Detail>();long pagedNanos=0;
                        for(int page=1;page<=Math.max(1,(expected.getTotal()+19)/20);page++){
                            q.setPageNo(page);session.clearCache();before=System.nanoTime();var result=current.details(q);pagedNanos+=System.nanoTime()-before;
                            assertEquals(expected.getTotal(),result.getTotal(),type+"/"+metric+" total");
                            assertTrue(result.getList().size()<=20,"Page must stay bounded");actual.addAll(result.getList());
                        }
                        assertTrue(multiset(expected.getList()).equals(multiset(actual)),type+"/"+metric+" complete population mismatch");
                        q.setPageNo(1);var first=current.details(q);assertTrue(multiset(first.getList()).equals(multiset(current.details(q).getList())),"Stable page");
                        System.out.printf(Locale.ROOT,"pagination scope=%s metric=%s rows=%d oldAllMs=%.2f newAllPagesMs=%.2f%n",type,metric,expected.getTotal(),referenceNanos/1e6,pagedNanos/1e6);
                        comparisons++;
                    }
                    for(String key:List.of("last7","last30","last60","last90","month","lastMonth")){
                        Query q=query(type,scope,"conversion");q.setPeriodKey(key);q.setPageSize(1000000);
                        var expected=reference.details(q);var actual=current.details(q);
                        assertTrue(multiset(expected.getList()).equals(multiset(actual.getList())),type+"/conversion/"+key);comparisons++;
                    }
                    for(String group:List.of("inbound|线上引流","self|非引流","unknown|其他")){
                        for(String metric:List.of("orders","conversion")){
                            Query q=query(type,scope,metric);q.setDimension("source");q.setGroupKey(group);q.setPageSize(1000000);
                            assertTrue(multiset(reference.details(q).getList()).equals(multiset(current.details(q).getList())),"Source projection");comparisons++;
                        }
                    }
                }
                assertTrue(comparisons>0,"No representative scoped data available");
                System.out.println("sales equivalence comparisons="+comparisons);
                // Clear the local MyBatis cache before BOTH implementations: this measures one visible page.
                for(String metric:List.of("orders","leads","conversion","followUps","tasks")) {
                    Long scope;
                    try(var sql=connection.prepareStatement("SELECT user_id FROM zsjos_performance_attribution WHERE tenant_id=1 AND deleted=0 AND user_id IS NOT NULL GROUP BY user_id ORDER BY COUNT(*) DESC LIMIT 1");var result=sql.executeQuery()) { scope=result.next()?result.getLong(1):null; }
                    if(scope==null)continue;
                    when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(true,true,Set.of()));
                    when(access.historicalRowAllowed(any(),any())).thenReturn(true);
                    var q=query("USER",scope,metric);q.setPageSize(20);
                    List<Double> oldTimes=new ArrayList<>(),newTimes=new ArrayList<>();long total=0;
                    for(int run=-3;run<20;run++) {
                        for(boolean old:run%2==0?List.of(true,false):List.of(false,true)) {
                            session.clearCache();long started=System.nanoTime();
                            var page=old?reference.details(q):current.details(q);double elapsed=(System.nanoTime()-started)/1e6;total=page.getTotal();
                            if(run>=0)(old?oldTimes:newTimes).add(elapsed);
                        }
                    }
                    Collections.sort(oldTimes);Collections.sort(newTimes);
                    System.out.printf(Locale.ROOT,"page benchmark metric=%s total=%d size=20 oldP50=%.2f oldP95=%.2f newP50=%.2f newP95=%.2f ms%n",metric,total,oldTimes.get(9),oldTimes.get(18),newTimes.get(9),newTimes.get(18));
                }
                var media=session.getMapper(MediaLeadFactMapper.class);var mediaPages=session.getMapper(MediaLeadQueryMapper.class);
                Long contributor;
                try(var sql=connection.prepareStatement("SELECT contribution_user_id_snapshot FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 AND contribution_user_id_snapshot IS NOT NULL GROUP BY contribution_user_id_snapshot ORDER BY COUNT(*) DESC LIMIT 1");var result=sql.executeQuery()){
                    contributor=result.next()?result.getLong(1):null;
                }
                if(contributor!=null){
                    var now=LocalDateTime.now(PerformancePeriods.ZONE);var start=now.toLocalDate().minusDays(365).atStartOfDay();var end=now.toLocalDate().plusDays(1).atStartOfDay();
                    var expected=media.leads(1L,"USER",contributor,List.of()).stream().filter(x->!x.getSubmittedAt().isBefore(start)&&x.getSubmittedAt().isBefore(end)&&!x.getSubmittedAt().isAfter(now)).map(MediaLeadFact::getLeadNo).toList();
                    long total=mediaPages.countDetails(1L,"USER",contributor,List.of(),start,end,now);
                    assertEquals(expected.size(),total);var actual=new ArrayList<String>();
                    for(int offset=0;offset<total;offset+=20)actual.addAll(mediaPages.pageDetails(1L,"USER",contributor,List.of(),start,end,now,offset,20).stream().map(MediaLeadDetailRow::getLeadNo).toList());
                    assertTrue(expected.equals(actual),"Media pages preserve complete descending business-number sequence");
                    System.out.println("media detail verified rows="+total);
                }
            } finally {if(!connection.isClosed())connection.rollback();TenantContextHolder.clear();}
        }
    }
    private Query query(String type,Long id,String metric){var q=new Query();q.setScopeType(type);q.setScopeId(id);q.setMetric(metric);q.setStart(LocalDate.now(PerformancePeriods.ZONE).minusYears(2));q.setEnd(LocalDate.now(PerformancePeriods.ZONE));return q;}
    private Map<List<Object>,Long> multiset(List<Detail> rows){return rows.stream().map(x->Arrays.<Object>asList(x.id(),x.number(),x.kind(),x.label(),x.occurredAt(),x.amount()==null?null:x.amount().stripTrailingZeros(),x.state(),x.leadId(),x.ownerName(),x.assigneeName(),x.receivedAt(),x.dueAt(),x.category(),x.stage())).collect(Collectors.groupingBy(x->x,HashMap::new,Collectors.counting()));}
}
