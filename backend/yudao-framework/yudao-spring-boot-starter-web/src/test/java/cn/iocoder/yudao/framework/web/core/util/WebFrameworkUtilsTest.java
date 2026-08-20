package cn.iocoder.yudao.framework.web.core.util;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.filter.ApiRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebFrameworkUtilsTest {

    @BeforeEach
    void setUp() {
        WebProperties properties = new WebProperties();
        new WebFrameworkUtils(properties);
    }

    @Test
    void resolvesIndependentPartnerApiAsPartner() {
        assertEquals(UserTypeEnum.PARTNER.getValue(), userType("/part-api/zsjos/partner/me"));
        assertEquals(UserTypeEnum.MEMBER.getValue(), userType("/app-api/system/dict-data/type"));
        assertEquals(UserTypeEnum.MEMBER.getValue(), userType("/app-api/zsjos/partner/me"));
        assertEquals(UserTypeEnum.ADMIN.getValue(), userType("/admin-api/system/auth/login"));
    }

    @Test
    void rejectsNullConfiguredUserTypeInsteadOfFallingBack() {
        WebProperties properties = new WebProperties();
        Map<String, Integer> mappings = new LinkedHashMap<>();
        mappings.put("/zsjos/", null);
        properties.setAppApiUserTypePrefixes(mappings);
        new WebFrameworkUtils(properties);

        assertFalse(properties.isAppApiUserTypePrefixesValid());
        assertThrows(IllegalStateException.class, () -> userType("/app-api/zsjos/partner/me"));
    }

    @Test
    void includesPartnerApiInApiFilters() {
        TestApiRequestFilter filter = new TestApiRequestFilter(new WebProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/part-api/zsjos/auth/login");

        assertFalse(filter.shouldSkip(request));
    }

    private Integer userType(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(path);
        return WebFrameworkUtils.getLoginUserType(request);
    }

    private static final class TestApiRequestFilter extends ApiRequestFilter {

        private TestApiRequestFilter(WebProperties webProperties) {
            super(webProperties);
        }

        private boolean shouldSkip(MockHttpServletRequest request) {
            return shouldNotFilter(request);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            chain.doFilter(request, response);
        }
    }
}
