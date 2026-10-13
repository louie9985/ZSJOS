package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BpmStarterSelectionPolicyTest {
    @InjectMocks BpmStarterSelectionPolicy policy;
    @Mock RoleApi roleApi;
    @Mock PermissionApi permissionApi;
    @Mock AdminUserApi adminUserApi;
    @Test void legacyUnrestrictedDoesNotReadRoleMembers() {
        assertNull(policy.resolve(null)); assertNull(policy.resolve(""));
        policy.validate(null,List.of(99L)); verifyNoInteractions(roleApi,permissionApi,adminUserApi);
    }
    @Test void malformedRoleScopeFailsClosed() {
        for (String raw : List.of("0","-1","1,","role:1","1,abc","999999999999999999999"))
            assertThrows(RuntimeException.class,()->policy.resolve(raw));
        verifyNoInteractions(roleApi,permissionApi,adminUserApi);
    }
    @Test void onlyEnabledRoleMembersMayBeSelected() {
        var role=new RoleRespDTO(); role.setId(7L);role.setStatus(0);
        when(roleApi.getRoleList(List.of(7L))).thenReturn(List.of(role));
        when(permissionApi.getUserRoleIdListByRoleIds(List.of(7L))).thenReturn(Set.of(2L,3L));
        var enabled=new AdminUserRespDTO();enabled.setId(2L);enabled.setStatus(0);
        var disabled=new AdminUserRespDTO();disabled.setId(3L);disabled.setStatus(1);
        when(adminUserApi.getUserList(Set.of(2L,3L))).thenReturn(List.of(enabled,disabled));
        assertEquals(List.of(2L),policy.resolve("7").getUserIds());
        assertDoesNotThrow(()->policy.validate("7",List.of(2L)));
        assertThrows(RuntimeException.class,()->policy.validate("7",List.of(3L)));
        assertThrows(RuntimeException.class,()->policy.validate("7",List.of(2L,99L)));
        role.setStatus(1);assertThrows(RuntimeException.class,()->policy.resolve("7"));
    }
    @Test void emptyRoleIsNotUnrestricted() {
        var role=new RoleRespDTO();role.setId(7L);role.setStatus(0);
        when(roleApi.getRoleList(List.of(7L))).thenReturn(List.of(role));
        when(permissionApi.getUserRoleIdListByRoleIds(List.of(7L))).thenReturn(Set.of());
        assertTrue(policy.resolve("7").getUserIds().isEmpty());
        assertThrows(RuntimeException.class,()->policy.validate("7",List.of(9L)));
    }
}
