package cn.iocoder.yudao.module.zsjos.service.export;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.export.ExportTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.service.export.ExportTaskStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportTaskServiceImplTest {
    private final ExportTaskServiceImpl service = new ExportTaskServiceImpl();
    @Mock private ExportTaskMapper mapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private SecurityFrameworkService securityService;
    @Mock private FileApi fileApi;
    @Mock private BusinessAuditService auditService;
    @Mock private ExportTypeProvider provider;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
        ReflectionTestUtils.setField(service, "securityService", securityService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "providers", List.of(provider));
        lenient().when(provider.getType()).thenReturn("lead");
        lenient().when(provider.getCreatePermission()).thenReturn("zsjos:export:lead");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRejectsUnknownTypeAndMissingPermission() {
        assertThrows(ServiceException.class, () -> service.create(1L, "unknown", "{}"));

        when(securityService.hasPermission("zsjos:export:lead")).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.create(1L, "lead", "{}"));
        verifyNoInteractions(adminUserApi);
    }

    @Test
    void createSavesNormalizedFilterAndPermissionSnapshot() {
        when(securityService.hasPermission("zsjos:export:lead")).thenReturn(true);
        when(adminUserApi.getUser(1L)).thenReturn(new AdminUserRespDTO().setId(1L).setNickname("提交人"));
        doAnswer(invocation -> {
            invocation.<ExportTaskDO>getArgument(0).setId(10L);
            return 1;
        }).when(mapper).insert(any(ExportTaskDO.class));

        assertEquals(10L, service.create(1L, "lead", "{\"status\":\"valid\"}"));

        ArgumentCaptor<ExportTaskDO> captor = ArgumentCaptor.forClass(ExportTaskDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(QUEUED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getPermissionSnapshotJson().contains("zsjos:export:lead"));
    }

    @Test
    void cancelIsCreatorOnlyAndRejectsLostRace() {
        when(mapper.selectById(10L)).thenReturn(task().setCreatorUserId(2L));
        assertThrows(ServiceException.class, () -> service.cancel(1L, 10L));

        when(mapper.selectById(10L)).thenReturn(task().setCreatorUserId(1L));
        when(mapper.transition(eq(10L), eq(3), anyList(), any())).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.cancel(1L, 10L));
        verifyNoInteractions(auditService);
    }

    @Test
    void downloadRejectsImpersonationContext() {
        LoginUser user = new LoginUser().setId(1L).setInfo(new HashMap<>());
        user.setContext("zsjos.impersonation.sessionId", 99L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());

        assertThrows(ServiceException.class, () -> service.getDownloadUrl(1L, 10L));
        verifyNoInteractions(mapper, fileApi);
    }

    @Test
    void rowLimitUsesConditionalTerminalTransition() throws Exception {
        ExportTaskDO task = task().setAttemptCount(1);
        when(mapper.transition(eq(10L), eq(3), eq(List.of(PRECHECKING)), any())).thenReturn(1);
        when(provider.generate(any())).thenReturn(new ExportTypeProvider.ExportResult(
                new byte[]{1}, "lead.xlsx", ExportTaskServiceImpl.MAX_ROWS + 1L));

        service.processOne(task);

        ArgumentCaptor<ExportTaskDO> values = ArgumentCaptor.forClass(ExportTaskDO.class);
        verify(mapper).transition(eq(10L), eq(4), eq(List.of(GENERATING)), values.capture());
        assertEquals(FAILED, values.getValue().getStatus());
        assertEquals("ROW_LIMIT_EXCEEDED", values.getValue().getFailureCode());
        verifyNoInteractions(fileApi);
    }

    @Test
    void generationFailureRetriesThenFailsOnThirdAttempt() throws Exception {
        when(mapper.transition(anyLong(), anyInt(), anyList(), any())).thenReturn(1);
        when(provider.generate(any())).thenThrow(new IllegalStateException("temporary"));

        service.processOne(task().setAttemptCount(1));
        ArgumentCaptor<ExportTaskDO> retryValues = ArgumentCaptor.forClass(ExportTaskDO.class);
        verify(mapper).transition(eq(10L), eq(4), eq(List.of(GENERATING)), retryValues.capture());
        assertEquals(QUEUED, retryValues.getValue().getStatus());
        long firstDelay = java.time.Duration.between(LocalDateTime.now(), retryValues.getValue().getNextAttemptAt()).toSeconds();
        assertTrue(firstDelay >= 28 && firstDelay <= 30);

        clearInvocations(mapper);
        when(mapper.transition(anyLong(), anyInt(), anyList(), any())).thenReturn(1);
        service.processOne(task().setAttemptCount(3));
        ArgumentCaptor<ExportTaskDO> failedValues = ArgumentCaptor.forClass(ExportTaskDO.class);
        verify(mapper).transition(eq(10L), eq(4), eq(List.of(GENERATING)), failedValues.capture());
        assertEquals(FAILED, failedValues.getValue().getStatus());
        assertEquals("GENERATION_FAILED", failedValues.getValue().getFailureCode());
    }

    @Test
    void expiryAndRetentionAreDelegatedToConditionalMapperOperations() {
        when(mapper.selectReadyExpired(any())).thenReturn(List.of(task().setStatus(READY)));
        when(mapper.transition(eq(10L), eq(3), eq(List.of(READY)), any())).thenReturn(1);
        when(mapper.deleteInactiveTerminal(any())).thenReturn(2);

        assertEquals(1, service.expireFiles());
        assertEquals(2, service.cleanInactiveTasks());
        verify(mapper).deleteInactiveTerminal(argThat(value -> value.isBefore(LocalDateTime.now().minusDays(89))));
    }

    private static ExportTaskDO task() {
        return new ExportTaskDO().setId(10L).setTaskNo("EXP001").setExportType("lead")
                .setStatus(PRECHECKING).setCreatorUserId(1L).setCreatorRoleSnapshot("zsjos:export:lead")
                .setAttemptCount(1).setVersion(3).setLastActiveAt(LocalDateTime.now());
    }
}
