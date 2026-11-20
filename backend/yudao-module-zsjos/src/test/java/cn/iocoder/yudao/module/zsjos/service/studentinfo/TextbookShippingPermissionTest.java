package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TextbookShippingPermissionTest {
    @Mock PermissionApi permissions;
    @Mock SalesOrderMapper orders;
    @InjectMocks TextbookShippingPermission scope;

    @Test void ownershipMeansFormalSalesAndAllDoesNotGrantEditing() {
        var order = new SalesOrderDO();
        order.setStatus("effective");
        order.setFormalSalesUserId(8L);
        order.setSubmitterUserId(9L);
        assertTrue(scope.visible(order, 8L));
        assertFalse(scope.visible(order, 9L));
        order.setFormalSalesUserId(null);
        assertFalse(scope.visible(order, 9L));
        when(permissions.hasAnyPermissions(9L, "zsjos:textbook-shipping:view-all")).thenReturn(true);
        assertTrue(scope.visible(order, 9L));
        when(permissions.hasAnyPermissions(9L, "zsjos:textbook-shipping:query")).thenReturn(true);
        assertFalse(scope.hasPermission(11L,"update",9L));
        when(permissions.hasAnyPermissions(9L, "zsjos:textbook-shipping:update")).thenReturn(true);
        when(orders.selectById(11L)).thenReturn(order);
        assertTrue(scope.hasPermission(11L,"update",9L));
        order.setStatus("terminated");
        assertFalse(scope.hasPermission(11L,"update",9L));
    }
}
