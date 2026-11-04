package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionTaskMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;

class ExamCollectionPermissionTest {
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void configuredPageAndActionAreCumulativeAndTenantBound() {
        var provider = new ExamCollectionPermissionProvider();
        var tasks = mock(ExamCollectionTaskMapper.class); var permissions = mock(PermissionApi.class);
        ReflectionTestUtils.setField(provider, "tasks", tasks); ReflectionTestUtils.setField(provider, "permissions", permissions);
        TenantContextHolder.setTenantId(1L);
        var row = new ExamCollectionTaskDO(); row.setId(1L); row.setTenantId(1L); row.setDeleted(false);
        when(tasks.selectById(1L)).thenReturn(row);
        assertFalse(provider.hasPermission(1L, "query", 7L));
        when(permissions.hasAnyPermissions(7L, QUERY)).thenReturn(true);
        assertTrue(provider.hasPermission(1L, "query", 7L));
        assertFalse(provider.hasPermission(1L, "resolve", 7L));
        when(permissions.hasAnyPermissions(7L, RESOLVE)).thenReturn(true);
        assertTrue(provider.hasPermission(1L, "resolve", 7L));
        row.setTenantId(2L); assertFalse(provider.hasPermission(1L, "resolve", 7L));
        row.setTenantId(1L); row.setDeleted(true); assertFalse(provider.hasPermission(1L, "query", 7L));
        assertFalse(provider.hasPermission(1L, "unknown", 7L));
        assertFalse(provider.hasPermission(1L, null, 7L));
        assertFalse(provider.hasPermission(1L, "query", null));
    }
    @Test void tokensAreRandomAndIdentityIndexUsesKeyedNormalizedHash() {
        var tokens = new ExamCollectionTokens();
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> tokens.identityHash("syntheticX"));
        ReflectionTestUtils.setField(tokens, "key", "isolated-test-key");
        var first = tokens.create(); var second = tokens.create();
        assertEquals(43, first.length()); assertNotEquals(first, second);
        assertEquals(64, tokens.hash(first).length());
        assertEquals(tokens.identityHash("syntheticX"), tokens.identityHash(" syntheticx "));
        var hash = tokens.identityHash("syntheticX");
        ReflectionTestUtils.setField(tokens, "key", "another-isolated-key");
        assertNotEquals(hash, tokens.identityHash("syntheticX"));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> tokens.hash("short"));
    }
}
