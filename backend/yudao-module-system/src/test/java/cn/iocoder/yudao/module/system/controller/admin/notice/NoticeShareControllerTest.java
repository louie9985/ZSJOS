package cn.iocoder.yudao.module.system.controller.admin.notice;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.system.service.notice.NoticeShareService;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.controller.pub.notice.PublicNoticeShareController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class NoticeShareControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean(name="ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean NoticeShareController controller() { return new NoticeShareController(); }
        @Bean NoticeShareService service() { return mock(NoticeShareService.class); }
    }
    @Test void allManagementOperationsRequireBothPermissions() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = ctx.getBean(NoticeShareController.class); var ss = ctx.getBean(SecurityFrameworkService.class);
            var service = ctx.getBean(NoticeShareService.class);
            for (String only : new String[]{"system:notice:query", "system:notice:share"}) {
                reset(ss); when(ss.hasPermission(only)).thenReturn(true);
                assertThrows(AccessDeniedException.class, () -> controller.get(1L));
                assertThrows(AccessDeniedException.class, () -> controller.open(new NoticeShareOpenReqVO()));
                assertThrows(AccessDeniedException.class, () -> controller.close(new NoticeShareCloseReqVO()));
            }
            verifyNoInteractions(service);
            when(ss.hasPermission("system:notice:query")).thenReturn(true);
            when(ss.hasPermission("system:notice:share")).thenReturn(true);
            controller.get(1L); controller.open(new NoticeShareOpenReqVO()); controller.close(new NoticeShareCloseReqVO());
            verify(service).get(1L);
        }
    }
    @Test void anonymousProjectionUsesNoStoreAndHeaderToken() {
        var controller = new PublicNoticeShareController(); var service = mock(NoticeShareService.class);
        ReflectionTestUtils.setField(controller, "shareService", service);
        var response = new MockHttpServletResponse();
        controller.get(null, "header-token", response);
        verify(service).publicNotice("header-token");
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
    }
    @Test void anonymousFailuresPreserveBusinessCodesWithoutExposingExceptionDetails() {
        var controller = new PublicNoticeShareController();
        var response = new MockHttpServletResponse();
        var business = controller.failure(new cn.iocoder.yudao.framework.common.exception.ServiceException(
                1002008007, "分享内容已失效"), response);
        assertEquals(1002008007, business.getCode());
        var transientFailure = controller.failure(new IllegalStateException("sensitive query payload"), response);
        assertEquals(500, transientFailure.getCode());
        assertFalse(transientFailure.getMsg().contains("sensitive"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }
}
