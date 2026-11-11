package cn.iocoder.yudao.module.zsjos.service.versionupdate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.versionupdate.vo.VersionUpdateVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.versionupdate.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.versionupdate.*;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.versionupdate.VersionUpdateConstants.*;
import java.util.List;
import java.util.Set;

class VersionUpdateServiceTest {
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void recipientAndManagementPermissionsRemainIndependent() {
        var provider = new VersionUpdatePermissionProvider();
        var updates = mock(VersionUpdateMapper.class);
        var recipients = mock(VersionUpdateRecipientMapper.class);
        var permissions = mock(PermissionApi.class);
        ReflectionTestUtils.setField(provider,"updates",updates);
        ReflectionTestUtils.setField(provider,"recipients",recipients);
        ReflectionTestUtils.setField(provider,"permissions",permissions);
        TenantContextHolder.setTenantId(1L);
        var row = new VersionUpdateDO(); row.setId(1L); row.setTenantId(1L); row.setDeleted(false);
        when(updates.selectById(1L)).thenReturn(row);
        assertFalse(provider.hasPermission(1L,"read",7L));
        when(permissions.hasAnyPermissions(7L,READ)).thenReturn(true);
        assertFalse(provider.hasPermission(1L,"read",7L));
        when(recipients.find(1L,7L)).thenReturn(new VersionUpdateRecipientDO());
        assertTrue(provider.hasPermission(1L,"read",7L));
        assertFalse(provider.hasPermission(1L,"acknowledge",7L));
        when(permissions.hasAnyPermissions(7L,ACKNOWLEDGE)).thenReturn(true);
        assertTrue(provider.hasPermission(1L,"acknowledge",7L));
        assertFalse(provider.hasPermission(1L,"query",7L));
        when(permissions.hasAnyPermissions(7L,QUERY)).thenReturn(true);
        assertTrue(provider.hasPermission(1L,"query",7L));
        assertFalse(provider.hasPermission(1L,"publish",7L));
        when(permissions.hasAnyPermissions(7L,PREFIX+"publish")).thenReturn(true);
        assertTrue(provider.hasPermission(1L,"publish",7L));
        row.setTenantId(2L); assertFalse(provider.hasPermission(1L,"publish",7L));
        row.setTenantId(1L); row.setDeleted(true); assertFalse(provider.hasPermission(1L,"read",7L));
        assertFalse(provider.hasPermission(1L,null,7L));
    }
    @Test void offlineUpdateCannotBeAcknowledgedOrRepublished() {
        var service = new VersionUpdateService();
        var updates = mock(VersionUpdateMapper.class);
        var recipients = mock(VersionUpdateRecipientMapper.class);
        ReflectionTestUtils.setField(service,"updates",updates);
        ReflectionTestUtils.setField(service,"recipients",recipients);
        ReflectionTestUtils.setField(service,"objects",mock(VersionUpdatePermissionProvider.class));
        TenantContextHolder.setTenantId(1L);
        var row = new VersionUpdateDO(); row.setId(1L); row.setTenantId(1L); row.setStatus("OFFLINE");
        when(updates.lock(1L)).thenReturn(row);
        assertEquals(STATE.getCode(), assertThrows(ServiceException.class,()->service.acknowledge(1L)).getCode());
        assertEquals(STATE.getCode(), assertThrows(ServiceException.class,()->service.publish(1L)).getCode());
        verifyNoInteractions(recipients);
    }
    @Test void createRejectsUnqualifiedRecipientsBeforeWriting() {
        var service = new VersionUpdateService();
        var permissions = mock(PermissionApi.class);
        var updates = mock(VersionUpdateMapper.class);
        ReflectionTestUtils.setField(service,"permissions",permissions);
        ReflectionTestUtils.setField(service,"updates",updates);
        TenantContextHolder.setTenantId(1L);
        var request = new VersionUpdateVO.Save(); request.setTitle("Synthetic update"); request.setContent("<p>Content</p>"); request.setTargetUserIds(List.of(8L));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            when(permissions.hasAnyPermissions(7L,QUERY)).thenReturn(true);
            when(permissions.hasAnyPermissions(7L,PREFIX+"create")).thenReturn(true);
            when(permissions.getEnabledUserIdsByPermission(READ)).thenReturn(Set.of(8L));
            when(permissions.getEnabledUserIdsByPermission(ACKNOWLEDGE)).thenReturn(Set.of());
            assertEquals(RECIPIENT.getCode(),assertThrows(ServiceException.class,()->service.create(request)).getCode());
            verifyNoInteractions(updates);
        }
    }
    @Test void publishedRetryDoesNotDuplicateRecipients() {
        var service = new VersionUpdateService();
        var updates = mock(VersionUpdateMapper.class);
        var recipients = mock(VersionUpdateRecipientMapper.class);
        ReflectionTestUtils.setField(service,"updates",updates);
        ReflectionTestUtils.setField(service,"recipients",recipients);
        ReflectionTestUtils.setField(service,"objects",mock(VersionUpdatePermissionProvider.class));
        TenantContextHolder.setTenantId(1L);
        var row = new VersionUpdateDO(); row.setId(1L); row.setTenantId(1L); row.setStatus("PUBLISHED");
        when(updates.lock(1L)).thenReturn(row);
        service.publish(1L);
        verifyNoInteractions(recipients);
        verify(updates,never()).updateById(any(VersionUpdateDO.class));
    }
    @Test void inlineImagesAreOwnedAndStoredAsFileReferences() {
        var service = new VersionUpdateService();
        var permissions = mock(PermissionApi.class);
        var updates = mock(VersionUpdateMapper.class);
        var users = mock(AdminUserApi.class);
        var files = mock(cn.iocoder.yudao.module.infra.api.file.FileApi.class);
        var cleaner = mock(XssCleaner.class);
        ReflectionTestUtils.setField(service,"permissions",permissions);
        ReflectionTestUtils.setField(service,"updates",updates);
        ReflectionTestUtils.setField(service,"users",users);
        ReflectionTestUtils.setField(service,"files",files);
        ReflectionTestUtils.setField(service,"cleaner",cleaner);
        when(cleaner.clean(anyString())).thenAnswer(call -> call.getArgument(0));
        TenantContextHolder.setTenantId(1L);
        var user = new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO(); user.setId(8L); user.setStatus(0);
        when(users.getUserList(anyCollection())).thenReturn(List.of(user));
        var file = new cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO();
        file.setId(21L); file.setCreator("7"); file.setPath("zsjos/version-update/7/image.png");
        when(files.getFileInfo(21L)).thenReturn(file);
        var request = new VersionUpdateVO.Save(); request.setTitle("Update"); request.setTargetUserIds(List.of(8L));
        request.setContent("<p>Update</p><img src=\"https://files.invalid/image?signature=temporary\">");
        var inline = new VersionUpdateVO.Attachment(); inline.setId(21L); inline.setUrl("https://files.invalid/image?signature=temporary");
        request.setInlineFiles(List.of(inline));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            when(permissions.hasAnyPermissions(7L,QUERY)).thenReturn(true);
            when(permissions.hasAnyPermissions(7L,PREFIX+"create")).thenReturn(true);
            when(permissions.getEnabledUserIdsByPermission(READ)).thenReturn(Set.of(8L));
            when(permissions.getEnabledUserIdsByPermission(ACKNOWLEDGE)).thenReturn(Set.of(8L));
            service.create(request);
            var captured = org.mockito.ArgumentCaptor.forClass(VersionUpdateDO.class);
            verify(updates).insert(captured.capture());
            assertTrue(captured.getValue().getContent().contains("https://version-update.invalid/file/21"));
            assertFalse(captured.getValue().getContent().contains("signature"));
            assertEquals("[21]",captured.getValue().getInlineFileIds());
            clearInvocations(updates);
            file.setCreator("99");
            assertEquals(FILE.getCode(),assertThrows(ServiceException.class,()->service.create(request)).getCode());
            verifyNoInteractions(updates);
        }
    }
    @Test void controllersDeclareIndependentPermissions() {
        var controller = cn.iocoder.yudao.module.zsjos.controller.admin.versionupdate.VersionUpdateController.class;
        for (var method : controller.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            var annotation = method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            assertNotNull(annotation, method.getName());
            assertTrue(annotation.value().contains(PREFIX), method.getName());
            assertFalse(annotation.value().contains("system:notice"));
        }
    }
    @Test void recipientDetailOmitsRosterAndRefreshesInlineUrls() {
        var service = new VersionUpdateService();
        var updates = mock(VersionUpdateMapper.class);
        var recipients = mock(VersionUpdateRecipientMapper.class);
        var files = mock(cn.iocoder.yudao.module.infra.api.file.FileApi.class);
        ReflectionTestUtils.setField(service,"updates",updates);
        ReflectionTestUtils.setField(service,"recipients",recipients);
        ReflectionTestUtils.setField(service,"files",files);
        ReflectionTestUtils.setField(service,"objects",mock(VersionUpdatePermissionProvider.class));
        TenantContextHolder.setTenantId(1L);
        var row = new VersionUpdateDO(); row.setId(1L); row.setTenantId(1L); row.setStatus("PUBLISHED");
        row.setTargetUserIds("[7,8]"); row.setAttachmentIds("[]"); row.setInlineFileIds("[21]");
        row.setContent("<img src=\"https://version-update.invalid/file/21\">");
        when(updates.selectById(1L)).thenReturn(row);
        when(files.presignGetUrl(21L,600)).thenReturn("https://files.invalid/new-signature");
        var result=service.myDetail(1L);
        assertNull(result.getTargetUserIds());
        assertNull(result.getRecipientCount());
        assertTrue(result.getContent().contains("https://files.invalid/new-signature"));
        assertTrue(result.getContent().contains("data-version-file=\"21\""));
    }
}
