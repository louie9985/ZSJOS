package cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedFilterTemplateControllerPermissionTest {

    private static final String VISIBLE_LIST = "visibleList";

    private PreAuthorize visibleListGuard() throws NoSuchMethodException {
        return AdvancedFilterTemplateController.class
                .getMethod(VISIBLE_LIST, String.class, String.class)
                .getAnnotation(PreAuthorize.class);
    }

    // 业务页取预置和取同一场景的字段目录是同一件事的前置条件，两者授权范围必须一致，
    // 否则会出现「页面能打开、筛选器能加条件，但预置标签永远为空」。
    @Test
    void visibleListGuardMatchesCatalogScenePermissions() throws NoSuchMethodException {
        String guard = visibleListGuard().value();
        assertTrue(guard.contains("'zsjos:sales-order:query-management'"),
                "订单管理页需要 query-management，缺失会让该页取预置直接 403");
        assertTrue(guard.contains("'zsjos:media-student:query-my'"));
        assertTrue(guard.contains("'zsjos:lead:query-all'"));
        assertTrue(guard.contains("'zsjos:lead-aging-pool:query'"));
    }

    // 守卫按 scene 分支，而不是一串静态权限的并集：预置接口的 scene 参数决定需要哪一页的权限。
    @Test
    void visibleListGuardIsSceneAware() throws NoSuchMethodException {
        String guard = visibleListGuard().value();
        assertEquals(7, guard.split("#scene").length - 1, "七个场景各需一条分支");
    }

    @Test
    void visibleListGuardCoversEverySupportedScene() throws NoSuchMethodException {
        String guard = visibleListGuard().value();
        List<String> scenes = List.of("lead", "order", "lead_appeal", "duplicate_review",
                "registration", "student", "subordinate_sales");
        for (String scene : scenes) {
            assertTrue(guard.contains("(#scene == '" + scene + "'"),
                    "场景 " + scene + " 缺少授权分支");
        }
    }
}
