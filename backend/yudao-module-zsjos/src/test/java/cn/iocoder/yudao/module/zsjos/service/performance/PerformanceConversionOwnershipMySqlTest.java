package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only real MySQL snapshot. Prints aggregate evidence only, never account/lead identifiers. */
@EnabledIfEnvironmentVariable(named="PERFORMANCE_TEST_MYSQL_URL", matches=".+")
class PerformanceConversionOwnershipMySqlTest {
    @Test
    @EnabledIfEnvironmentVariable(named="PERFORMANCE_TEST_EXACT_SEPTEMBER", matches="true")
    void septemberPopulationMatchesIndependentCurrentOwnerSelection() throws Exception {
        try (var c = DriverManager.getConnection(System.getenv("PERFORMANCE_TEST_MYSQL_URL"),
                System.getenv("PERFORMANCE_TEST_MYSQL_USER"), System.getenv("PERFORMANCE_TEST_MYSQL_PASSWORD"))) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setReadOnly(true); c.setAutoCommit(false);
            var config = new Configuration(); config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("exact-population", new JdbcTransactionFactory(), new SingleConnectionDataSource(c, true)));
            config.addMapper(PerformanceDetailMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession(c)) {
                long user = Long.parseLong(System.getenv("PERFORMANCE_TEST_USER_ID"));
                var expected = new HashSet<Long>();
                try (var sql = c.prepareStatement("SELECT id FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 AND owner_user_id=? "
                        + "AND submitted_at >= '2026-09-01' AND submitted_at < '2026-10-01' "
                        + "AND source_type IN ('internal_new_media','partner') AND status IN ('valid','converted','won')")) {
                    sql.setLong(1,user);
                    try (var rs=sql.executeQuery()) { while(rs.next()) expected.add(rs.getLong(1)); }
                }
                assertEquals(214,expected.size(),"Acceptance snapshot changed; inspect rather than adjusting expected counts");
                var w=PerformancePeriods.window("lastMonth",LocalDateTime.of(2026,10,15,12,0));
                var q=new PerformanceDetailQuery().setTenant(1L).setType("USER").setId(user).setAllDepartments(true)
                        .setMissingDepartment(true).setStart(w.start()).setEnd(w.end());
                var mapper=session.getMapper(PerformanceDetailMapper.class);
                var orders=mapper.conversionOrders(q);
                q.setConversionLeadIds(orders.stream().map(PerformanceFact::getLeadId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
                var receipts=mapper.conversionReceipts(q);
                var assessment=new PerformanceConversion.Population(orders,receipts).assessment(w);
                assertTrue(assessment.ready());
                assertEquals(expected,assessment.eligible().keySet(),"Compare identities, not just counts");
                var metric=PerformanceStatisticsService.metric(w,orders,receipts);
                System.out.println("Exact September acceptance: denominator="+metric.denominator()+" converted="+metric.converted()+" rate="+metric.rate());
                c.rollback();
            }
        }
    }
    @Test void actualMapperOwnershipAndScopeInOneSnapshot() throws Exception {
        try (var c = DriverManager.getConnection(System.getenv("PERFORMANCE_TEST_MYSQL_URL"),
                System.getenv("PERFORMANCE_TEST_MYSQL_USER"), System.getenv("PERFORMANCE_TEST_MYSQL_PASSWORD"))) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setReadOnly(true); c.setAutoCommit(false);
            var config = new Configuration(); config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("conversion-read-only", new JdbcTransactionFactory(), new SingleConnectionDataSource(c, true)));
            config.addMapper(PerformanceDetailMapper.class); config.addMapper(PerformanceFactMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession(c)) {
                var mapper = session.getMapper(PerformanceDetailMapper.class);
                var now = LocalDateTime.of(2026, 10, 15, 12, 0);
                int checked = 0;
                for (String type : List.of("USER", "DEPT", "CENTER")) {
                    String column = type.equals("USER") ? "user_id" : type.equals("DEPT") ? "dept_id" : "center_id";
                    var ids = new ArrayList<Long>();
                    try (var sql = c.createStatement(); var rs = sql.executeQuery("SELECT DISTINCT " + column
                            + " FROM zsjos_performance_attribution WHERE tenant_id=1 AND deleted=0 AND " + column + " IS NOT NULL")) {
                        while (rs.next()) ids.add(rs.getLong(1));
                    }
                    for (Long id : ids) {
                        for (String key : List.of("lastWeek", "month", "lastMonth", "last7", "last30", "last60", "last90")) {
                            var w = PerformancePeriods.window(key, now);
                            var q = new PerformanceDetailQuery().setTenant(1L).setType(type).setId(id).setAllDepartments(true)
                                    .setMissingDepartment(type.equals("USER")).setStart(w.start()).setEnd(w.end());
                            var orders = mapper.conversionOrders(q);
                            q.setConversionLeadIds(orders.stream().map(PerformanceFact::getLeadId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
                            var receipts = mapper.conversionReceipts(q);
                            var population = PerformanceConversion.population(w, orders, receipts);
                            for (var row : population.values()) {
                                for (var fact : java.util.stream.Stream.of(row.receipt(), row.order()).filter(Objects::nonNull).toList()) {
                                    assertEquals(fact.getUserId(), fact.getOwnerUserId());
                                    assertTrue(Boolean.TRUE.equals(fact.getCurrentAssignment()));
                                }
                            }
                            var metric = PerformanceStatisticsService.metric(w, orders, receipts);
                            if (metric.sourceStatus().equals("ready")) {
                                assertEquals(population.size(), metric.denominator());
                                assertEquals(population.values().stream().filter(r -> r.order() != null).count(), metric.converted());
                            }
                            checked++;
                        }
                    }
                }
                assertTrue(checked > 0);
                System.out.println("Current ownership MySQL scope/window checks: " + checked);
                try (var sql = c.prepareStatement("SELECT id FROM system_users WHERE tenant_id=1 AND deleted=0 AND id=?")) {
                    sql.setLong(1, Long.parseLong(System.getenv().getOrDefault("PERFORMANCE_TEST_USER_ID", "0")));
                    try (var rs = sql.executeQuery()) {
                        if (rs.next()) {
                            var w = PerformancePeriods.window("lastMonth", now);
                            var q = new PerformanceDetailQuery().setTenant(1L).setType("USER").setId(rs.getLong(1)).setAllDepartments(true)
                                    .setMissingDepartment(true).setStart(w.start()).setEnd(w.end());
                            var orders = mapper.conversionOrders(q);
                            q.setConversionLeadIds(orders.stream().map(PerformanceFact::getLeadId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
                            var receipts = mapper.conversionReceipts(q);
                            var m = PerformanceStatisticsService.metric(w, orders, receipts);
                            System.out.println("Requested account local snapshot: denominator=" + m.denominator() + " converted=" + m.converted() + " rate=" + m.rate() + " source=" + m.sourceStatus());
                        }
                    }
                }
                c.rollback();
            }
        }
    }
}
