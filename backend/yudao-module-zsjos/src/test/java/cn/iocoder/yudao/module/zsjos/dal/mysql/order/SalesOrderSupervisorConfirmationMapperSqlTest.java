package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesOrderSupervisorConfirmationMapperSqlTest {

    @Test
    void pendingSupervisorInboxRequiresCurrentActiveApprovalRound() {
        String sql = SalesOrderSupervisorConfirmationMapper.SqlProvider.supervisorCursorSql();

        assertTrue(sql.contains("c.status = 'pending'"));
        assertTrue(sql.contains("zsjos_order o"));
        assertTrue(sql.contains("zsjos_order_approval_round r"));
        assertTrue(sql.contains("o.status = 'pending_approval'"));
        assertTrue(sql.contains("r.status = 'pending'"));
        assertTrue(sql.contains("o.current_approval_round_id = c.approval_round_id"));
    }

    @Test
    void handledSupervisorInboxKeepsHistoricalNonPendingConfirmations() {
        String sql = SalesOrderSupervisorConfirmationMapper.SqlProvider.supervisorCursorSql();

        assertTrue(sql.contains("c.status != 'pending'"));
    }

    @Test // 分页偏移必须在 Java 侧算好：MyBatis 的 #{} 占位符参与算术会让 MySQL 直接报语法错误
    void supervisorPageSqlPassesOffsetAsPlainParameter() {
        String sql = SalesOrderSupervisorConfirmationMapper.SqlProvider.supervisorPageSql();

        assertTrue(sql.contains("LIMIT #{request.pageSize} OFFSET #{offset}"));
        assertFalse(sql.contains("#{request.pageNo}"));
    }
}
