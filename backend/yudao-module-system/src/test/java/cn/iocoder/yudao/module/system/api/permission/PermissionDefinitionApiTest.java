package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionDefinitionApiTest {
    @InjectMocks PermissionApiImpl api;
    @Mock MenuService menus;

    @Test void rejectsBlankAndWildcardWithoutQuery() {
        assertFalse(api.isPermissionDefined(null));
        assertFalse(api.isPermissionDefined(" "));
        assertFalse(api.isPermissionDefined("*:*:*"));
        verifyNoInteractions(menus);
    }
    @Test void usesTenantAndEnabledHierarchyInsteadOfUserGrantOrUnfilteredCatalogue() {
        MenuDO enabled = new MenuDO(); enabled.setPermission("zsjos:lead:query-owned");
        MenuDO disabled = new MenuDO(); disabled.setPermission("zsjos:lead:query-all");
        var candidates = List.of(enabled, disabled);
        when(menus.getMenuListByTenant(any())).thenReturn(candidates);
        when(menus.filterDisableMenus(candidates)).thenReturn(List.of(enabled));
        assertTrue(api.isPermissionDefined(enabled.getPermission()));
        assertFalse(api.isPermissionDefined(disabled.getPermission()));
        assertFalse(api.isPermissionDefined("invented:permission"));
    }
}
