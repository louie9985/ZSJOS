package cn.iocoder.yudao.module.zsjos.service.pointsredemption;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessNodeStatusRespDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsRequestMapper;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointsIssuanceServiceTest {
    private static BpmProcessNodeStatusRespDTO consecutiveDecision(String key, Long reviewer) {
        var d = decision(key, null); d.setReviewerUserId(reviewer); d.setConsecutiveApprovalDeduplicated(true);
        d.setEndTime(java.time.LocalDateTime.of(2026, 1, 1, 10, 1)); return d;
    }

    @Test void consecutiveSameApproverIssuesWithoutInventingHumanActor() {
        row.setApproverIdsJson("[2,2]");
        var manual = decision("supervisor", 2L); manual.setEndTime(java.time.LocalDateTime.of(2026, 1, 1, 10, 0));
        var automatic = consecutiveDecision("chairman", 2L);
        when(tasks.getProcessNodeStatuses(anyString(), any())).thenReturn(List.of(automatic, manual));
        service.issue(9L);
        assertEquals("issued", row.getIssueStatus()); verify(platform).issue(row);
        assertNull(automatic.getActionUserId());
    }

    @Test void deduplicationCannotReplaceDifferentFrozenApprover() {
        var manual = decision("supervisor", 2L); manual.setEndTime(java.time.LocalDateTime.of(2026, 1, 1, 10, 0));
        when(tasks.getProcessNodeStatuses(anyString(), any())).thenReturn(List.of(manual, consecutiveDecision("chairman", 3L)));
        service.issue(9L);
        assertEquals("failed", row.getIssueStatus()); verify(platform, never()).issue(any());
    }

    @Test void deduplicationNeedsPriorManualDecisionAndMatchingReviewer() {
        var manual = decision("supervisor", 2L); manual.setEndTime(java.time.LocalDateTime.of(2026, 1, 1, 10, 0));
        var automatic = consecutiveDecision("chairman", 2L);
        assertTrue(PointsIssuanceService.validApprovalDecisions(List.of("supervisor", "chairman"), List.of(2L, 2L), List.of(manual, automatic), 1L));
        assertFalse(PointsIssuanceService.validApprovalDecisions(List.of("supervisor", "chairman"), List.of(2L, 2L), List.of(consecutiveDecision("supervisor", 2L), automatic), 1L));
        automatic.setReviewerUserId(3L);
        assertFalse(PointsIssuanceService.validApprovalDecisions(List.of("supervisor", "chairman"), List.of(2L, 2L), List.of(manual, automatic), 1L));
        automatic.setReviewerUserId(2L); automatic.setConsecutiveApprovalDeduplicated(false);
        assertFalse(PointsIssuanceService.validApprovalDecisions(List.of("supervisor", "chairman"), List.of(2L, 2L), List.of(manual, automatic), 1L));
        automatic.setConsecutiveApprovalDeduplicated(true); automatic.setEndTime(manual.getEndTime().minusSeconds(1));
        assertFalse(PointsIssuanceService.validApprovalDecisions(List.of("supervisor", "chairman"), List.of(2L, 2L), List.of(manual, automatic), 1L));
        automatic.setEndTime(null);
        assertFalse(PointsIssuanceService.validApprovalDecisions(List.of("supervisor", "chairman"), List.of(2L, 2L), List.of(manual, automatic), 1L));
    }

    PointsIssuanceService service;
    PointsRequestDO row;
    PointsPlatformClient platform;
    PointsRequestMapper mapper;
    BpmProcessTaskApi tasks;
    NotifyBusinessEventApi notifications;
    RLock lock;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        service = new PointsIssuanceService(); mapper = mock(PointsRequestMapper.class);
        platform = mock(PointsPlatformClient.class); tasks = mock(BpmProcessTaskApi.class);
        notifications = mock(NotifyBusinessEventApi.class);
        var redis = mock(RedissonClient.class); lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock); when(lock.tryLock()).thenReturn(true);
        var crypto = mock(PointsCrypto.class); when(crypto.encrypt(anyString())).thenReturn("encrypted-only");
        ReflectionTestUtils.setField(service, "requests", mapper);
        ReflectionTestUtils.setField(service, "platform", platform);
        ReflectionTestUtils.setField(service, "tasks", tasks);
        ReflectionTestUtils.setField(service, "notifications", notifications);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "crypto", crypto);
        ReflectionTestUtils.setField(service, "transactions", new TransactionTemplate(mock(PlatformTransactionManager.class)));
        row = new PointsRequestDO(); row.setId(9L); row.setTenantId(1L); row.setApplicantUserId(1L);
        row.setApprovalStatus("approved"); row.setIssueStatus("queued"); row.setAttempts(0);
        row.setNotified(false); row.setProcessInstanceId("process"); row.setTaskKeysJson("[\"supervisor\",\"chairman\"]");
        row.setApproverIdsJson("[2,3]"); row.setCreditsAmount(5000); row.setOrderId("test-order"); row.setRequestNo("JFTEST");
        when(mapper.lock(9L, 1L)).thenReturn(row);
        when(platform.currentBaseUrl()).thenReturn("https://zsj.nbility.ai/api/partner/v1");
        when(tasks.getProcessNodeStatuses(anyString(), any())).thenReturn(List.of(decision("supervisor",2L), decision("chairman",3L)));
        when(platform.number(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(5);
        when(platform.issue(row)).thenReturn(new JsonMapper().readTree("{\"id\":\"batch\",\"orderId\":\"test-order\",\"codes\":[{\"id\":\"code-id\",\"code\":\"KM-0123456789ABCDEF\",\"creditsAmount\":5000,\"status\":\"available\",\"expiresAt\":null}]}"));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    private static BpmProcessNodeStatusRespDTO decision(String key, Long user) {
        var d = new BpmProcessNodeStatusRespDTO(); d.setTaskDefinitionKey(key); d.setActionUserId(user); d.setStatus("approved"); return d;
    }
    @Test void notificationFailureRetriesWithoutIssuingAnotherCode() {
        when(notifications.publishDurable(any())).thenThrow(new IllegalStateException("unavailable")).thenReturn(1);
        service.issue(9L);
        assertEquals("issued",row.getIssueStatus()); assertFalse(row.getNotified()); assertEquals("encrypted-only",row.getCodeCiphertext());
        service.issue(9L); service.issue(9L);
        assertTrue(row.getNotified()); verify(platform,times(1)).issue(row); verify(notifications,times(2)).publishDurable(any());
    }
    @Test void automaticOrMissingChairmanDecisionNeverCallsPlatform() {
        when(tasks.getProcessNodeStatuses(anyString(),any())).thenReturn(List.of(decision("supervisor",2L),decision("chairman",null)));
        service.issue(9L); assertEquals("failed",row.getIssueStatus()); verify(platform,never()).issue(any()); verifyNoInteractions(notifications);
    }
    @Test void duplicateNodeCannotStandInForChairman() {
        when(tasks.getProcessNodeStatuses(anyString(),any())).thenReturn(List.of(decision("supervisor",2L),decision("supervisor",2L)));
        service.issue(9L); assertEquals("failed",row.getIssueStatus()); verify(platform,never()).issue(any());
    }
    @Test void anotherWorkerHoldingLockPreventsConcurrentIssue() {
        when(lock.tryLock()).thenReturn(false); service.issue(9L);
        verifyNoInteractions(mapper, platform, tasks, notifications); verify(lock,never()).unlock();
    }
    @Test void lostResponseKeepsOrderAndFrozenExpiryForRetry() {
        row.setValidDays(30);
        when(platform.issue(row)).thenThrow(new PointsPlatformClient.PlatformFailure("network_error",true,0));
        service.issue(9L); var expiry=row.getExpiresAt();
        assertEquals("queued",row.getIssueStatus()); assertNotNull(expiry);
        service.issue(9L); assertEquals(expiry,row.getExpiresAt()); assertEquals("test-order",row.getOrderId());
        assertEquals(2,row.getAttempts()); assertFalse(row.getNotified()); verifyNoInteractions(notifications);
    }
}
