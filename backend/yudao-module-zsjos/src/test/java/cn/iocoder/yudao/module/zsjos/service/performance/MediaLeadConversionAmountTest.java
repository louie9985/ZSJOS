package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MediaLeadConversionAmountTest {
    @Test
    void overviewAndPagedDetailsRequireAnIndividualQualifyingOrder() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:media-amount-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("media-amount", new JdbcTransactionFactory(), source));
        config.addMapper(MediaLeadFactMapper.class);
        config.addMapper(MediaLeadQueryMapper.class);
        var start = LocalDateTime.of(2025, 1, 1, 0, 0);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("""
                    CREATE TABLE zsjos_lead(id BIGINT, tenant_id BIGINT, deleted INT, lead_no VARCHAR,
                      submitted_at TIMESTAMP, contribution_user_id_snapshot BIGINT,
                      contribution_dept_id_snapshot BIGINT, contribution_user_name_snapshot VARCHAR,
                      status VARCHAR, source_channel_label_snapshot VARCHAR, lead_category_label_snapshot VARCHAR)
                    """);
                for (int id = 1; id <= 10; id++) {
                    sql.execute("INSERT INTO zsjos_lead VALUES(" + id
                            + ",9,0,'LD-" + id + "','2025-01-01',1,10,'test','valid',NULL,NULL)");
                }
                sql.execute("""
                    CREATE TABLE zsjos_order(tenant_id BIGINT, deleted INT, lead_id BIGINT,
                      status VARCHAR, order_type VARCHAR, effective_at TIMESTAMP, total_amount DECIMAL(18,2))
                    """);
                sql.execute("""
                    INSERT INTO zsjos_order VALUES
                      (9,0,1,'effective','first_purchase','2025-01-02',1279.99),
                      (9,0,2,'effective','first_purchase','2025-01-02',1280),
                      (9,0,3,'effective','first_purchase','2025-01-02',1280.01),
                      (9,0,4,'effective','first_purchase','2025-01-02',NULL),
                      (9,0,5,'effective','first_purchase','2025-01-02',640),
                      (9,0,5,'effective','first_purchase','2025-01-03',640),
                      (9,0,6,'effective','first_purchase','2025-01-02',100),
                      (9,0,6,'effective','first_purchase','2025-01-03',1280),
                      (9,0,6,'effective','first_purchase','2025-01-04',2560),
                      (9,0,7,'effective','repurchase','2025-01-02',2560),
                      (9,0,8,'pending','first_purchase','2025-01-02',2560),
                      (9,1,9,'effective','first_purchase','2025-01-02',2560),
                      (99,0,10,'effective','first_purchase','2025-01-02',2560)
                    """);
            }
            var facts = session.getMapper(MediaLeadFactMapper.class);
            var pages = session.getMapper(MediaLeadQueryMapper.class);
            Map<Long, LocalDateTime> expected = Map.of(2L, start.plusDays(1),
                    3L, start.plusDays(1), 6L, start.plusDays(2));
            for (String scope : List.of("USER", "DEPT", "CENTER")) {
                long scopeId = "USER".equals(scope) ? 1L : 10L;
                var orders = facts.firstOrders(9L, scope, scopeId, List.of(10L));
                assertEquals(expected, orders.stream().collect(Collectors.toMap(
                        MediaLeadOrderFact::getLeadId, MediaLeadOrderFact::getEffectiveAt)));
                // 金额门槛只限制成交事实，不移除有效客资分母或明细行。
                assertEquals(10, facts.leads(9L, scope, scopeId, List.of(10L)).size());
                assertEquals(10, pages.countDetails(9L, scope, scopeId, List.of(10L),
                        start, start.plusMonths(1), start.plusDays(20)));
                var rows = pages.pageDetails(9L, scope, scopeId, List.of(10L),
                        start, start.plusMonths(1), start.plusDays(20), 0, 20);
                assertEquals(10, rows.size());
                for (var row : rows) {
                    assertEquals(expected.get(Long.parseLong(row.getLeadNo().substring(3))),
                            row.getOrderEffectiveAt(), row.getLeadNo());
                }
            }
            assertTrue(facts.firstOrders(9L, "CENTER", 10L, List.of()).isEmpty());
            assertTrue(facts.firstOrders(9L, "USER", 2L, List.of()).isEmpty());
            assertTrue(facts.firstOrders(99L, "USER", 1L, List.of()).isEmpty());
        }
    }
}
