package cn.iocoder.yudao.module.system.controller.admin.notice;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.system.service.notice.NoticeService;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeReadPageReqVO;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeControllerTest {
    @Configuration @EnableMethodSecurity
    static class SecurityConfig {
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean NoticeController controller() { return new NoticeController(); }
        @Bean NoticeService service() { return mock(NoticeService.class); }
    }

    @Test void readingStatisticsRequireQueryPermissionBeforeServiceAccess() {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfig.class)) {
            var controller = context.getBean(NoticeController.class);
            var service = context.getBean(NoticeService.class);
            var request = new NoticeReadPageReqVO(); request.setId(7L);
            assertThrows(AccessDeniedException.class, () -> controller.getReadSummary(7L));
            assertThrows(AccessDeniedException.class, () -> controller.getReadPage(request));
            verifyNoInteractions(service);
            when(context.getBean(SecurityFrameworkService.class).hasPermission("system:notice:query")).thenReturn(true);
            controller.getReadSummary(7L); controller.getReadPage(request);
            verify(service).getReadSummary(7L); verify(service).getReadPage(request);
        }
    }

    @Test
    void shouldKeepAnnouncementOperationsBehindServerPermissions() throws NoSuchMethodException {
        assertPermission("uploadAttachment", "@ss.hasAnyPermissions('system:notice:create','system:notice:update')",
                MultipartFile.class);
        assertPermission("publish", "@ss.hasPermission('system:notice:publish')", Long.class);
        assertPermission("offline", "@ss.hasPermission('system:notice:offline')", Long.class);
        assertPermission("getMyNoticePage", "@ss.hasPermission('system:notice:read')",
                cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeMyPageReqVO.class);
        assertPermission("getMyNoticeCursor", "@ss.hasPermission('system:notice:read')",
                cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeMyCursorReqVO.class);
        assertPermission("getRecipientOptions", "@ss.hasAnyPermissions('system:notice:create','system:notice:update')");
        assertPermission("getMyNotice", "@ss.hasPermission('system:notice:read')", Long.class);
        assertPermission("getUnreadSummary", "@ss.hasPermission('system:notice:read')");
        assertPermission("markRead", "@ss.hasPermission('system:notice:read')", Long.class);
        assertPermission("getReadSummary", "@ss.hasPermission('system:notice:query')", Long.class);
        assertPermission("getReadPage", "@ss.hasPermission('system:notice:query')",
                cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeReadPageReqVO.class);
    }

    private void assertPermission(String methodName, String expression, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = NoticeController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(expression);
    }

}
