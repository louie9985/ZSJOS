package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentReviewObjectPermissionProviderTest {
    @InjectMocks private ContentReviewObjectPermissionProvider provider;
    @Mock private ContentReviewBatchMapper batchMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ContentReviewAccessService accessService;

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    private ContentReviewBatchDO batch(long id, Long parent, boolean submitted) {
        var row = new ContentReviewBatchDO();
        row.setId(id); row.setTenantId(1L); row.setRevisionOfBatchId(parent);
        row.setOperatorUserId(7L); row.setDirectorUserId(8L);
        row.setSubmittedAt(submitted ? LocalDateTime.of(2026, 9, 22, 10, 0) : null);
        return row;
    }

    @Test void currentReviewerCanReadSubmittedAncestorsButCannotMutateThem() {
        var old = batch(1, null, true); var middle = batch(2, 1L, true); var current = batch(3, 2L, true);
        when(batchMapper.selectById(1L)).thenReturn(old);
        when(batchMapper.selectByRevisionOfBatchId(1L)).thenReturn(List.of(middle));
        when(batchMapper.selectByRevisionOfBatchId(2L)).thenReturn(List.of(current));
        when(accessService.hasCurrentTask(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
                .thenAnswer(call -> ((ContentReviewBatchDO) call.getArgument(0)).getId().equals(3L));
        assertTrue(provider.hasPermission(1L, "read", 9L));
        for (String action : List.of("submit", "publish", "director-review", "final-review")) {
            assertFalse(provider.hasPermission(1L, action, 9L));
            assertFalse(provider.hasPermission(1L, action, 7L));
        }
    }

    @Test void unreadableDraftDoesNotInheritReadFromItsDescendant() {
        when(batchMapper.selectById(1L)).thenReturn(batch(1, null, false));
        assertFalse(provider.hasPermission(1L, "read", 9L));
        verify(batchMapper, never()).selectByRevisionOfBatchId(1L);
    }

    @Test void unrelatedAndCrossTenantDescendantsDoNotGrantAccess() {
        var old = batch(1, null, true);
        var wrongParent = batch(2, 99L, true); wrongParent.setOperatorUserId(9L);
        var wrongTenant = batch(3, 1L, true); wrongTenant.setTenantId(2L); wrongTenant.setOperatorUserId(9L);
        when(batchMapper.selectById(1L)).thenReturn(old);
        when(batchMapper.selectByRevisionOfBatchId(1L)).thenReturn(List.of(wrongParent, wrongTenant));
        assertFalse(provider.hasPermission(1L, "read", 9L));
    }

    @Test void crossTenantRootIsDeniedEvenWithGlobalReadCapability() {
        var row = batch(1, null, true); row.setTenantId(2L);
        when(batchMapper.selectById(1L)).thenReturn(row);
        assertFalse(provider.hasPermission(1L, "read", 9L));
        verifyNoInteractions(permissionApi, accessService);
    }

    @Test void cyclicChainTerminatesWithoutManufacturingAccess() {
        var first = batch(1, 2L, true); var second = batch(2, 1L, true);
        when(batchMapper.selectById(1L)).thenReturn(first);
        when(batchMapper.selectByRevisionOfBatchId(1L)).thenReturn(List.of(second));
        when(batchMapper.selectByRevisionOfBatchId(2L)).thenReturn(List.of(first));
        assertFalse(provider.hasPermission(1L, "read", 9L));
    }

    @Test void directOperatorCanStillReadAndSubmitOwnLatestDraft() {
        when(batchMapper.selectById(1L)).thenReturn(batch(1, null, false));
        assertTrue(provider.hasPermission(1L, "read", 7L));
        assertTrue(provider.hasPermission(1L, "submit", 7L));
        assertFalse(provider.hasPermission(1L, "read", null));
    }

    @Test void ancestorAccessDoesNotGrantSiblingOrDescendantAccess() {
        var sibling = batch(4, 1L, true);
        when(batchMapper.selectById(4L)).thenReturn(sibling);
        assertFalse(provider.hasPermission(4L, "read", 9L));
        verify(batchMapper, never()).selectById(1L);
    }

    @Test void actualServiceReadBoundaryRejectsBeforeReadingFilesAndAllowsAncestor() {
        var old = batch(1, null, true); var current = batch(2, 1L, true);
        when(batchMapper.selectById(1L)).thenReturn(old);
        when(batchMapper.selectByRevisionOfBatchId(1L)).thenReturn(List.of(current));
        var service = new ContentReviewBatchService();
        var itemMapper = mock(cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchItemMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adminUserApi",
                mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class));
        var aspect = new cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermissionAspect(List.of(provider));
        var factory = new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(service);
        factory.addAspect(aspect);
        ContentReviewBatchService proxy = factory.getProxy();
        try (var security = mockStatic(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> proxy.get(1L, 9L));
            verifyNoInteractions(itemMapper);
            current.setOperatorUserId(9L);
            var response = proxy.get(1L, 9L);
            assertEquals(1L, response.getId());
            assertTrue(response.getAvailableActions().isEmpty());
            verify(itemMapper).selectByBatchId(1L);
        }
    }
}
