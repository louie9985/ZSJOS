package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmPendingTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchItemMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise Spring's real BEFORE_COMMIT/rollback lifecycle without starting shared services. */
class ContentReviewNodeTransactionTest {
    AnnotationConfigApplicationContext context;
    ContentReviewNotifyPublisher publisher;
    NotifyBusinessEventApi api;
    TestTransactions transactions;
    TransactionTemplate template;
    ContentReviewBatchDO batch;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        context = new AnnotationConfigApplicationContext(Config.class);
        publisher = context.getBean(ContentReviewNotifyPublisher.class);
        api = context.getBean(NotifyBusinessEventApi.class);
        transactions = context.getBean(TestTransactions.class);
        template = new TransactionTemplate(transactions);
        batch = new ContentReviewBatchDO().setId(50L).setBatchNo("CR-50")
                .setOperatorUserId(7L).setProcessInstanceId("process-1")
                .setContextSnapshotJson("{\"finalTaskKey\":\"actualFinal\"}");
        lenient().when(context.getBean(ContentReviewBatchMapper.class).selectById(50L)).thenReturn(batch);
    }

    @AfterEach void close() { context.close(); TenantContextHolder.clear(); }

    BpmTaskActionContext action() {
        return new BpmTaskActionContext().setTaskId("director-1").setProcessInstanceId("process-1")
                .setUserId(9L).setAction("APPROVE").setReason("通过");
    }

    @Test void eventWaitsForEngineCompletionAndSharesCommit() {
        template.executeWithoutResult(status -> {
            publisher.deferNodeResult(batch, "DIRECTOR", action());
            verifyNoInteractions(api);
            when(context.getBean(BpmProcessTaskApi.class).getPendingTasks("process-1")).thenReturn(List.of(
                    new BpmPendingTaskRespDTO("final-1", "actualFinal", 12L, LocalDateTime.now())));
        });
        var event = org.mockito.ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(api).publish(event.capture());
        assertEquals(Set.of(12L), event.getValue().getPayload().get("finalReviewerUserIds"));
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test void laterMaterialOrEngineFailureDoesNotPublish() {
        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            publisher.deferNodeResult(batch, "FINAL", action());
            throw new IllegalStateException("material collection failed");
        }));
        verifyNoInteractions(api);
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
    }

    @Test void outboxFailureRollsBackApprovalRatherThanSilentlyLosingResult() {
        doThrow(new IllegalStateException("outbox unavailable")).when(api).publish(any());
        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status ->
                publisher.deferNodeResult(batch, "FINAL", action())));
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
    }

    @Test void rollbackOnlyAndNonTransactionalValidationNeverDeliver() {
        template.executeWithoutResult(status -> {
            publisher.deferNodeResult(batch, "FINAL", action());
            status.setRollbackOnly();
        });
        publisher.deferNodeResult(batch, "FINAL", action());
        verifyNoInteractions(api);
        assertEquals(0, transactions.commits);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean ContentReviewNotifyPublisher publisher() { return new ContentReviewNotifyPublisher(); }
        @Bean ContentReviewBatchMapper batchMapper() { return mock(ContentReviewBatchMapper.class); }
        @Bean ContentReviewBatchItemMapper itemMapper() { return mock(ContentReviewBatchItemMapper.class); }
        @Bean BpmProcessTaskApi processTaskApi() { return mock(BpmProcessTaskApi.class); }
        @Bean NotifyBusinessEventApi api() { return mock(NotifyBusinessEventApi.class); }
        @Bean TestTransactions transactionManager() { return new TestTransactions(); }
    }

    static class TestTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
