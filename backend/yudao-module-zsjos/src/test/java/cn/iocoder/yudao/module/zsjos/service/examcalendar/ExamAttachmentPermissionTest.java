package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamAttachmentPermissionTest {
    @Mock ExamScheduleMapper mapper;
    @Mock PermissionApi permissionApi;
    @InjectMocks ExamScheduleObjectPermissionProvider provider;
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void fileVisibilityMatchesExamVisibilityAcrossTenantStatusAndPermissions() {
        TenantContextHolder.setTenantId(1L);
        var row = new ExamScheduleDO().setId(5L).setRecordStatus("PUBLISHED"); row.setTenantId(1L); row.setDeleted(false);
        when(mapper.selectById(5L)).thenReturn(row);
        assertFalse(provider.hasPermission(5L, "read-attachment", 7L));
        when(permissionApi.hasAnyPermissions(7L, "zsjos:exam-calendar:query")).thenReturn(true);
        assertTrue(provider.hasPermission(5L, "read-attachment", 7L));
        row.setRecordStatus("DRAFT"); assertFalse(provider.hasPermission(5L, "read-attachment", 7L));
        when(permissionApi.hasAnyPermissions(7L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true);
        assertTrue(provider.hasPermission(5L, "read-attachment", 7L));
        row.setRecordStatus("REVOKED").setRevokedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(6));
        assertFalse(provider.hasPermission(5L, "read-attachment", 7L));
        row.setRevokedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))); assertTrue(provider.hasPermission(5L, "read-attachment", 7L));
        row.setReeditClaimedAt(LocalDateTime.now()); assertFalse(provider.hasPermission(5L, "read-attachment", 7L));
        row.setRecordStatus("PUBLISHED"); row.setTenantId(2L); assertFalse(provider.hasPermission(5L, "read-attachment", 7L));
        row.setTenantId(1L); row.setDeleted(true); assertFalse(provider.hasPermission(5L, "read-attachment", 7L));
    }
}
