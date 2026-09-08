package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamScheduleObjectPermissionProviderTest {
    @InjectMocks private ExamScheduleObjectPermissionProvider provider;
    @Mock private ExamScheduleMapper mapper;
    @Mock private PermissionApi permissionApi;

    @Test
    void permitsOnlySupportedActionsOnExistingSchedulesForManagers() {
        when(mapper.selectById(9L)).thenReturn(new ExamScheduleDO().setId(9L));
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);

        assertTrue(provider.hasPermission(9L, "update", 20L));
        assertTrue(provider.hasPermission(9L, "publish", 20L));
        assertTrue(provider.hasPermission(9L, "revoke", 20L));
        assertFalse(provider.hasPermission(9L, "delete", 20L));
    }

    @Test
    void rejectsMissingSchedulesAndUsersWithoutManagePermission() {
        when(mapper.selectById(9L)).thenReturn(new ExamScheduleDO().setId(9L));
        when(permissionApi.hasAnyPermissions(20L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(false);
        assertEquals(1_900_018_006,
                assertThrows(ServiceException.class, () -> provider.check(9L, "update", 20L)).getCode());

        when(mapper.selectById(10L)).thenReturn(null);
        assertFalse(provider.hasPermission(10L, "update", 20L));
    }
}
