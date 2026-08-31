package cn.iocoder.yudao.module.zsjos.framework.mediascreen;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MediaScreenAccessFilterTest {
    @Test void matchesIpv4AndCidr() {
        assertTrue(MediaScreenAccessFilter.matches("192.168.10.9", "192.168.10.0/24"));
        assertFalse(MediaScreenAccessFilter.matches("192.168.11.9", "192.168.10.0/24"));
        assertTrue(MediaScreenAccessFilter.matches("127.0.0.1", "127.0.0.1"));
    }
    @Test void matchesIpv6Cidr() {
        assertTrue(MediaScreenAccessFilter.matches("2001:db8::1", "2001:db8::/32"));
        assertFalse(MediaScreenAccessFilter.matches("2001:db9::1", "2001:db8::/32"));
    }
    @Test void rejectsInvalidRanges() {
        assertFalse(MediaScreenAccessFilter.matches("127.0.0.1", "bad-range"));
        assertFalse(MediaScreenAccessFilter.matches("127.0.0.1", "127.0.0.1/40"));
    }

    @Test void returnsServiceUnavailableWhenFeatureIsDisabled() throws Exception {
        MediaScreenProperties properties = new MediaScreenProperties();
        MockHttpServletResponse response = invoke(properties, request("GET", "127.0.0.1", "1"), new AtomicBoolean());

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("媒体大屏服务未开启"));
    }

    @Test void rejectsUnsupportedMethodWithStableJsonError() throws Exception {
        MediaScreenProperties properties = allowedProperties("127.0.0.1");
        MockHttpServletResponse response = invoke(properties, request("POST", "127.0.0.1", "1"), new AtomicBoolean());

        assertEquals(405, response.getStatus());
        assertEquals("GET", response.getHeader("Allow"));
        assertTrue(response.getContentAsString().contains("媒体大屏仅支持 GET 请求"));
    }

    @Test void rejectsMissingOrInvalidTenantIdAsBadRequest() throws Exception {
        MediaScreenProperties properties = allowedProperties("127.0.0.1");

        assertEquals(400, invoke(properties, request("GET", "127.0.0.1", null), new AtomicBoolean()).getStatus());
        assertEquals(400, invoke(properties, request("GET", "127.0.0.1", "0"), new AtomicBoolean()).getStatus());
        assertEquals(400, invoke(properties, request("GET", "127.0.0.1", "invalid"), new AtomicBoolean()).getStatus());
    }

    @Test void rejectsClientOutsideTenantAllowlist() throws Exception {
        MediaScreenProperties properties = allowedProperties("10.0.0.0/24");
        MockHttpServletResponse response = invoke(properties, request("GET", "10.0.1.8", "1"), new AtomicBoolean());

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("当前客户端无权访问该租户的媒体大屏"));
    }

    @Test void allowsConfiguredTenantAndClearsTenantContext() throws Exception {
        MediaScreenProperties properties = allowedProperties("10.0.0.0/24");
        AtomicBoolean invoked = new AtomicBoolean();

        MockHttpServletResponse response = invoke(properties, request("GET", "10.0.0.8", "1"), invoked);

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test void trustsForwardedAddressOnlyFromConfiguredProxy() throws Exception {
        MediaScreenProperties properties = allowedProperties("10.20.0.0/16");
        properties.setTrustedProxies(List.of("127.0.0.1"));
        MockHttpServletRequest request = request("GET", "127.0.0.1", "1");
        request.addHeader("X-Forwarded-For", "10.20.3.9, 127.0.0.1");
        AtomicBoolean invoked = new AtomicBoolean();

        invoke(properties, request, invoked);

        assertTrue(invoked.get());
    }

    @Test void ignoresForwardedAddressFromUntrustedClient() throws Exception {
        MediaScreenProperties properties = allowedProperties("10.20.0.0/16");
        MockHttpServletRequest request = request("GET", "10.30.0.8", "1");
        request.addHeader("X-Forwarded-For", "10.20.3.9");

        assertEquals(403, invoke(properties, request, new AtomicBoolean()).getStatus());
    }

    private static MediaScreenProperties allowedProperties(String cidr) {
        MediaScreenProperties.Client client = new MediaScreenProperties.Client();
        client.setTenantId(1L);
        client.setCidrs(List.of(cidr));
        MediaScreenProperties properties = new MediaScreenProperties();
        properties.setEnabled(true);
        properties.setClients(List.of(client));
        return properties;
    }

    private static MockHttpServletRequest request(String method, String remoteAddress, String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/public-api/zsjos/media-screen/stats");
        request.setRemoteAddr(remoteAddress);
        if (tenantId != null) request.addParameter("tenantId", tenantId);
        return request;
    }

    private static MockHttpServletResponse invoke(MediaScreenProperties properties, MockHttpServletRequest request,
                                                   AtomicBoolean invoked) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new MediaScreenAccessFilter(properties).doFilter(request, response, (req, res) -> {
            assertEquals(1L, TenantContextHolder.getTenantId());
            invoked.set(true);
        });
        return response;
    }
}
