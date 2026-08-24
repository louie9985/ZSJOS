package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedFilterVisibleUserServiceTest {

    @InjectMocks private AdvancedFilterVisibleUserService service;
    @Mock private LeadObjectPermissionService leadObjectPermissionService;
    @Mock private ServiceRelationMapper serviceRelationMapper;
    @Mock private AdminUserApi adminUserApi;

    @Test
    void leadSceneUsesLeadHierarchyAndExcludesDisabledUsers() {
        when(leadObjectPermissionService.hasQueryAll()).thenReturn(false);
        when(leadObjectPermissionService.getRelatedAndManagedUserIds(7L)).thenReturn(Set.of(7L, 8L));
        when(adminUserApi.getUserList(Set.of(7L, 8L))).thenReturn(List.of(
                user(7L, "周一", 0), user(8L, "周二", 1)));

        var result = service.resolve("lead", 7L);

        assertTrue(result.supported());
        assertEquals(List.of("7"), result.options().stream().map(option -> option.value()).toList());
    }

    @Test
    void leadQueryAllUsesAllEnabledUsers() {
        when(leadObjectPermissionService.hasQueryAll()).thenReturn(true);
        when(adminUserApi.getUserListByStatus(0)).thenReturn(List.of(user(9L, "赵六", 0)));

        var result = service.resolve("lead", 7L);

        assertEquals(List.of("9"), result.options().stream().map(option -> option.value()).toList());
    }

    @Test
    void subordinateSceneUsesManagedUsersWithoutCurrentUserFallback() {
        when(leadObjectPermissionService.getManagedUserIds(7L)).thenReturn(Set.of());

        var result = service.resolve("subordinate_sales", 7L);

        assertTrue(result.supported());
        assertTrue(result.options().isEmpty());
        verifyNoInteractions(adminUserApi);
    }

    @Test
    void studentSceneReturnsCurrentEnabledOwnerOnlyForActiveServiceRelation() {
        when(serviceRelationMapper.existsActiveByOwner(7L)).thenReturn(true);
        when(adminUserApi.getUserList(Set.of(7L))).thenReturn(List.of(user(7L, "钱七", 0)));

        var result = service.resolve("student", 7L);

        assertEquals(List.of("7"), result.options().stream().map(option -> option.value()).toList());
    }

    @Test
    void studentSceneExcludesDisabledCurrentOwner() {
        when(serviceRelationMapper.existsActiveByOwner(7L)).thenReturn(true);
        when(adminUserApi.getUserList(Set.of(7L))).thenReturn(List.of(user(7L, "钱七", 1)));

        var result = service.resolve("student", 7L);

        assertTrue(result.supported());
        assertTrue(result.options().isEmpty());
    }

    @Test
    void unsupportedSceneDoesNotResolveAnyPersonnel() {
        var result = service.resolve("order", 7L);

        assertFalse(result.supported());
        assertTrue(result.options().isEmpty());
        verifyNoInteractions(leadObjectPermissionService, serviceRelationMapper, adminUserApi);
    }

    private static AdminUserRespDTO user(Long id, String nickname, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        user.setStatus(status);
        return user;
    }
}
