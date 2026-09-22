package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SubordinateSalesDailyQueryTest {
    @Test
    void actualMapperQueriesEnforceUserTimeTenantDeletionAndEventScope() throws Exception {
        var datasource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:daily" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("daily-test", new JdbcTransactionFactory(), datasource));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public LongValue getTenantId() { return new LongValue(1); }
        }));
        config.addInterceptor(plugins);
        config.addMapper(LeadAssignmentHistoryMapper.class);
        config.addMapper(LeadFollowUpRecordMapper.class);
        config.addMapper(OpportunityFollowUpRecordMapper.class);
        config.addMapper(BusinessEventMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            try (var sql = session.getConnection().createStatement()) {
                for (var table : List.of("zsjos_lead_assignment_history", "zsjos_lead_follow_up_record", "zsjos_opportunity_follow_up_record", "zsjos_business_event")) {
                    sql.execute("CREATE TABLE " + table + " (id BIGINT, lead_id BIGINT, candidate_user_id BIGINT, operator_user_id BIGINT, action_type VARCHAR, aggregate_id BIGINT, aggregate_type VARCHAR, event_type VARCHAR, occurred_at TIMESTAMP, tenant_id BIGINT, deleted BOOLEAN)");
                    sql.execute("INSERT INTO " + table + " VALUES "
                            + "(1,1,20,20,'dispatch',1,'lead','lead_qualified_valid','2026-09-22 00:00:00',1,false),"
                            + "(2,2,21,21,'dispatch',2,'lead','lead_qualified_valid','2026-09-22 12:00:00',1,false),"
                            + "(3,3,20,20,'dispatch',3,'lead','lead_qualified_valid','2026-09-21 23:59:59',1,false),"
                            + "(4,4,20,20,'dispatch',4,'lead','lead_qualified_valid','2026-09-23 00:00:00',1,false),"
                            + "(5,5,20,20,'dispatch',5,'lead','lead_qualified_valid','2026-09-22 12:00:00',2,false),"
                            + "(6,6,20,20,'dispatch',6,'lead','lead_qualified_valid','2026-09-22 12:00:00',1,true)");
                }
                sql.execute("INSERT INTO zsjos_business_event VALUES (7,7,20,20,NULL,7,'lead','lead_qualified_invalid','2026-09-22 12:00:00',1,false), (8,8,20,20,NULL,8,'other','lead_qualified_valid','2026-09-22 12:00:00',1,false)");
            }
            var start = LocalDateTime.of(2026, 9, 22, 0, 0);
            var end = start.plusDays(1);
            var history = session.getMapper(LeadAssignmentHistoryMapper.class);
            var follow = session.getMapper(LeadFollowUpRecordMapper.class);
            var opportunity = session.getMapper(OpportunityFollowUpRecordMapper.class);
            var events = session.getMapper(BusinessEventMapper.class);
            assertEquals(List.of(1L), history.selectTodayByUserIds(List.of(20L), start, end).stream().map(row -> row.getLeadId()).toList());
            assertEquals(1, follow.selectTodayByUserIds(List.of(20L), start, end).size());
            assertEquals(1, opportunity.selectTodayByUserIds(List.of(20L), start, end).size());
            assertEquals(List.of(1L), events.selectTodayByUserIds(List.of(20L), start, end).stream().map(row -> row.getAggregateId()).toList());
            assertEquals(List.of(), history.selectTodayByUserIds(List.of(), start, end));
            assertEquals(List.of(), follow.selectTodayByUserIds(List.of(), start, end));
            assertEquals(List.of(), opportunity.selectTodayByUserIds(List.of(), start, end));
            assertEquals(List.of(), events.selectTodayByUserIds(List.of(), start, end));
            session.rollback();
        }
    }
}
