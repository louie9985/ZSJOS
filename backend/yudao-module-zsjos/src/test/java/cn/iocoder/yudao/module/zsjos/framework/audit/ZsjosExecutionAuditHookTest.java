package cn.iocoder.yudao.module.zsjos.framework.audit;

import cn.iocoder.yudao.framework.audit.ExecutionAuditContext;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZsjosExecutionAuditHookTest {
    private final BusinessAuditService service = mock(BusinessAuditService.class);
    private final ZsjosExecutionAuditHook hook = new ZsjosExecutionAuditHook(service);

    @AfterEach
    void cleanUp() { TenantContextHolder.clear(); }

    @Test
    void explicitExecutionTenantOverridesAmbientTenantAndIgnoreFlag() {
        TenantContextHolder.setTenantId(99L);
        TenantContextHolder.setIgnore(true);
        doAnswer(call -> {
            assertEquals(2L, TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            Map<?, ?> details = call.getArgument(5);
            assertEquals("FAILURE", details.get("resultStatus"));
            assertEquals("IllegalStateException", details.get("errorType"));
            return null;
        }).when(service).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        hook.onFailure(context("2"), 10, new IllegalStateException("private payload"));
        assertEquals(99L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
        verify(service).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void completionWithoutAmbientTenantUsesContextAndRestoresNullEvenOnInsertFailure() {
        doAnswer(call -> {
            assertEquals(1L, TenantContextHolder.getTenantId());
            throw new IllegalStateException("synthetic insert failure");
        }).when(service).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        assertThrows(IllegalStateException.class, () -> hook.onSuccess(context("1"), 10));
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void ordinaryExecutionWithoutExplicitTenantRetainsCurrentTenantContract() {
        TenantContextHolder.setTenantId(3L);
        doAnswer(call -> {
            assertEquals(3L, TenantContextHolder.getTenantId());
            return null;
        }).when(service).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        hook.onSuccess(context(null), 10);
        verify(service).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    private ExecutionAuditContext context(String tenant) {
        return new ExecutionAuditContext("ASYNC", "test", null, null, null, null, tenant, Map.of());
    }
}
