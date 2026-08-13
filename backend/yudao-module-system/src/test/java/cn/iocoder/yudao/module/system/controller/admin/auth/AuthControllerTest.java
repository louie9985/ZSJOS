package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void permissionInfoIncludesDefaultUserAvatar() {
        ConfigApi configApi = mock(ConfigApi.class);
        when(configApi.getDefaultUserAvatar()).thenReturn("https://example.com/default.png");
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "configApi", configApi);

        AuthPermissionInfoRespVO result = controller.withDefaultAvatar(new AuthPermissionInfoRespVO());

        assertEquals("https://example.com/default.png", result.getDefaultAvatar());
    }
}
