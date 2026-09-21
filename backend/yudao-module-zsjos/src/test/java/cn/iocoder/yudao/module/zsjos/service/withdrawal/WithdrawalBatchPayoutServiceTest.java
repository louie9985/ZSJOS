package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.WithdrawalController;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermissionAspect;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WithdrawalBatchPayoutServiceTest {
    private JdbcTemplate jdbc;
    private WithdrawalBatchPayoutService batch;
    private WithdrawalService service;
    private WithdrawalMapper mapper;
    private PermissionApi permissions;
    private WithdrawalNotifyPublisher publisher;
    private WithdrawalObjectPermissionProvider provider;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(9L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(30L), new org.springframework.mock.web.MockHttpServletRequest());
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:payout" + UUID.randomUUID(), "sa", ""));
        // Keep the in-memory database alive across the connections used by the real Spring transaction proxies.
        jdbc.execute("SET DB_CLOSE_DELAY -1");
        jdbc.execute("CREATE TABLE withdrawal(id BIGINT PRIMARY KEY, tenant_id BIGINT, status VARCHAR(30), paid_at TIMESTAMP, remark VARCHAR(500), operator_id BIGINT)");
        jdbc.execute("CREATE TABLE cashback(id BIGINT PRIMARY KEY, tenant_id BIGINT, status VARCHAR(30))");
        jdbc.execute("CREATE TABLE audit(id BIGINT)");
        jdbc.execute("CREATE TABLE outbox(id BIGINT)");
        jdbc.update("INSERT INTO withdrawal(id,tenant_id,status) VALUES(1,9,'approved'),(2,9,'approved'),(3,10,'approved')");
        jdbc.update("INSERT INTO cashback VALUES(1,9,'withdrawing'),(2,9,'withdrawing'),(3,10,'withdrawing')");
        mapper = mock(WithdrawalMapper.class);
        when(mapper.selectById(anyLong())).thenAnswer(call -> read(call.getArgument(0), TenantContextHolder.getRequiredTenantId()));
        when(mapper.selectByIdForUpdate(anyLong(), anyLong())).thenAnswer(call -> read(call.getArgument(0), call.getArgument(1)));
        when(mapper.updateById(any(WithdrawalDO.class))).thenAnswer(call -> {
            WithdrawalDO row = call.getArgument(0);
            return jdbc.update("UPDATE withdrawal SET status=?,paid_at=?,remark=?,operator_id=? WHERE id=? AND tenant_id=?",
                    row.getStatus(), row.getPaidAt(), row.getPayoutRemark(), row.getPaidByUserId(), row.getId(), TenantContextHolder.getRequiredTenantId());
        });
        var items = mock(WithdrawalItemMapper.class);
        when(items.selectByWithdrawalId(anyLong())).thenAnswer(call -> List.of(new WithdrawalItemDO().setCashbackId(call.getArgument(0))));
        var cashbacks = mock(CashbackMapper.class);
        when(cashbacks.selectByIdForUpdate(anyLong(), anyLong())).thenAnswer(call -> {
            List<CashbackDO> rows = jdbc.query("SELECT * FROM cashback WHERE id=? AND tenant_id=? FOR UPDATE",
                    (rs, i) -> new CashbackDO().setId(rs.getLong("id")).setStatus(rs.getString("status")).setVersion(0), call.getArgument(0), call.getArgument(1));
            return rows.isEmpty() ? null : rows.getFirst();
        });
        when(cashbacks.transitionStatus(anyLong(), anyInt(), anyString(), anyString())).thenAnswer(call -> jdbc.update(
                "UPDATE cashback SET status=? WHERE id=? AND tenant_id=? AND status=?", call.getArgument(3), call.getArgument(0),
                TenantContextHolder.getRequiredTenantId(), call.getArgument(2)));
        permissions = mock(PermissionApi.class);
        when(permissions.hasAnyPermissions(30L, "zsjos:withdrawal:payout")).thenReturn(true);
        provider = new WithdrawalObjectPermissionProvider();
        ReflectionTestUtils.setField(provider, "mapper", mapper);
        ReflectionTestUtils.setField(provider, "permissionApi", permissions);
        var audits = mock(BusinessAuditService.class);
        doAnswer(call -> { jdbc.update("INSERT INTO audit VALUES(?)", Long.valueOf(call.getArgument(3))); return null; })
                .when(audits).record(any(), any(), any(), any(), any(), any());
        publisher = mock(WithdrawalNotifyPublisher.class);
        doAnswer(call -> { jdbc.update("INSERT INTO outbox VALUES(?)", (Long) call.getArgument(1)); return null; })
                .when(publisher).publish(anyString(), anyLong(), anyString(), anyLong(), anyMap());
        var users = mock(AdminUserApi.class);
        when(users.getUserListByStatus(0)).thenReturn(List.of());
        var target = new WithdrawalServiceImpl();
        ReflectionTestUtils.setField(target, "withdrawalMapper", mapper);
        ReflectionTestUtils.setField(target, "itemMapper", items);
        ReflectionTestUtils.setField(target, "cashbackMapper", cashbacks);
        ReflectionTestUtils.setField(target, "auditService", audits);
        ReflectionTestUtils.setField(target, "notifyPublisher", publisher);
        ReflectionTestUtils.setField(target, "adminUserApi", users);
        ReflectionTestUtils.setField(target, "permissionApi", permissions);
        var transactions = new DataSourceTransactionManager(jdbc.getDataSource());
        var singleProxy = new AspectJProxyFactory(target);
        singleProxy.addAspect(new ZsjosPermissionAspect(List.of(provider)));
        singleProxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = singleProxy.getProxy();
        var batchTarget = new WithdrawalBatchPayoutService();
        ReflectionTestUtils.setField(batchTarget, "withdrawalService", service);
        ReflectionTestUtils.setField(batchTarget, "objectPermissionProvider", provider);
        var batchProxy = new AspectJProxyFactory(batchTarget);
        batchProxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        batch = batchProxy.getProxy();
    }

    @AfterEach void clear() {
        if (jdbc != null) jdbc.execute("SHUTDOWN");
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test void sharedTimeAndRemarkCommitOncePerDistinctIdInStableOrder() {
        var request = request(2L, 1L, 2L);
        request.setPaidAt(LocalDateTime.of(2026, 9, 20, 15, 30)); request.setRemark(" 批量登记 ");
        batch.recordPayouts(30L, request);
        assertEquals(2, count("withdrawal", "status='paid' AND paid_at=TIMESTAMP '2026-09-20 15:30:00' AND remark='批量登记' AND operator_id=30"));
        assertEquals(2, count("cashback", "status='withdrawn'"));
        assertEquals(2, count("audit", "1=1")); assertEquals(2, count("outbox", "1=1"));
        var order = inOrder(mapper);
        order.verify(mapper).selectByIdForUpdate(1L, 9L); order.verify(mapper).selectByIdForUpdate(2L, 9L);
    }

    @Test void omittedTimeAndRemarkRemainNullForSingleAndBatch() {
        service.recordPayout(1L, 30L, new WithdrawalPayoutReqVO());
        batch.recordPayouts(30L, request(2L));
        assertEquals(2, count("withdrawal", "status='paid' AND paid_at IS NULL AND remark IS NULL"));
    }

    @Test void laterStateConflictRollsBackEarlierWithdrawalCashbackAuditAndOutbox() {
        jdbc.update("UPDATE withdrawal SET status='paid' WHERE id=2");
        assertError(WITHDRAWAL_STATE_INVALID.getCode(), () -> batch.recordPayouts(30L, request(1L, 2L)));
        assertEquals(1, count("withdrawal", "id=1 AND status='approved'"));
        assertEquals(2, count("cashback", "tenant_id=9 AND status='withdrawing'"));
        assertEquals(0, count("audit", "1=1")); assertEquals(0, count("outbox", "1=1"));
    }

    @Test void laterNotificationFailureAlsoRollsBackEntireBatch() {
        doThrow(new IllegalStateException("controlled notification failure")).when(publisher)
                .publish(anyString(), eq(2L), anyString(), anyLong(), anyMap());
        assertThrows(IllegalStateException.class, () -> batch.recordPayouts(30L, request(1L, 2L)));
        assertEquals(2, count("withdrawal", "tenant_id=9 AND status='approved'"));
        assertEquals(2, count("cashback", "tenant_id=9 AND status='withdrawing'"));
        assertEquals(0, count("audit", "1=1")); assertEquals(0, count("outbox", "1=1"));
    }

    @Test void mixedTenantOrMissingTargetFailsBeforeAnyMutation() {
        for (long invalid : new long[]{3L, 99L}) {
            assertError(WITHDRAWAL_NOT_EXISTS.getCode(), () -> batch.recordPayouts(30L, request(1L, invalid)));
        }
        verify(mapper, never()).updateById(any(WithdrawalDO.class));
        assertEquals(3, count("withdrawal", "status='approved'"));
    }

    @Test void missingPermissionRejectsSingleAndBatchEvenForKnownObjects() {
        when(permissions.hasAnyPermissions(30L, "zsjos:withdrawal:payout")).thenReturn(false);
        assertError(WITHDRAWAL_PERMISSION_DENIED.getCode(), () -> batch.recordPayouts(30L, request(1L, 2L)));
        assertError(WITHDRAWAL_PERMISSION_DENIED.getCode(), () -> service.recordPayout(1L, 30L, new WithdrawalPayoutReqVO()));
        verify(mapper, never()).updateById(any(WithdrawalDO.class));
    }

    @Test void mixedObjectAuthorizationFailsBeforeFirstWrite() {
        var denyingProvider = spy(provider);
        doThrow(new ServiceException(WITHDRAWAL_PERMISSION_DENIED)).when(denyingProvider).check(2L, "payout", 30L);
        var target = new WithdrawalBatchPayoutService();
        ReflectionTestUtils.setField(target, "withdrawalService", service);
        ReflectionTestUtils.setField(target, "objectPermissionProvider", denyingProvider);
        assertError(WITHDRAWAL_PERMISSION_DENIED.getCode(), () -> target.recordPayouts(30L, request(1L, 2L)));
        verify(mapper, never()).updateById(any(WithdrawalDO.class));
    }

    @Test void requestValidationAndJsonContract() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new WithdrawalPayoutReqVO()).isEmpty());
            assertFalse(validator.validate(new WithdrawalBatchPayoutReqVO()).isEmpty());
            assertFalse(validator.validate(request()).isEmpty());
            assertFalse(validator.validate(request(0L)).isEmpty());
            assertFalse(validator.validate(request((Long) null)).isEmpty());
            assertFalse(validator.validate(request(Collections.nCopies(101, 1L).toArray(Long[]::new))).isEmpty());
            var valid = request(1L); valid.setRemark("中".repeat(500)); assertTrue(validator.validate(valid).isEmpty());
            valid.setRemark("中".repeat(501)); assertFalse(validator.validate(valid).isEmpty());
        }
        var parsed = JsonUtils.parseObject("{\"ids\":[1,2],\"paidAt\":\"2026-09-20T15:30:00\",\"remark\":\"登记\"}", WithdrawalBatchPayoutReqVO.class);
        assertEquals(LocalDateTime.of(2026, 9, 20, 15, 30), parsed.getPaidAt());
        assertEquals("@ss.hasPermission('zsjos:withdrawal:payout')", WithdrawalController.class
                .getMethod("batchPayout", WithdrawalBatchPayoutReqVO.class).getAnnotation(PreAuthorize.class).value());
    }

    private WithdrawalDO read(Long id, Long tenantId) {
        var rows = jdbc.query("SELECT * FROM withdrawal WHERE id=? AND tenant_id=? FOR UPDATE", (rs, i) ->
                new WithdrawalDO().setId(rs.getLong("id")).setStatus(rs.getString("status"))
                        .setWithdrawalNo("TEST-" + id).setApplicationAmount(BigDecimal.TEN), id, tenantId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private int count(String table, String condition) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + condition, Integer.class); }
    private static WithdrawalBatchPayoutReqVO request(Long... ids) {
        var request = new WithdrawalBatchPayoutReqVO(); request.setIds(Arrays.asList(ids)); return request;
    }
    private static void assertError(int code, org.junit.jupiter.api.function.Executable call) {
        assertEquals(code, assertThrows(ServiceException.class, call).getCode());
    }
}
