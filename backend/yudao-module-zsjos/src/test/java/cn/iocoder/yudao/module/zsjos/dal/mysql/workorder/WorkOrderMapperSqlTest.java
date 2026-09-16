package cn.iocoder.yudao.module.zsjos.dal.mysql.workorder;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkOrderMapperSqlTest {
    private String poolSql() throws Exception {
        return String.join("", WorkOrderMapper.class
                .getMethod("selectEligiblePool", IPage.class, String.class, Long.class)
                .getAnnotation(Select.class).value()).replaceAll("#\\{[^}]+}", "?");
    }

    private String tenantSql(long tenantId) throws Exception {
        TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new LongValue(tenantId); }
        });
        return interceptor.parserSingle(poolSql(), null);
    }

    @Test
    void poolIsCompatibleWithTenantParserAndScopesEveryJoinedTable() throws Exception {
        String sql = tenantSql(1);
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
        for (String alias : new String[]{"wo", "u", "ur", "r"}) {
            assertTrue(sql.contains(alias + ".tenant_id = 1"), sql);
        }
    }

    @Test
    void poolUsesRequestedTenantInsteadOfFixedTenant() throws Exception {
        String sql = tenantSql(2);
        for (String alias : new String[]{"wo", "u", "ur", "r"}) {
            assertTrue(sql.contains(alias + ".tenant_id = 2"), sql);
        }
        assertFalse(sql.contains("tenant_id = 1"));
    }

    @Test
    void paginatedCountRemainsTenantScopedAndParserCompatible() throws Exception {
        String count = new PaginationInnerInterceptor().autoCountSql(new Page<>(1, 20), tenantSql(1));
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(count));
        assertTrue(count.contains("wo.tenant_id = 1"), count);
        assertTrue(count.contains("u.tenant_id = 1"), count);
    }
}
