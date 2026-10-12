package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.OrderDetailVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes actual mapper SQL in one read-only MySQL snapshot, without logging business rows. */
@EnabledIfEnvironmentVariable(named="PERFORMANCE_TEST_MYSQL_URL", matches=".+")
class OrderDetailMySqlTest {
    @Test void entryScopesFiltersAndAmountsMatchExistingMySqlQueries() throws Exception {
        try(var connection=DriverManager.getConnection(System.getenv("PERFORMANCE_TEST_MYSQL_URL"),System.getenv("PERFORMANCE_TEST_MYSQL_USER"),System.getenv("PERFORMANCE_TEST_MYSQL_PASSWORD"))) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setReadOnly(true);connection.setAutoCommit(false);
            var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("readonly-order-details",new JdbcTransactionFactory(),new SingleConnectionDataSource(connection,true)));
            config.addMapper(OrderDetailMapper.class);config.addMapper(PerformanceDetailMapper.class);
            try(var session=new SqlSessionFactoryBuilder().build(config).openSession(connection)) {
                var service=new OrderDetailsService();ReflectionTestUtils.setField(service,"mapper",session.getMapper(OrderDetailMapper.class));
                var old=session.getMapper(PerformanceDetailMapper.class);var now=LocalDateTime.now(PerformancePeriods.ZONE);int comparisons=0;
                for(String type:List.of("SELF","USER","DEPT","CENTER")) {
                    String column=type.equals("DEPT")?"dept_id":type.equals("CENTER")?"center_id":"user_id";
                    Long id;
                    try(var statement=connection.prepareStatement("SELECT "+column+" FROM zsjos_performance_attribution WHERE tenant_id=1 AND deleted=0 AND fact_type='ORDER' AND "+column+" IS NOT NULL GROUP BY "+column+" ORDER BY COUNT(*) DESC LIMIT 1");var rows=statement.executeQuery()) {
                        if(!rows.next())continue;id=rows.getLong(1);
                    }
                    var scope=new PerformanceReportQuery();scope.setTenant(1L).setType(type).setId(id).setAllDepartments(true).setMissingDepartment(type.equals("USER")||type.equals("SELF"));
                    for(String period:List.of("month","last90")) {
                        var window=PerformancePeriods.window(period,now);var q=new OrderDetailVO.Query();q.setPageSize(20);
                        var response=service.read(q,scope,window,now);
                        var legacy=new PerformanceDetailQuery().setTenant(1L).setType(type).setId(id).setAllDepartments(true).setMissingDepartment(scope.isMissingDepartment()).setMetric("orders").setStart(window.start()).setEnd(window.end()).setSize(100000);
                        long count=old.count(legacy);var oldRows=old.page(legacy);
                        assertEquals(count,response.total());assertTrue(oldRows.size()==count,"comparison requires complete legacy population");
                        BigDecimal total=oldRows.stream().map(PerformanceFact::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
                        assertTrue(total.compareTo(response.summary().amount())==0,"base amount must match legacy SQL");
                        if(!response.options().sources().isEmpty()) {
                            var option=response.options().sources().getFirst();q.setSourceKey(option.key());
                            var filtered=service.read(q,scope,window,now);assertEquals(option.count(),filtered.total());assertEquals(response.options(),filtered.options());
                            q.setSourceKey(null);q.setDimension("source");q.setGroupKey(option.key()+"|"+option.label());
                            assertEquals(filtered.total(),service.read(q,scope,window,now).total());q.setDimension(null);q.setGroupKey(null);
                        }
                        if(!response.options().products().isEmpty()) {
                            var product=response.options().products().getFirst();q.setProductKey(product.key());var filtered=service.read(q,scope,window,now);
                            assertEquals(product.count(),filtered.total());assertEquals(response.summary(),filtered.summary());
                            if(!filtered.list().isEmpty()) {
                                var p=filtered.list().getFirst().products().stream().filter(x->x.key().equals(product.key())).findFirst().orElseThrow();
                                q.setProductKey(null);q.setDimension("product");q.setGroupKey(Objects.toString(p.ref(),"unknown")+"|"+(p.label().equals("商品名称快照缺失")?"历史产品名称缺失":p.label()));
                                legacy.setDimension("product").setGroupKey(q.getGroupKey());var productRows=old.page(legacy);var result=service.read(q,scope,window,now);
                                assertEquals(old.count(legacy),result.total());assertTrue(productRows.stream().map(PerformanceFact::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(result.summary().amount())==0,"product contribution must match legacy SQL");
                            }
                        }
                        comparisons++;
                    }
                }
                assertTrue(comparisons>0,"requires existing authorized performance facts");
                System.out.println("MySQL read-only order detail comparisons passed: "+comparisons);
                connection.rollback();
            }
        }
    }
}
