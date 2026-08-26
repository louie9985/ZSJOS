package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import cn.iocoder.yudao.module.system.service.workbenchlayout.WorkbenchLayoutService;
import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchMenuProjection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void permissionInfoFallsBackToAuthorizedMenus() {
        WorkbenchLayoutService layoutService = mock(WorkbenchLayoutService.class);
        WorkbenchMenuProjection.Meta meta = WorkbenchMenuProjection.Meta.builder()
                .fallback(true).fallbackReason("GLOBAL_LAYOUT_NOT_PUBLISHED").build();
        when(layoutService.getProjection(Set.of(1L), List.of())).thenReturn(
                WorkbenchMenuProjection.builder().meta(meta).build());
        AuthPermissionInfoRespVO permissionInfo = new AuthPermissionInfoRespVO();
        List<AuthPermissionInfoRespVO.MenuVO> authorizedMenus = List.of(
                AuthPermissionInfoRespVO.MenuVO.builder().id(10L).name("原授权菜单").build());
        permissionInfo.setMenus(authorizedMenus);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "workbenchLayoutService", layoutService);

        AuthPermissionInfoRespVO result = ReflectionTestUtils.invokeMethod(controller,
                "withWorkbenchLayout", permissionInfo, Set.of(1L), List.of());

        assertSame(authorizedMenus, result.getWorkbenchMenus());
        assertSame(meta, result.getWorkbenchLayoutMeta());
    }

    @Test
    void permissionInfoUsesPublishedProjectionWithoutChangingAuthorizedMenus() {
        WorkbenchLayoutService layoutService = mock(WorkbenchLayoutService.class);
        WorkbenchMenuProjection.Meta meta = WorkbenchMenuProjection.Meta.builder()
                .globalVersionId(20L).globalVersionNo(2).fallback(false).build();
        List<AuthPermissionInfoRespVO.MenuVO> projectedMenus = List.of(
                AuthPermissionInfoRespVO.MenuVO.builder().id(-1L).name("员工导航分组").build());
        when(layoutService.getProjection(Set.of(1L), List.of())).thenReturn(
                WorkbenchMenuProjection.builder().menus(projectedMenus).meta(meta).build());
        AuthPermissionInfoRespVO permissionInfo = new AuthPermissionInfoRespVO();
        List<AuthPermissionInfoRespVO.MenuVO> authorizedMenus = List.of(
                AuthPermissionInfoRespVO.MenuVO.builder().id(10L).name("原授权菜单").build());
        permissionInfo.setMenus(authorizedMenus);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "workbenchLayoutService", layoutService);

        AuthPermissionInfoRespVO result = ReflectionTestUtils.invokeMethod(controller,
                "withWorkbenchLayout", permissionInfo, Set.of(1L), List.of());

        assertSame(authorizedMenus, result.getMenus());
        assertSame(projectedMenus, result.getWorkbenchMenus());
        assertFalse(result.getWorkbenchLayoutMeta().getFallback());
    }
}
