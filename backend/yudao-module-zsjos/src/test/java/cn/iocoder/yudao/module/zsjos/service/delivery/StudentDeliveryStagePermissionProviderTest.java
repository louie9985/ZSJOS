package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentDeliveryStagePermissionProviderTest {
    @InjectMocks private StudentDeliveryStagePermissionProvider provider;
    @Mock private StudentDeliveryStageMapper mapper;
    @Test void allowsOnlyTheStoredDirector() {
        when(mapper.selectById(1L)).thenReturn(new StudentDeliveryStageDO().setDirectorUserId(10L));
        assertTrue(provider.hasPermission(1L, "defer", 10L));
        assertFalse(provider.hasPermission(1L, "defer", 11L));
        assertThrows(RuntimeException.class, () -> provider.check(1L, "defer", 11L));
    }
    @Test void missingOrTenantFilteredStageIsDenied() {
        assertFalse(provider.hasPermission(1L, "defer", 10L));
    }
    @Test void anonymousAndUnsupportedActionsAreDeniedWithoutReading() {
        assertFalse(provider.hasPermission(1L, "defer", null));
        assertFalse(provider.hasPermission(1L, "edit", 10L));
        verifyNoInteractions(mapper);
    }
}
