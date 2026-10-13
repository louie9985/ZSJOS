package cn.iocoder.yudao.module.zsjos.service.pointsredemption;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessProgressApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.PointsRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.PointsRequestMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.redisson.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PointsApprovalProgressServiceTest {
    PointsApprovalProgressService service;
    PointsRequestMapper requests;
    BpmProcessProgressApi progress;
    PermissionApi permissions;
    AdminUserApi users;
    NotifyBusinessEventApi notifications;
    RedissonClient redis;
    RLock lock;
    RBucket<Object> cooldown, replay;
    PointsRequestDO row;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(1L);
        service = new PointsApprovalProgressService(); requests = mock(PointsRequestMapper.class);
        progress = mock(BpmProcessProgressApi.class); permissions = mock(PermissionApi.class);
        users = mock(AdminUserApi.class); notifications = mock(NotifyBusinessEventApi.class);
        redis = mock(RedissonClient.class); lock = mock(RLock.class);
        cooldown = mock(RBucket.class); replay = mock(RBucket.class);
        var transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> { ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) invocation.getArgument(0)).accept(mock(org.springframework.transaction.TransactionStatus.class)); return null; })
                .when(transactions).executeWithoutResult(any());
        Map.of("requests", requests, "progress", progress, "permissions", permissions, "users", users,
                "notifications", notifications, "redis", redis, "transactions", transactions)
                .forEach((name, value) -> ReflectionTestUtils.setField(service, name, value));
        when(redis.getLock(anyString())).thenReturn(lock); when(lock.tryLock()).thenReturn(true);
        when(redis.getBucket(contains(":cooldown"))).thenReturn(cooldown);
        when(redis.getBucket(contains(":replay:"))).thenReturn(replay);
        row = new PointsRequestDO(); row.setId(42L); row.setTenantId(1L); row.setApplicantUserId(7L);
        row.setRequestNo("JFTEST042"); row.setApplicantName("测试申请人"); row.setProcessInstanceId("process-42"); row.setApprovalStatus("pending");
        when(requests.selectById(42L)).thenReturn(row); when(requests.lock(42L, 1L)).thenReturn(row);
        when(permissions.hasAnyPermissions(7L, PointsApprovalProgressService.URGE_PERMISSION)).thenReturn(true);
        var task = new BpmProcessProgressDTO.PendingTask("task-8", "审批节点", "node-8", 8L, "当前审批人", LocalDateTime.now());
        when(progress.getProgress("process-42", 7L)).thenReturn(new BpmProcessProgressDTO(1, List.of(), List.of(task)));
        when(progress.getCurrentTasks(Set.of("process-42"))).thenReturn(Map.of("process-42", List.of(task, task)));
        var user = new AdminUserRespDTO(); user.setId(8L); user.setStatus(0);
        when(users.getUserMap(Set.of(8L))).thenReturn(Map.of(8L, user));
        when(notifications.publishDurable(any())).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void progressAndCooldownAreReadOnly() {
        long next = System.currentTimeMillis() + 10000; when(cooldown.get()).thenReturn(next);
        var result = service.get(42L, 7L);
        assertEquals("AVAILABLE", result.getAvailability()); assertTrue(result.isCanUrge()); assertEquals(next, result.getNextUrgeAt());
        verifyNoInteractions(notifications);
    }
    @Test void progressFailureRemainsVisible() {
        when(progress.getProgress(anyString(), anyLong())).thenThrow(new IllegalStateException());
        var result = service.get(42L, 7L); assertEquals("UNAVAILABLE", result.getAvailability()); assertFalse(result.isCanUrge());
    }
    @Test void missingProcessIsUnavailable() {
        row.setProcessInstanceId(null); assertEquals("UNAVAILABLE", service.get(42L, 7L).getAvailability()); verifyNoInteractions(progress);
    }
    @Test void onlyApplicantWithConfiguredPermissionCanUrge() {
        assertEquals(1_090_296_002, assertThrows(ServiceException.class, () -> service.urge(42L, "request-key-00001", 9L)).getCode());
        when(permissions.hasAnyPermissions(7L, PointsApprovalProgressService.URGE_PERMISSION)).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.urge(42L, "request-key-00001", 7L)); verifyNoInteractions(notifications, redis);
    }
    @Test void dispatchUsesLiveDeduplicatedRecipientsAndTenant() {
        service.urge(42L, "request-key-00001", 7L);
        var capture = ArgumentCaptor.forClass(NotifyBusinessEvent.class); verify(notifications).publishDurable(capture.capture());
        assertEquals(1L, capture.getValue().getTenantId()); assertEquals("task-8", capture.getValue().getPayload().get("taskId"));
        assertEquals(1, capture.getValue().getFixedRecipients().size());
        verify(cooldown).set(anyLong(), eq(Duration.ofMinutes(30))); verify(replay).set(true, Duration.ofDays(1)); verify(lock).unlock();
    }
    @Test void replayAndCooldownDoNotNotify() {
        when(replay.get()).thenReturn(true); service.urge(42L, "request-key-00001", 7L); verifyNoInteractions(notifications);
        when(replay.get()).thenReturn(null); when(cooldown.get()).thenReturn(System.currentTimeMillis() + 60000);
        assertEquals(1_090_296_009, assertThrows(ServiceException.class, () -> service.urge(42L, "request-key-00002", 7L)).getCode());
        verifyNoInteractions(notifications);
    }
    @Test void finishedProcessCannotBeUrged() {
        row.setApprovalStatus("approved"); assertEquals(1_090_296_010, assertThrows(ServiceException.class, () -> service.urge(42L, "request-key-00001", 7L)).getCode());
        verifyNoInteractions(notifications); verify(cooldown, never()).set(any(), any(Duration.class));
    }
    @Test void noAssigneeOrDisabledUserCannotBeUrged() {
        when(users.getUserMap(any())).thenReturn(Map.of());
        assertEquals(1_090_296_011, assertThrows(ServiceException.class, () -> service.urge(42L, "request-key-00001", 7L)).getCode());
        verifyNoInteractions(notifications);
    }
    @Test void unavailableNotificationDoesNotStartCooldown() {
        when(notifications.publishDurable(any())).thenReturn(0);
        assertEquals(1_090_296_012, assertThrows(ServiceException.class, () -> service.urge(42L, "request-key-00001", 7L)).getCode());
        verify(cooldown, never()).set(any(), any(Duration.class)); verify(replay, never()).set(any(), any(Duration.class)); verify(lock).unlock();
    }
    @Test void lockContentionDoesNotSend() {
        when(lock.tryLock()).thenReturn(false);
        assertEquals(1_090_296_008, assertThrows(ServiceException.class, () -> service.urge(42L, "request-key-00001", 7L)).getCode()); verifyNoInteractions(notifications);
    }
    @Test void objectProviderLimitsUrgeToOwnerAndDoesNotBypassMissingRows() {
        var provider = new PointsObjectPermissionProvider();
        ReflectionTestUtils.setField(provider, "requests", requests);
        ReflectionTestUtils.setField(provider, "permissions", permissions);
        ReflectionTestUtils.setField(provider, "progress", progress);
        assertTrue(provider.hasPermission(42L, "urge", 7L));
        assertFalse(provider.hasPermission(42L, "urge", 9L));
        assertFalse(provider.hasPermission(99L, "urge", 7L));
        when(permissions.hasAnyPermissions(9L, PointsRequestService.PERM + "manage")).thenReturn(true);
        assertTrue(provider.hasPermission(42L, "read", 9L));
        assertFalse(provider.hasPermission(42L, "urge", 9L));
    }
    @Test void currentTaskWithoutAssigneeFailsWithoutNullMapLookup() {
        var task = new BpmProcessProgressDTO.PendingTask("unassigned", "审批", "node", null, null, LocalDateTime.now());
        when(progress.getCurrentTasks(any())).thenReturn(Map.of("process-42", List.of(task)));
        when(users.getUserMap(Set.of())).thenReturn(Map.of());
        assertEquals(1_090_296_011, assertThrows(ServiceException.class, () -> service.urge(42L, "request-key-00001", 7L)).getCode());
    }
}
