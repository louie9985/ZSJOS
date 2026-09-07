package cn.iocoder.yudao.module.system.controller.admin.notice;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeControllerTest {

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
    }

    private void assertPermission(String methodName, String expression, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = NoticeController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(expression);
    }

}
