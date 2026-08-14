package cn.iocoder.yudao.module.zsjos.framework.impersonation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.service.impersonation.ImpersonationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImpersonationInterceptorTest {
    private final ImpersonationService service = mock(ImpersonationService.class);
    private final ImpersonationInterceptor interceptor = new ImpersonationInterceptor(service);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsWriteBeforeUsingSession() {
        MockHttpServletRequest request = request("POST");
        assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(service);
    }

    @Test
    void switchesReadIdentityAndRecordsOriginal() {
        LoginUser administrator = new LoginUser().setId(10L).setUserType(2).setInfo(new HashMap<>());
        MockHttpServletRequest request = request("GET");
        SecurityFrameworkUtils.setLoginUser(administrator, request);
        when(service.useReadSession(10L, 20L, "GET", "/admin-api/zsjos/lead/page"))
                .thenReturn(new ImpersonationService.ImpersonationContext(20L, 30L, "目标员工", 40L));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(30L, SecurityFrameworkUtils.getLoginUserId());
        assertEquals(40L, SecurityFrameworkUtils.getLoginUserDeptId());
        assertEquals(10L, administrator.getContext("zsjos.impersonation.originalUserId", Long.class));
        assertEquals(20L, administrator.getContext("zsjos.impersonation.sessionId", Long.class));
    }

    private static MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/admin-api/zsjos/lead/page");
        request.addHeader(ImpersonationInterceptor.HEADER, "20");
        return request;
    }
}
