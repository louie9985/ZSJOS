package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceObjectPermissionProviderTest {

    @InjectMocks private StudentServiceObjectPermissionProvider provider;
    @Mock private ServiceRelationMapper relationMapper;
    @Mock private PermissionApi permissionApi;

    @Test
    void deliveryStageRequiresActiveOwner() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(7L); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);

        assertTrue(provider.hasPermission(10L, "delivery-stage", 7L));
        assertFalse(provider.hasPermission(10L, "delivery-stage", 8L));

        relation.setStatus("completed");
        assertFalse(provider.hasPermission(10L, "delivery-stage", 7L));
    }

    @Test
    void assignedOperatorCanOnlyReadAcceptedServiceAcrossReadableStatuses() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(7L); relation.setOperatorUserId(9L);
        relation.setAcceptanceStatus("accepted"); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);

        for (String status : new String[]{"active", "paused", "completed"}) {
            relation.setStatus(status);
            assertTrue(provider.hasPermission(10L, "read", 9L));
            assertFalse(provider.hasPermission(10L, "contact", 9L));
        }

        relation.setStatus("active"); relation.setAcceptanceStatus("pending");
        assertFalse(provider.hasPermission(10L, "read", 9L));
        relation.setAcceptanceStatus("accepted"); relation.setOperatorUserId(10L);
        assertFalse(provider.hasPermission(10L, "read", 9L));
    }
}
