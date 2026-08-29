package cn.iocoder.yudao.module.zsjos.framework.forcedform;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo.ForcedFormStatusRespVO;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants;
import cn.iocoder.yudao.module.zsjos.service.forcedform.ForcedFormService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForcedFormRequestFilterTest {

    @Mock
    private ForcedFormService forcedFormService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminRequestWithoutWorkbenchMarkerPassesThrough() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = invoke(request("/admin-api/zsjos/lead/page", false), invoked);

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
        verify(forcedFormService, never()).status(9L);
    }

    @Test
    void whitelistedWorkbenchForcedFormRuntimePassesThrough() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = invoke(request("/admin-api/zsjos/forced-form/10/runtime", true), invoked);

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
        verify(forcedFormService, never()).status(9L);
    }

    @Test
    void workbenchBusinessRequestWithPendingFormReturnsStableBusinessError() throws Exception {
        ForcedFormStatusRespVO status = new ForcedFormStatusRespVO();
        status.setPendingCount(1);
        status.setFirstPendingFormId(10L);
        status.setFirstPendingFormName("入职确认");
        when(forcedFormService.status(9L)).thenReturn(status);

        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = invoke(request("/admin-api/zsjos/lead/page", true), invoked);

        assertEquals(false, invoked.get());
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains(String.valueOf(ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED.getCode())));
        assertTrue(response.getContentAsString().contains("入职确认"));
    }

    @Test
    void workbenchBusinessRequestWithoutPendingFormPassesThrough() throws Exception {
        ForcedFormStatusRespVO status = new ForcedFormStatusRespVO();
        status.setPendingCount(0);
        when(forcedFormService.status(9L)).thenReturn(status);

        AtomicBoolean invoked = new AtomicBoolean();
        MockHttpServletResponse response = invoke(request("/admin-api/zsjos/lead/page", true), invoked);

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request, AtomicBoolean invoked) throws Exception {
        SecurityFrameworkUtils.setLoginUser(loginUser(), request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ForcedFormRequestFilter(new WebProperties(), forcedFormService).doFilter(request, response, (req, res) ->
                invoked.set(true));
        return response;
    }

    private static MockHttpServletRequest request(String uri, boolean workbench) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (workbench) {
            request.addHeader(ForcedFormRequestFilter.WORKBENCH_MARKER_HEADER, "PC");
        }
        return request;
    }

    private static LoginUser loginUser() {
        LoginUser user = new LoginUser();
        user.setId(9L);
        user.setUserType(UserTypeEnum.ADMIN.getValue());
        user.setTenantId(1L);
        user.setClientId(OAuth2ClientConstants.CLIENT_ID_ZSJOS_PC);
        return user;
    }
}
