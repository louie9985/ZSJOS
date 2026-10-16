package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSupervisorPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderSupervisorConfirmationMapper;
import org.apache.ibatis.mapping.BoundSql;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceReportParserTest {
    @Test
    void everyAmountGroupingParsesThroughActualTenantInterceptor() throws Exception {
        var interceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(1); }
        });
        for (String grouping : List.of("total", "source", "user", "contributor", "product")) {
            for (int scope = 0; scope < 3; scope++) {
                var q = new PerformanceReportQuery();
                q.setBusinessDomain("sales"); q.setGrouping(grouping); q.setTenant(1L); q.setType("USER"); q.setId(1L);
                q.setStart(LocalDateTime.of(2026, 10, 1, 0, 0)); q.setEnd(LocalDateTime.of(2026, 11, 1, 0, 0));
                q.setAllDepartments(scope == 0); q.setMissingDepartment(scope == 0);
                q.setDepartments(scope == 1 ? Set.of(10L) : Set.of());
                q.setIntervals(List.of(new PerformanceReportQuery.Interval("current",
                        LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0))));
                var config = new Configuration();
                var bound = new XMLLanguageDriver().createSqlSource(config,
                        PerformanceReportSql.amounts(q), PerformanceReportQuery.class).getBoundSql(q);
                String sql = bound.getSql();
                String parsed = assertDoesNotThrow(() -> interceptor.parserSingle(sql, null), grouping + "/" + scope);
                assertDoesNotThrow(() -> CCJSqlParserUtil.parse(parsed));
                assertTrue(parsed.contains("tenant_id"));
                if (scope == 2) assertTrue(parsed.contains("1 = 0"), parsed);
                export("amounts-" + grouping + "-scope" + scope, config, bound, parsed, q);
            }
        }
    }

    @Test
    void supervisorPagingBindsPlainOffsetsThroughTenantParser() throws Exception {
        var interceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(1); }
        });
        for (int offset : List.of(0, 20)) {
            var request = new SalesOrderSupervisorPageReqVO();
            request.setPageNo(offset / 20 + 1); request.setPageSize(20); request.setHandled(false);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("request", request); params.put("userId", 1L); params.put("orderIds", null); params.put("offset", offset);
            var config = new Configuration();
            var bound = new XMLLanguageDriver().createSqlSource(config,
                    SalesOrderSupervisorConfirmationMapper.SqlProvider.supervisorPageSql(), Map.class).getBoundSql(params);
            String parsed = assertDoesNotThrow(() -> interceptor.parserSingle(bound.getSql(), null));
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(parsed));
            export("supervisor-offset" + offset, config, bound, parsed, params);
        }
    }

    @Test void unclassifiedHistoryParsesWithTheSameTenantAndDomainBoundary() throws Exception {
        var q=new PerformanceDetailQuery().setTenant(1L).setType("USER").setId(1L)
            .setUnclassified(true).setAllDepartments(true).setMissingDepartment(true).setSize(20);
        var config=new Configuration();
        for(String statement:List.of(PerformanceDetailSql.unclassifiedCount(q),PerformanceDetailSql.unclassifiedPage(q))) {
            var bound=new XMLLanguageDriver().createSqlSource(config,statement,PerformanceDetailQuery.class).getBoundSql(q);
            assertDoesNotThrow(()->CCJSqlParserUtil.parse(bound.getSql()));
            assertTrue(bound.getSql().contains("resolved_domain IS NULL"));
        }
    }
    private static void export(String name, Configuration config, BoundSql bound, String sql, Object request) throws Exception {
        String directory = System.getenv("EXCEPTION_QUERY_EXPORT_DIR");
        if (directory == null || directory.isBlank()) return;
        Path root = Path.of(directory).toAbsolutePath().normalize();
        assertTrue(root.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()),
                "Query evidence must be written under the temporary directory");
        var parameters = new ArrayList<Object>();
        var meta = config.newMetaObject(request);
        for (var mapping : bound.getParameterMappings()) {
            String property = mapping.getProperty();
            Object value = bound.hasAdditionalParameter(property) ? bound.getAdditionalParameter(property) : meta.getValue(property);
            parameters.add(value instanceof LocalDateTime time ? time.toString().replace('T', ' ') : value);
        }
        Files.createDirectories(root);
        Files.writeString(root.resolve(name + ".json"), JsonUtils.toJsonString(Map.of("sql", sql, "parameters", parameters)));
    }
}
