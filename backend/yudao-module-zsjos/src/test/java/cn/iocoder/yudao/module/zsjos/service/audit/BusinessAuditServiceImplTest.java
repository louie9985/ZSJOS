package cn.iocoder.yudao.module.zsjos.service.audit;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.audit.BusinessAuditLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.audit.BusinessAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAuditOperation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessAuditServiceImplTest {
    @InjectMocks private BusinessAuditServiceImpl service;
    @Mock private BusinessAuditLogMapper mapper;

    @Test
    void beginsAndCompletesAttemptWithoutRequestPayload() {
        doAnswer(invocation -> {
            invocation.<BusinessAuditLogDO>getArgument(0).setId(99L);
            return 1;
        }).when(mapper).insert(any(BusinessAuditLogDO.class));
        when(mapper.updateById((BusinessAuditLogDO) any())).thenReturn(1);

        Long id = service.begin(new ZsjosAuditOperation("business", "lead.create", "lead", null,
                "HTTP", "POST", "/admin-api/zsjos/lead/create"));
        service.complete(id, false, 1900001001, "token=secret validation failed", 12);

        ArgumentCaptor<BusinessAuditLogDO> captor = ArgumentCaptor.forClass(BusinessAuditLogDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("STARTED", captor.getValue().getResultStatus());
        assertEquals(0L, captor.getValue().getTenantId());
        assertEquals("{}", captor.getValue().getDetailJson());
        ArgumentCaptor<BusinessAuditLogDO> updateCaptor = ArgumentCaptor.forClass(BusinessAuditLogDO.class);
        verify(mapper).updateById((BusinessAuditLogDO) updateCaptor.capture());
        assertEquals("FAILURE", updateCaptor.getValue().getResultStatus());
        assertTrue(updateCaptor.getValue().getResultMessage().contains("[REDACTED]"));
        assertEquals(12L, updateCaptor.getValue().getDurationMs());
    }

    @Test
    void recordsRegisteredActionWithoutSensitivePayload() {
        service.record("export", "export.create", "export_task", "10", "finance", Map.of("type", "order"));

        ArgumentCaptor<BusinessAuditLogDO> captor = ArgumentCaptor.forClass(BusinessAuditLogDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("export.create", captor.getValue().getActionCode());
        assertTrue(captor.getValue().getDetailJson().contains("order"));
    }

    @Test
    void rejectsUnregisteredAndSensitiveDetails() {
        assertThrows(ServiceException.class,
                () -> service.record("export", "free text", "task", "1", "admin", Map.of()));
        assertThrows(ServiceException.class,
                () -> service.record("export", "export.create", "task", "1", "admin",
                        Map.of("mobileNumber", "sensitive")));
        verifyNoInteractions(mapper);
    }
}
