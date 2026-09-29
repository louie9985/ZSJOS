package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaLeadFactMapperContractTest {

    @Test
    void bothQueriesUseFrozenContributorAndDepartmentScope() throws NoSuchMethodException {
        String leads = sql("leads");
        String orders = sql("firstOrders");
        for (String statement : new String[] {leads, orders}) {
            assertTrue(statement.contains("l.contribution_user_id_snapshot=#{scopeId}"));
            assertTrue(statement.contains("l.contribution_dept_id_snapshot=#{scopeId}"));
            assertTrue(statement.contains("l.contribution_dept_id_snapshot IN"));
            assertTrue(statement.contains("centerDeptIds"));
            assertTrue(statement.contains("AND 1=0"));
            assertFalse(statement.contains("l.creator=#{scopeId}"));
        }
        assertTrue(orders.contains("MIN(o.effective_at) effectiveAt"));
        assertTrue(orders.contains("o.status='effective'"));
        assertTrue(orders.contains("o.order_type &lt;&gt; 'repurchase'"));
        assertTrue(orders.contains("GROUP BY o.lead_id"));
    }

    private String sql(String method) throws NoSuchMethodException {
        return String.join(" ", MediaLeadFactMapper.class
                .getMethod(method, Long.class, String.class, Long.class, java.util.List.class)
                .getAnnotation(Select.class).value());
    }
}
