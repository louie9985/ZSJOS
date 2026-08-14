package cn.iocoder.yudao.module.system.framework.maintenance;

import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenanceModeFilterTest {
    private MaintenanceModeApi maintenanceModeApi;
    private MaintenanceModeFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        maintenanceModeApi = mock(MaintenanceModeApi.class);
        filter = new MaintenanceModeFilter(maintenanceModeApi);
        chain = mock(FilterChain.class);
    }

    @Test
    void disabledModeAllowsWrite() throws Exception {
        execute("POST", "/admin-api/zsjos/lead/create");
        verify(chain).doFilter(any(), any());
    }

    @Test
    void enabledModeRejectsOrdinaryWriteWith503() throws Exception {
        when(maintenanceModeApi.isEnabled()).thenReturn(true);
        MockHttpServletResponse response = execute("POST", "/admin-api/zsjos/lead/create");

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("系统维护中"));
        verifyNoInteractions(chain);
    }

    @Test
    void enabledModeAllowsReadAndFixedWriteExemptions() throws Exception {
        when(maintenanceModeApi.isEnabled()).thenReturn(true);
        execute("GET", "/admin-api/zsjos/lead/page");
        execute("POST", "/admin-api/system/auth/login");
        execute("PUT", "/admin-api/system/maintenance-mode");

        verify(chain, times(3)).doFilter(any(), any());
    }

    private MockHttpServletResponse execute(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
