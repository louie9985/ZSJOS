package cn.iocoder.yudao.module.zsjos.dal.mysql.order;

import org.junit.jupiter.api.Test;

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
}
