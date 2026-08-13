package cn.iocoder.yudao.module.infra.controller.admin.config;

import cn.iocoder.yudao.module.infra.controller.admin.config.vo.DefaultUserAvatarUpdateReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigControllerPermissionTest {

    @Test
    void defaultUserAvatarReadRequiresConfigQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = ConfigController.class.getMethod("getDefaultUserAvatar")
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('infra:config:query')", authorization.value());
    }

    @Test
    void defaultUserAvatarUpdateRequiresConfigUpdatePermission() throws NoSuchMethodException {
        PreAuthorize authorization = ConfigController.class
                .getMethod("updateDefaultUserAvatar", DefaultUserAvatarUpdateReqVO.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('infra:config:update')", authorization.value());
    }
}
