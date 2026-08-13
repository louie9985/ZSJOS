package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderCommandMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_IDEMPOTENCY_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderCommandServiceTest {
    @InjectMocks private SalesOrderCommandService service;
    @Mock private SalesOrderCommandMapper mapper;

    @BeforeEach void setUp() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }

    @Test
    void replayAcceptsOnlyTheSameCommand() {
        SalesOrderCommandService.Command command = command("approve", "registrationReview", "task-1", 20L, "fp-1");
        when(mapper.selectByIdempotencyKey("key-1")).thenReturn(row(command));

        assertTrue(service.replay("key-1", command));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.replay("key-1", command("reject", "registrationReview", "task-1", 20L, "fp-1")));
        assertEquals(SALES_ORDER_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void replayDecisionUsesStoredNodeButRejectsCrossUserTaskAndRequest() {
        SalesOrderCommandService.Command stored = command("approve", "financeReview", "task-1", 20L, "fp-1");
        when(mapper.selectByIdempotencyKey("key-1")).thenReturn(row(stored));

        assertTrue(service.replayDecision("key-1",
                command("approve", null, "task-1", 20L, "fp-1")));
        assertConflict(() -> service.replayDecision("key-1", command("approve", null, "task-2", 20L, "fp-1")));
        assertConflict(() -> service.replayDecision("key-1", command("approve", null, "task-1", 21L, "fp-1")));
        assertConflict(() -> service.replayDecision("key-1", command("approve", null, "task-1", 20L, "fp-2")));
    }

    @Test
    void registerPersistsAllCommandAttributes() {
        SalesOrderCommandService.Command command = command("terminate", null, null, 20L, "fp-1");

        when(mapper.insertIgnore(eq(1L), any(SalesOrderCommandDO.class))).thenReturn(1);
        service.register("key-1", command);

        verify(mapper).insertIgnore(eq(1L), org.mockito.ArgumentMatchers.<SalesOrderCommandDO>argThat(row -> "key-1".equals(row.getIdempotencyKey())
                && row.getOrderId().equals(100L) && row.getApprovalRoundId().equals(200L)
                && "process-1".equals(row.getProcessInstanceId()) && "terminate".equals(row.getCommandType())
                && row.getTaskDefinitionKey() == null && row.getBpmTaskId() == null
                && row.getOperatorUserId().equals(20L) && "fp-1".equals(row.getRequestFingerprint())));
    }

    @Test
    void registerConcurrentDuplicateReadsAndValidatesWinner() {
        SalesOrderCommandService.Command command = command("approve", "registrationReview", "task-1", 20L, "fp-1");
        when(mapper.insertIgnore(eq(1L), any(SalesOrderCommandDO.class))).thenReturn(0);
        when(mapper.selectByIdempotencyKey("key-1")).thenReturn(row(command));

        assertDoesNotThrow(() -> service.register("key-1", command));
    }

    private void assertConflict(Runnable action) {
        ServiceException error = assertThrows(ServiceException.class, action::run);
        assertEquals(SALES_ORDER_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
    }

    private static SalesOrderCommandService.Command command(String type, String node, String task, Long user, String fp) {
        return new SalesOrderCommandService.Command(100L, 200L, "process-1", type, node, task, user, fp);
    }

    private static SalesOrderCommandDO row(SalesOrderCommandService.Command command) {
        SalesOrderCommandDO row = new SalesOrderCommandDO();
        row.setOrderId(command.orderId()); row.setApprovalRoundId(command.roundId());
        row.setProcessInstanceId(command.processInstanceId()); row.setCommandType(command.commandType());
        row.setTaskDefinitionKey(command.taskDefinitionKey()); row.setBpmTaskId(command.taskId());
        row.setOperatorUserId(command.operatorUserId()); row.setRequestFingerprint(command.requestFingerprint());
        return row;
    }
}
