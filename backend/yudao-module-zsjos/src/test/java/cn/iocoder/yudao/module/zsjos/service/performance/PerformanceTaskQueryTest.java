package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFactMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceTaskQueryTest {
    @ParameterizedTest
    @ValueSource(strings = {"SELF", "USER", "DEPT", "CENTER"})
    void taskAttributionsKeepMultiplicityMissingHistoryAndIsolation(String scope) throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:performance-task-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("task-query", new JdbcTransactionFactory(), source));
        config.addMapper(PerformanceFactMapper.class);
        config.addMapper(cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceDetailMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("CREATE ALIAS JSON_EXTRACT FOR 'cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyQueryTest.jsonExtract'");
                sql.execute("CREATE ALIAS JSON_UNQUOTE FOR 'cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyQueryTest.jsonUnquote'");
                sql.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR,owner_user_id BIGINT,ownership_started_at TIMESTAMP,lead_category_label_snapshot VARCHAR,sales_stage_label_snapshot VARCHAR,current_assignment_history_id BIGINT,qualification_round_no INT)");
                sql.execute("INSERT INTO zsjos_lead VALUES(1,9,0,'LD-QUERY',1,CURRENT_TIMESTAMP,'snapshot','stage',7,1)");
                sql.execute("CREATE TABLE zsjos_performance_attribution(fact_type VARCHAR,fact_id BIGINT,tenant_id BIGINT,deleted INT,dept_id BIGINT,center_id BIGINT,completed_at TIMESTAMP,outcome VARCHAR,assignment_id BIGINT)");
                sql.execute("CREATE TABLE zsjos_business_task(id BIGINT,tenant_id BIGINT,deleted INT,biz_id BIGINT,assignee_id BIGINT,create_time TIMESTAMP,due_at TIMESTAMP,completed_at TIMESTAMP,cancelled_at TIMESTAMP,idempotency_key VARCHAR,status VARCHAR,task_type VARCHAR,cancel_reason VARCHAR,payload VARCHAR)");
                for (int id = 1; id <= 9; id++) {
                    sql.execute("INSERT INTO zsjos_business_task VALUES(" + id + "," + (id == 5 ? 99 : 9) + ","
                            + (id == 6 ? 1 : 0) + ",1," + (id == 8 ? 2 : 1)
                            + ",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,NULL,'lead-qualification:1:1','pending','"
                            + (id == 7 ? "unrelated" : "lead_qualification") + "',NULL,'{}')");
                }
                sql.execute("INSERT INTO zsjos_performance_attribution VALUES"
                        + "('QUALIFICATION',1,9,0,20,30,NULL,'pending',7),"
                        + "('TASK',1,9,0,20,30,NULL,'completed',7),"
                        + "('ORDER',2,9,0,20,30,NULL,NULL,7),"
                        + "('TASK',3,9,1,20,30,NULL,NULL,7),"
                        + "('TASK',4,99,0,20,30,NULL,NULL,7),"
                        + "('TASK',5,9,0,20,30,NULL,NULL,7),"
                        + "('TASK',6,9,0,20,30,NULL,NULL,7),"
                        + "('TASK',7,9,0,20,30,NULL,NULL,7),"
                        + "('TASK',8,9,0,20,30,NULL,NULL,7),"
                        + "('TASK',9,9,0,21,31,NULL,NULL,7)");
            }
            var mapper = session.getMapper(PerformanceFactMapper.class);
            long id = "DEPT".equals(scope) ? 20L : "CENTER".equals(scope) ? 30L : 1L;
            var rows = mapper.tasks(9L, scope, id);
            boolean personal = List.of("SELF", "USER").contains(scope);
            assertEquals(personal ? List.of(9L, 4L, 3L, 2L, 1L, 1L) : List.of(8L, 1L, 1L),
                    rows.stream().map(PerformanceFact::getId).toList());
            assertEquals(List.of("completed", "pending"), rows.stream().filter(row -> row.getId() == 1L)
                    .map(PerformanceFact::getOutcome).sorted().toList());
            assertTrue(rows.stream().filter(row -> row.getId() == 1L)
                    .allMatch(row -> Long.valueOf(7).equals(row.getAssignmentId()) && Boolean.TRUE.equals(row.getCurrentQualification())));
            if (personal) {
                assertEquals(3L, rows.stream().filter(row -> row.getDeptId() == null).count());
            } else {
                assertTrue(rows.stream().allMatch(row -> Long.valueOf(20).equals(row.getDeptId())
                        && Long.valueOf(30).equals(row.getCenterId())));
            }
            var pages = session.getMapper(cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceDetailMapper.class);
            var now = java.time.LocalDateTime.now().plusDays(1);
            var query = new cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceDetailQuery()
                    .setTenant(9L).setType(scope).setId(id).setAllDepartments(true).setMissingDepartment(personal)
                    .setDepartments(java.util.Set.of()).setMetric("qualification").setNow(now).setSize(1);
            var expected = rows.stream().filter(x -> Boolean.TRUE.equals(x.getCurrentQualification()))
                    .sorted(java.util.Comparator.comparing(PerformanceFact::getId)).map(PerformanceFact::getId).toList();
            assertEquals(expected.size(), pages.count(query));
            var paged = new java.util.ArrayList<Long>();
            for (int offset=0;offset<expected.size();offset++) {
                query.setOffset(offset);paged.addAll(pages.page(query).stream().map(PerformanceFact::getId).toList());
            }
            assertEquals(expected, paged, "Page boundaries preserve duplicate frozen task facts");
            query.setAllDepartments(false).setDepartments(java.util.Set.of(999L));assertEquals(0,pages.count(query));
            query.setAllDepartments(true).setTenant(999L);assertEquals(0,pages.count(query));
            assertTrue(mapper.tasks(999L, scope, id).isEmpty());
            assertTrue(mapper.tasks(9L, scope, 999L).isEmpty());
        }
    }
}
