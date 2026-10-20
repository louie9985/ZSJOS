package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.DeliveryPerformanceDetailMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryperformance.DeliveryPerformanceDetailQuery;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.apache.ibatis.annotations.SelectProvider;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceAttributionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceOrgDO;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise old facts through the real report, drilldown and conversion mapper paths. */
class PerformanceLegacyDomainTest {
    private SqlSession fixture() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("legacy-domain", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:legacy" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")));
        config.addMapper(PerformanceDetailMapper.class);
        config.addMapper(PerformanceReportMapper.class);
        config.addMapper(DeliveryPerformanceDetailMapper.class);
        var session = new SqlSessionFactoryBuilder().build(config).openSession();
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("CREATE TABLE zsjos_lead(id BIGINT,tenant_id BIGINT,deleted INT,lead_no VARCHAR,source_type VARCHAR,source_provider_user_id BIGINT,submission_source_type VARCHAR,source_provider_recorded BOOLEAN,provider_owner_type VARCHAR,provider_owner_id BIGINT,status VARCHAR,lead_category_label_snapshot VARCHAR,sales_stage_label_snapshot VARCHAR,owner_user_id BIGINT,current_assignment_history_id BIGINT)");
            sql.execute("CREATE TABLE zsjos_order(id BIGINT,tenant_id BIGINT,deleted INT,order_no VARCHAR,lead_id BIGINT,formal_sales_user_id BIGINT,submitted_at TIMESTAMP,total_amount DECIMAL(12,2),order_type VARCHAR,status VARCHAR)");
            sql.execute("CREATE TABLE zsjos_lead_assignment_history(id BIGINT,tenant_id BIGINT,deleted INT,lead_id BIGINT,to_owner_user_id BIGINT,occurred_at TIMESTAMP,action_type VARCHAR)");
            sql.execute("CREATE TABLE zsjos_performance_attribution(fact_type VARCHAR,fact_id BIGINT,tenant_id BIGINT,deleted INT,user_id BIGINT,user_name VARCHAR,received_at TIMESTAMP,assignment_id BIGINT,source_group VARCHAR,channel_label VARCHAR,channel_code VARCHAR,dept_id BIGINT,center_id BIGINT,business_domain VARCHAR,org_source VARCHAR)");
            sql.execute("CREATE TABLE zsjos_performance_org(tenant_id BIGINT,deleted INT,dept_id BIGINT,business_domain VARCHAR,create_time TIMESTAMP,update_time TIMESTAMP)");
            // The sales mapping was introduced after these facts. It cannot prove their old domain.
            sql.execute("INSERT INTO zsjos_performance_org VALUES(1,0,10,'sales','2026-02-01','2026-02-01'),(1,0,20,'delivery','2020-01-01','2020-01-01')");
            for (int id = 1; id <= 14; id++) {
                int tenant = id == 10 ? 2 : 1;
                sql.execute("INSERT INTO zsjos_lead VALUES(" + id + "," + tenant + ",0,'LD" + id + "','" + (id == 13 ? "sales_self_sourced" : "internal_new_media") + "',NULL,NULL,TRUE,NULL,NULL,'valid','category','stage'," + (id == 8 ? 2 : 1) + "," + id + ")");
                sql.execute("INSERT INTO zsjos_lead_assignment_history VALUES(" + id + "," + tenant + "," + (id == 12 ? 1 : 0) + "," + id + ",1,'2026-01-02','accept')");
                sql.execute("INSERT INTO zsjos_order VALUES(" + id + "," + tenant + "," + (id == 11 || id == 12 ? 1 : 0) + ",'O" + id + "'," + id + ",1,'2026-01-10',1280,'first_purchase','effective')");
                if (id != 1) {
                    String domain = id == 4 || id == 9 || id == 10 ? "'sales'" : id == 5 ? "'delivery'" : id == 14 ? "'invalid'" : "NULL";
                    String origin = id == 2 || id == 8 || id == 9 ? "'current'" : "'frozen'";
                    int dept = id == 6 ? 20 : id == 7 ? 99 : 10;
                    for (String kind : List.of("ASSIGNMENT", "ORDER"))
                        sql.execute("INSERT INTO zsjos_performance_attribution VALUES('" + kind + "'," + id + "," + tenant + ",0,1,'snapshot','2026-01-02'," + id + ",'inbound','channel','channel'," + dept + ",30," + domain + "," + origin + ")");
                }
            }
            sql.execute("UPDATE zsjos_lead SET deleted=1 WHERE id=11");
        }
        return session;
    }

    private PerformanceDetailQuery query(String type, long id) {
        return new PerformanceDetailQuery().setTenant(1L).setBusinessDomain("sales").setType(type).setId(id)
                .setAllDepartments(true).setMissingDepartment("USER".equals(type)).setMetric("orders")
                .setStart(LocalDateTime.of(2026, 1, 1, 0, 0)).setEnd(LocalDateTime.of(2026, 2, 1, 0, 0)).setSize(100);
    }

    @Test void legacyPersonalReportsAndPagesRestoreFactsWithoutAdmittingDelivery() throws Exception {
        try (var session = fixture()) {
            var mapper = session.getMapper(PerformanceDetailMapper.class);
            var q = query("USER", 1);
            var expected = Set.of(1L, 2L, 3L, 4L, 7L, 8L, 9L, 13L);
            assertEquals(expected, ids(mapper.page(q)));
            assertEquals(expected.size(), mapper.count(q));
            q.setSize(3);
            var paged = new ArrayList<PerformanceFact>();
            for (int offset = 0; offset < expected.size(); offset += 3) {
                q.setOffset(offset);
                paged.addAll(mapper.page(q));
            }
            assertEquals(expected, ids(paged));
            assertEquals(expected.size(), paged.size());
            var report = report(q);
            try (var amount = amountRows(session, report)) {
                assertTrue(amount.next());
                assertEquals(expected.size(), amount.getLong("count"));
                assertEquals(0, new BigDecimal("10240").compareTo(amount.getBigDecimal("amount")));
            }
            report.setGrouping("source");
            long grouped = 0;
            try (var amount = amountRows(session, report)) {
                while (amount.next()) grouped += amount.getLong("count");
            }
            assertEquals(expected.size(), grouped);
            q.setMetric("leads").setOffset(0).setSize(100);
            assertEquals(expected, ids(mapper.page(q)));
            assertEquals(expected.size(), mapper.count(q));
            assertEquals(expected, session.getMapper(PerformanceReportMapper.class).receipts(report).stream().map(PerformanceFact::getLeadId).collect(java.util.stream.Collectors.toSet()));
            assertEquals(q.getStart().plusDays(1), mapper.firstDate(q));
        }
    }

    @Test void legacyDepartmentsRetainOriginalScopeAndKnownDomainsKeepFrozenGuard() throws Exception {
        try (var session = fixture()) {
            var mapper = session.getMapper(PerformanceDetailMapper.class);
            var q = query("DEPT", 10);
            var expected = Set.of(2L, 3L, 4L, 8L, 13L);
            assertEquals(expected, ids(mapper.page(q)));
            q.setAllDepartments(false).setDepartments(Set.of(10L));
            assertEquals(expected, ids(mapper.page(q)));
            q.setDepartments(Set.of(99L));
            assertTrue(mapper.page(q).isEmpty());
            assertEquals(0, mapper.count(q));
            q.setDepartments(Set.of());
            assertTrue(mapper.page(q).isEmpty());
            q.setAllDepartments(true).setType("CENTER").setId(30L);
            assertEquals(Set.of(2L, 3L, 4L, 7L, 8L, 13L), ids(mapper.page(q)));
        }
    }

    @Test void recoveredConversionDenominatorPreservesOwnershipSourceAndAmountRules() throws Exception {
        try (var session = fixture()) {
            var mapper = session.getMapper(PerformanceDetailMapper.class);
            var q = query("USER", 1);
            var orders = mapper.conversionOrders(q);
            q.setConversionLeadIds(orders.stream().map(PerformanceFact::getLeadId).collect(java.util.stream.Collectors.toSet()));
            var receipts = mapper.conversionReceipts(q);
            var window = new PerformancePeriods.Window("month", "", q.getStart(), q.getEnd());
            var result = new PerformanceConversion.Population(orders, receipts).assessment(window);
            assertTrue(result.ready());
            assertEquals(Set.of(1L, 2L, 3L, 4L, 7L, 9L), result.eligible().keySet());
            assertEquals(6, result.eligible().values().stream().filter(x -> x.order() != null).count());
            q.setConversionLeadIds(java.util.stream.LongStream.rangeClosed(1, 600).boxed().collect(java.util.stream.Collectors.toSet()));
            assertEquals(ids(receipts), ids(mapper.conversionReceipts(q)));
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("UPDATE zsjos_order SET total_amount=100 WHERE id=1");
            }
            session.clearCache();
            result = new PerformanceConversion.Population(mapper.conversionOrders(q), mapper.conversionReceipts(q)).assessment(window);
            assertEquals(6, result.eligible().size());
            assertNull(result.eligible().get(1L).order(), "Low-value current receipts still belong to the denominator");
        }
    }

    @Test void legacyCompatibilityDoesNotExpandDeliveryOrRewriteUnknownHistory() throws Exception {
        try (var session = fixture()) {
            var q = new DeliveryPerformanceDetailQuery().setTenant(1L).setBusinessDomain("delivery").setType("USER").setId(1L)
                    .setAllDepartments(true).setMissingDepartment(true).setMetric("orders")
                    .setStart(LocalDateTime.of(2026, 1, 1, 0, 0)).setEnd(LocalDateTime.of(2026, 2, 1, 0, 0)).setSize(100);
            var delivery = session.getMapper(DeliveryPerformanceDetailMapper.class);
            assertEquals(Set.of(5L, 6L), delivery.page(q).stream().map(x -> x.getId()).collect(java.util.stream.Collectors.toSet()));
            q.setUnclassified(true);
            assertEquals(Set.of(1L, 2L, 3L, 7L, 8L, 13L, 14L), delivery.page(q).stream().map(x -> x.getId()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(ids(session.getMapper(PerformanceDetailMapper.class).page(query("USER", 1).setUnclassified(true))),
                    delivery.page(q).stream().map(x -> x.getId()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test void restoredQueriesBindAndParseThroughTenantInterceptor() throws Exception {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "legacy-domain-tenant");
        for (Class<?> type : List.of(LeadDO.class, LeadAssignmentHistoryDO.class, SalesOrderDO.class, PerformanceAttributionDO.class, PerformanceOrgDO.class))
            TableInfoHelper.initTableInfo(assistant, type);
        var handler = new TenantDatabaseInterceptor(new TenantProperties());
        assertFalse(handler.ignoreTable("zsjos_performance_attribution"));
        assertTrue(handler.ignoreTable("receipts"));
        assertTrue(handler.ignoreTable("windows"));
        var interceptor = new TenantLineInnerInterceptor(handler);
        TenantContextHolder.setTenantId(1L);
        try {
        for (String type : List.of("USER", "DEPT", "CENTER")) {
            var q = query(type, type.equals("USER") ? 1 : type.equals("DEPT") ? 1021 : 1020);
            q.setStart(LocalDateTime.of(2026, 9, 1, 0, 0)).setEnd(LocalDateTime.of(2026, 10, 1, 0, 0));
            q.setConversionLeadIds(java.util.stream.LongStream.rangeClosed(1, 600).boxed().collect(java.util.stream.Collectors.toSet()));
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("orders", PerformanceDetailSql.page(q));
            sources.put("order-count", PerformanceDetailSql.count(q));
            sources.put("conversion-orders", PerformanceDetailSql.conversionOrders(q));
            sources.put("conversion-receipts", PerformanceDetailSql.conversionReceipts(q));
            q.setMetric("leads");
            sources.put("receipts", PerformanceDetailSql.page(q));
            sources.put("receipt-count", PerformanceDetailSql.count(q));
            sources.put("amounts", PerformanceReportSql.amounts(report(q)));
            for (var entry : sources.entrySet()) {
                Object request = entry.getKey().equals("amounts") ? report(q) : q;
                var config = new Configuration();
                var bound = new XMLLanguageDriver().createSqlSource(config, entry.getValue(), request.getClass()).getBoundSql(request);
                String sql = assertDoesNotThrow(() -> interceptor.parserSingle(bound.getSql(), null));
                assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
                String directory = System.getenv("PERFORMANCE_LEGACY_EXPORT_DIR");
                if (directory != null && !directory.isBlank()) {
                    var root = java.nio.file.Path.of(directory).toAbsolutePath().normalize();
                    assertTrue(root.startsWith(java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()));
                    var params = new ArrayList<Object>();
                    var properties = new ArrayList<String>();
                    for (var mapping : bound.getParameterMappings()) {
                        String key = mapping.getProperty();
                        Object value = bound.hasAdditionalParameter(key) ? bound.getAdditionalParameter(key) : config.newMetaObject(request).getValue(key);
                        params.add(value instanceof LocalDateTime time ? time.toString().replace('T', ' ') : value);
                        properties.add(key);
                    }
                    java.nio.file.Files.createDirectories(root);
                    java.nio.file.Files.writeString(root.resolve(type + "-" + entry.getKey() + ".json"),
                            JsonUtils.toJsonString(Map.of("sql", sql, "parameters", params, "properties", properties)));
                }
            }
        }
        } finally { TenantContextHolder.clear(); }
    }

    @Test
    @EnabledIfEnvironmentVariable(named="PERFORMANCE_LEGACY_MYSQL_URL", matches=".+")
    void liveReadOnlySnapshotReportsActualBeforeAndAfterConversion() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection(System.getenv("PERFORMANCE_LEGACY_MYSQL_URL"),
                System.getenv("PERFORMANCE_LEGACY_MYSQL_USER"), System.getenv("PERFORMANCE_LEGACY_MYSQL_PASSWORD"))) {
            connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            long user;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT to_owner_user_id FROM zsjos_lead_assignment_history WHERE tenant_id=1 AND deleted=0 AND action_type IN ('accept','claim','transfer') AND to_owner_user_id IS NOT NULL GROUP BY to_owner_user_id ORDER BY COUNT(*) DESC LIMIT 1")) {
                assertTrue(rows.next());
                user = rows.getLong(1);
            }
            var config = new Configuration();
            config.setMapUnderscoreToCamelCase(true);
            config.setEnvironment(new Environment("legacy-read-only", new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
            config.addMapper(PerformanceDetailMapper.class);
            config.addMapper(StrictMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession(connection)) {
                var fixed = session.getMapper(PerformanceDetailMapper.class);
                var strict = session.getMapper(StrictMapper.class);
                for (String type : List.of("USER", "DEPT", "CENTER")) {
                    var q = query(type, type.equals("USER") ? user : type.equals("DEPT") ? 1021 : 1020);
                    q.setStart(LocalDateTime.of(2026, 9, 1, 0, 0)).setEnd(LocalDateTime.of(2026, 10, 1, 0, 0));
                    var window = new PerformancePeriods.Window("lastMonth", "", q.getStart(), q.getEnd());
                    for (boolean before : List.of(true, false)) {
                        var orders = before ? strict.orders(q) : fixed.conversionOrders(q);
                        q.setConversionLeadIds(orders.stream().map(PerformanceFact::getLeadId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
                        var receipts = before ? strict.receipts(q) : fixed.conversionReceipts(q);
                        var metric = PerformanceStatisticsService.metric(window, orders, receipts);
                        var assessment = new PerformanceConversion.Population(orders, receipts).assessment(window);
                        System.out.println("Legacy MySQL " + type + " " + (before ? "before" : "after")
                                + ": eligible=" + assessment.eligible().size() + ", denominator=" + metric.denominator()
                                + ", converted=" + metric.converted() + ", source=" + metric.sourceStatus() + ", review=" + metric.sourceReviewCount());
                    }
                }
                connection.rollback();
            }
        }
    }

    interface StrictMapper {
        @SelectProvider(type=StrictSql.class, method="orders") List<PerformanceFact> orders(PerformanceDetailQuery q);
        @SelectProvider(type=StrictSql.class, method="receipts") List<PerformanceFact> receipts(PerformanceDetailQuery q);
    }

    public static class StrictSql {
        public static String orders(PerformanceDetailQuery q) { return before(PerformanceDetailSql.conversionOrders(q)); }
        public static String receipts(PerformanceDetailQuery q) { return before(PerformanceDetailSql.conversionReceipts(q)); }
        private static String before(String sql) {
            return sql.replace("(f.resolved_domain='sales' OR (f.business_domain IS NULL AND f.resolved_domain IS NULL))", "f.resolved_domain='sales'")
                    .replace("(f.org_source='frozen'<if test=\"businessDomain == 'sales' and !unclassified\"> OR (f.business_domain IS NULL AND f.resolved_domain IS NULL)</if>)", "f.org_source='frozen'");
        }
    }

    private PerformanceReportQuery report(PerformanceDetailQuery q) {
        var report = new PerformanceReportQuery();
        report.setTenant(q.getTenant()).setBusinessDomain(q.getBusinessDomain()).setType(q.getType()).setId(q.getId())
                .setAllDepartments(q.isAllDepartments()).setMissingDepartment(q.isMissingDepartment()).setDepartments(q.getDepartments())
                .setStart(q.getStart()).setEnd(q.getEnd());
        report.setIntervals(List.of(new PerformanceReportQuery.Interval("month", q.getStart(), q.getEnd())));
        return report;
    }

    private java.sql.ResultSet amountRows(SqlSession session, PerformanceReportQuery q) throws Exception {
        // H2 needs literals in the window CTE and text casts for these ASCII source fixtures; MySQL SQL is tested separately.
        var config = session.getConfiguration();
        var bound = new XMLLanguageDriver().createSqlSource(config, PerformanceReportSql.amounts(q), PerformanceReportQuery.class).getBoundSql(q);
        String sql = bound.getSql();
        for (var parameter : bound.getParameterMappings()) {
            String key = parameter.getProperty();
            Object value = bound.hasAdditionalParameter(key) ? bound.getAdditionalParameter(key) : config.newMetaObject(q).getValue(key);
            String literal = value == null ? "NULL" : value instanceof Number ? value.toString()
                    : "'" + value.toString().replace('T', ' ').replace("'", "''") + "'";
            sql = sql.replaceFirst("\\?", java.util.regex.Matcher.quoteReplacement(literal));
        }
        sql = sql.replace("CAST(order_type AS BINARY)", "CAST(order_type AS VARCHAR)")
                .replace("CAST(group_key AS BINARY)", "CAST(group_key AS VARCHAR)");
        var statement = session.getConnection().createStatement();
        statement.closeOnCompletion();
        return statement.executeQuery(sql);
    }

    private Set<Long> ids(List<PerformanceFact> rows) {
        return rows.stream().map(PerformanceFact::getId).collect(java.util.stream.Collectors.toSet());
    }
}
