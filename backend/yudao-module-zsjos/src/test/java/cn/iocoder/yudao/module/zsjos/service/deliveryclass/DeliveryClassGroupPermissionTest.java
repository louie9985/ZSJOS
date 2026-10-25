package cn.iocoder.yudao.module.zsjos.service.deliveryclass;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeliveryClassGroupPermissionTest {
    DeliveryClassGroupPermissionProvider provider;
    PermissionApi permissions; AdminUserApi users; DeliveryClassScopeService scope;
    DeliveryClassObjectPermissionProvider classAuth; DeliveryClassMapper classes;
    DeliveryClassGroupMapper groups; DeliveryClassGroupDO group;
    @BeforeEach void setup(){
        provider=new DeliveryClassGroupPermissionProvider();permissions=mock(PermissionApi.class);users=mock(AdminUserApi.class);
        scope=mock(DeliveryClassScopeService.class);classAuth=mock(DeliveryClassObjectPermissionProvider.class);classes=mock(DeliveryClassMapper.class);groups=mock(DeliveryClassGroupMapper.class);
        Object[][] fields={{"mapper",groups},{"permissions",permissions},{"users",users},{"scope",scope},{"classes",classes},{"classAuthorization",classAuth}};
        for(var f:fields)ReflectionTestUtils.setField(provider,(String)f[0],f[1]);
        group=new DeliveryClassGroupDO();group.setId(1L);group.setDeptId(80L);when(groups.selectById(1L)).thenReturn(group);
        var user=new AdminUserRespDTO();user.setId(9L);user.setDeptId(80L);when(users.getUser(9L)).thenReturn(user);
        when(scope.resolve(eq(9L),any(AdminUserRespDTO.class))).thenReturn(new DeliveryClassScopeService.Scope(false,java.util.Set.of(80L)));
    }
    @Test void visibilityDoesNotGrantMaintenance(){
        when(permissions.hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(false);
        assertFalse(provider.canMaintain(group,9L,"update"));
    }
    @Test void managerAndButtonAndDepartmentAreCumulative(){
        when(permissions.hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(true);
        assertTrue(provider.canMaintain(group,9L,"create"));assertTrue(provider.canMaintain(group,9L,"update"));
        group.setDeptId(90L);assertFalse(provider.canMaintain(group,9L,"update"));
        group.setDeptId(80L);when(scope.resolve(eq(9L),any(AdminUserRespDTO.class))).thenReturn(new DeliveryClassScopeService.Scope(false,java.util.Set.of()));assertFalse(provider.canMaintain(group,9L,"delete"));
    }
    @Test void hiddenGroupCannotBeReadMerelyByGuessingItsId(){
        when(classes.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        assertFalse(provider.hasPermission(1L,"read",9L));assertFalse(provider.hasPermission(999L,"read",9L));
    }
    @Test void visibleChildAllowsGroupReadWithoutGrantingSiblingWrites(){
        var row=new DeliveryClassDO().setId(7L);
        when(classes.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row));
        when(classAuth.hasPermission(7L,"read",9L)).thenReturn(true);
        assertTrue(provider.hasPermission(1L,"read",9L));assertFalse(provider.hasPermission(1L,"update",9L));
    }
    @Test void oneMaintenanceSnapshotServesAllGroupActionsWithoutRepeatedLookups(){
        when(permissions.hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(true);
        var context=provider.maintenanceContext(9L);
        for(int i=0;i<30;i++){assertTrue(context.can(group,"create"));assertTrue(context.can(group,"update"));assertTrue(context.can(group,"delete"));}
        verify(users,times(1)).getUser(9L);verify(scope,times(1)).resolve(eq(9L),any(AdminUserRespDTO.class));
    }

}
