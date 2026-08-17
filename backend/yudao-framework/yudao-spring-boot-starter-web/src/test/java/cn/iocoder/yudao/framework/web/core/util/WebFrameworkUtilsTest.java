package cn.iocoder.yudao.framework.web.core.util;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebFrameworkUtilsTest {

    @BeforeEach
    void setUp() {
        WebProperties properties = new WebProperties();
        properties.setAppApiUserTypePrefixes(Map.of("/zsjos/", UserTypeEnum.PARTNER.getValue()));
        new WebFrameworkUtils(properties);
    }

    @Test
    void resolvesConfiguredAppSubtreeAsPartner() {
        assertEquals(UserTypeEnum.PARTNER.getValue(), userType("/app-api/zsjos/partner/me"));
        assertEquals(UserTypeEnum.MEMBER.getValue(), userType("/app-api/system/dict-data/type"));
        assertEquals(UserTypeEnum.MEMBER.getValue(), userType("/app-api/zsjos-other/profile"));
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

    private Integer userType(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(path);
        return WebFrameworkUtils.getLoginUserType(request);
    }
}
