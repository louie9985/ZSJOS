package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionMemberDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamCollectionRecipientScopeTest {
    @InjectMocks private ExamCollectionRecipientScope scope;
    @Mock private AdminUserApi users;
    @Mock private PermissionApi permissions;
    @Mock private DeliveryClassMapper classes;
    @Mock private DeliveryClassObjectPermissionProvider classPermissions;

    @Test void requiresConfiguredManagerCapability() {
        assertEquals(Set.of(), scope.managedClassIds(9L));
        verifyNoInteractions(classes, classPermissions);
    }

    @Test void includesOnlyReadableClassesForEnabledInboxRecipients() {
        var manager = new AdminUserRespDTO(); manager.setId(9L);
        var denied = new AdminUserRespDTO(); denied.setId(8L);
        when(users.getUserListByStatus(0)).thenReturn(List.of(manager, denied));
        when(permissions.hasAnyPermissions(9L, "zsjos:business-task:query")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L, DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        var first = new DeliveryClassDO(); first.setId(1L);
        var second = new DeliveryClassDO(); second.setId(2L);
        when(classes.selectList()).thenReturn(List.of(first, second));
        when(classPermissions.hasPermission(1L, "read", 9L)).thenReturn(true);
        var visible = new ExamCollectionMemberDO(); visible.setClassId(1L);
        var hidden = new ExamCollectionMemberDO(); hidden.setClassId(2L);
        assertEquals(Map.of(9L, List.of(visible)), scope.managers(List.of(visible, hidden)));
    }
}
