package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaLeadFactMapperParsingTest {

    @Test
    void mapperRegistersAndRendersBothQueriesForEveryScope() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(MediaLeadFactMapper.class);
        for (String method : new String[] {"leads", "firstOrders"}) {
            var statement = configuration.getMappedStatement(MediaLeadFactMapper.class.getName() + "." + method);
            for (String scope : new String[] {"USER", "DEPT", "CENTER"}) {
                var boundSql = statement.getBoundSql(Map.of("tenantId", 1L, "scopeType", scope,
                        "scopeId", 2L, "centerDeptIds", List.of(3L, 4L)));
                String sql = boundSql.getSql().replaceAll("\\s+", " ");
                assertTrue(sql.contains("tenant_id=?"));
                assertEquals("CENTER".equals(scope) ? 3 : 2, boundSql.getParameterMappings().size());
                String scopeCondition = switch (scope) {
                    case "USER" -> "l.contribution_user_id_snapshot=?";
                    case "DEPT" -> "l.contribution_dept_id_snapshot=?";
                    default -> "l.contribution_dept_id_snapshot IN";
                };
                assertTrue(sql.contains(scopeCondition));
                assertFalse(sql.contains("&lt;"));
                if ("firstOrders".equals(method)) {
                    assertTrue(sql.contains("o.order_type <> 'repurchase'"));
                    assertTrue(sql.contains("MIN(o.effective_at) effectiveAt"));
                    assertTrue(sql.contains("GROUP BY o.lead_id"));
                }
            }
        }
    }

    @Test
    void emptyCenterScopeRendersDenyCondition() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(MediaLeadFactMapper.class);
        for (String method : new String[] {"leads", "firstOrders"}) {
            var statement = configuration.getMappedStatement(MediaLeadFactMapper.class.getName() + "." + method);
            var boundSql = statement.getBoundSql(Map.of("tenantId", 1L, "scopeType", "CENTER",
                    "scopeId", 2L, "centerDeptIds", List.of()));
            assertTrue(boundSql.getSql().contains("AND 1=0"));
            assertEquals(1, boundSql.getParameterMappings().size());
        }
    }
}
