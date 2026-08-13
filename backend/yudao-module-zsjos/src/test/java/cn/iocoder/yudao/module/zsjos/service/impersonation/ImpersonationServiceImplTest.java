package cn.iocoder.yudao.module.zsjos.service.impersonation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.impersonation.ImpersonationSessionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.impersonation.ImpersonationRequestLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.impersonation.ImpersonationSessionMapper;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImpersonationServiceImplTest {
    @InjectMocks private ImpersonationServiceImpl service;
    @Mock private ImpersonationSessionMapper sessionMapper;
    @Mock private ImpersonationRequestLogMapper requestLogMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private BusinessAuditService auditService;

    @Test
    void endRejectsConcurrentTransition() {
        when(sessionMapper.selectActive(20L, 10L)).thenReturn(session());
        when(sessionMapper.close(eq(20L), eq(3), eq("ended"), any(), eq("done"))).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.end(10L, 20L, "done"));
        verifyNoInteractions(auditService);
    }

    @Test
    void idleExpiryCountsOnlySuccessfulConditionalTransitions() {
        when(sessionMapper.selectIdle(any())).thenReturn(List.of(session(), session().setId(21L)));
        when(sessionMapper.close(anyLong(), anyInt(), eq("expired"), any(), eq("idle_timeout")))
                .thenReturn(1, 0);

        assertEquals(1, service.expireIdleSessions());
    }

    @Test
    void readRejectsDisabledTargetBeforeTouchingSession() {
        when(sessionMapper.selectActive(20L, 10L)).thenReturn(session());
        when(adminUserApi.getUser(30L)).thenReturn(new AdminUserRespDTO().setId(30L).setStatus(1));

        assertThrows(ServiceException.class,
                () -> service.useReadSession(10L, 20L, "GET", "/admin-api/zsjos/lead/page"));
        verify(sessionMapper, never()).touch(anyLong(), anyInt(), any());
        verifyNoInteractions(requestLogMapper);
    }

    private static ImpersonationSessionDO session() {
        return new ImpersonationSessionDO().setId(20L).setAdministratorUserId(10L).setTargetUserId(30L)
                .setStatus("active").setVersion(3).setLastActiveAt(LocalDateTime.now());
    }
}
