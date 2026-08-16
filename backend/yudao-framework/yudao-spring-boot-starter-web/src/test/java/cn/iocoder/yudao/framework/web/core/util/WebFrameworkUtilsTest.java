package cn.iocoder.yudao.framework.web.core.util;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebFrameworkUtilsTest {

    @BeforeEach
    void setUp() {
        WebProperties properties = new WebProperties();
        properties.setAppApiAdminPrefixes(List.of("/zsjos/"));
        new WebFrameworkUtils(properties);
    }

    @Test
    void resolvesOnlyConfiguredAppSubtreeAsAdmin() {
        assertEquals(UserTypeEnum.ADMIN.getValue(), userType("/app-api/zsjos/partner/me"));
        assertEquals(UserTypeEnum.MEMBER.getValue(), userType("/app-api/system/dict-data/type"));
        assertEquals(UserTypeEnum.MEMBER.getValue(), userType("/app-api/zsjos-other/profile"));
        assertEquals(UserTypeEnum.ADMIN.getValue(), userType("/admin-api/system/auth/login"));
    }

    private Integer userType(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(path);
        return WebFrameworkUtils.getLoginUserType(request);
    }
}
