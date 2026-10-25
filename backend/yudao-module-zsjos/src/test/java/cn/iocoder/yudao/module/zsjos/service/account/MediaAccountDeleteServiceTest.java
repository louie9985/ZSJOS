package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDeleteRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountDeleteRequestMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaAccountDeleteServiceTest {
    @InjectMocks private MediaAccountDeleteService service;
    @Mock private MediaAccountMapper mapper;
    @Mock private ServiceRelationMapper relations;
    @Mock private BpmProcessInstanceApi bpm;
    @Mock private AdminUserApi users;
    @Mock private PermissionApi permissions;
    @Mock private MediaWorkflowEventService events;
    @Mock private MediaAccountDeleteRequestMapper requestMapper;
    @Mock private BusinessTaskMapper taskMapper;
    @Mock private ProductionTicketMapper tickets;
    @Mock private ContentReviewBatchMapper reviewBatches;

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void persistedErrorBoundaryUsesMysqlCharacterLengthWithoutSplittingUnicode() {
        String summary = "错误😀".repeat(600);
        String bounded = MediaAccountDeleteRequestMapper.boundedError(summary);
        assertEquals(1000, bounded.codePointCount(0, bounded.length()));
        assertFalse(Character.isHighSurrogate(bounded.charAt(bounded.length() - 1)));
        assertNull(MediaAccountDeleteRequestMapper.boundedError(null));
    }

    private MediaAccountDO account() {
        var account = new MediaAccountDO().setId(1L).setAccountNo("MA-001").setVersion(3)
                .setStudentPersonId(40L).setCreateServiceRelationId(30L)
                .setOwnerOperatorUserId(900L).setDirectorUserId(901L).setRunStatus("active");
        account.setTenantId(1L);
        when(mapper.selectByIdForUpdate(1L, 1L)).thenReturn(account);
        return account;
    }

    private ServiceRelationDO relation() {
        var source = new ServiceRelationDO().setId(30L).setPersonId(40L)
                .setStatus("active").setAcceptanceStatus("accepted")
                .setOperatorUserId(10L).setContentDirectorUserId(20L);
        source.setTenantId(1L);
        when(relations.selectByIdForUpdate(30L, 1L)).thenReturn(source);
        return source;
    }

    private void reviewer() {
        var director = new AdminUserRespDTO(); director.setId(20L); director.setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(users.getUser(20L)).thenReturn(director);
        when(permissions.hasAnyPermissions(20L, "zsjos:media-account:delete-approve")).thenReturn(true);
    }

    @Test void operatorSubmitsToCurrentSourceDirectorAndEmptyAccountSnapshotWorks() {
        account(); relation(); reviewer();
        when(bpm.createProcessInstance(eq(10L), any())).thenReturn("process-1");
        when(mapper.claimDelete(1L, 3, "process-1", 10L, 20L, "误新增")).thenReturn(1);
        assertEquals("process-1", service.submit(1L, " 误新增 ", 10L));
        var request = ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(bpm).createProcessInstance(eq(10L), request.capture());
        assertEquals(20L, request.getValue().getVariables().get("assignee"));
        assertEquals(List.of(20L), request.getValue().getVariables().get("coll_userList"));
        assertEquals(List.of(20L), request.getValue().getStartUserSelectAssignees().get("review"));
        var snapshot = ArgumentCaptor.forClass(MediaAccountDeleteRequestDO.class);
        verify(requestMapper).insert(snapshot.capture());
        assertEquals(20L, snapshot.getValue().getReviewerUserId());
        assertTrue(snapshot.getValue().getAccountSnapshotJson().contains("MA-001"));
    }

    @Test void directDirectorDeletesWithoutBpmOrApprovalPermissionAndCleansLinkedWork() {
        account(); relation();
        when(mapper.deleteDirectly(1L, 3, 20L, "误新增")).thenReturn(1);
        service.deleteDirectly(1L, " 误新增 ", 20L);
        verify(taskMapper).cancelByAccountId(eq(1L), any(), anyString());
        verify(tickets).excludeDeletedAccount(1L);
        verify(reviewBatches).excludeDeletedAccount(1L);
        verify(events).transition(anyString(), eq(1L), eq(20L), eq("active"), eq("deleted"), eq("误新增"), anyString());
        verifyNoInteractions(bpm, requestMapper, permissions, users);
    }

    @Test void operatorCannotUseDirectDelete() {
        account(); relation();
        assertEquals(MEDIA_ACCOUNT_PERMISSION_DENIED.getCode(), assertThrows(ServiceException.class,
                () -> service.deleteDirectly(1L, "原因", 10L)).getCode());
        verifyNoInteractions(bpm, requestMapper, taskMapper, tickets, reviewBatches);
        verify(mapper, never()).deleteDirectly(anyLong(), anyInt(), anyLong(), anyString());
    }

    @Test void staleSnapshotOwnersAndUnrelatedUsersCannotSubmitOrDelete() {
        account(); relation();
        for (long user : new long[]{900L, 901L, 999L}) {
            assertThrows(ServiceException.class, () -> service.submit(1L, "原因", user));
            assertThrows(ServiceException.class, () -> service.deleteDirectly(1L, "原因", user));
        }
        verifyNoInteractions(bpm, requestMapper, taskMapper);
    }

    @Test void directorUsesDirectPathEvenWhenAlsoOperator() {
        account(); relation().setOperatorUserId(20L);
        assertThrows(ServiceException.class, () -> service.submit(1L, "原因", 20L));
        when(mapper.deleteDirectly(1L, 3, 20L, "原因")).thenReturn(1);
        service.deleteDirectly(1L, "原因", 20L);
        verifyNoInteractions(bpm);
    }

    @Test void missingDisabledOrUnpermittedDirectorNeverFallsBackToSupervisor() {
        account(); var source = relation(); source.setContentDirectorUserId(null);
        assertEquals(MEDIA_ACCOUNT_DELETE_REVIEWER_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.submit(1L, "原因", 10L)).getCode());
        source.setContentDirectorUserId(20L);
        var director = new AdminUserRespDTO(); director.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(users.getUser(20L)).thenReturn(director);
        assertThrows(ServiceException.class, () -> service.submit(1L, "原因", 10L));
        director.setStatus(CommonStatusEnum.ENABLE.getStatus());
        assertEquals(MEDIA_ACCOUNT_DELETE_REVIEWER_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.submit(1L, "原因", 10L)).getCode());
        verifyNoInteractions(bpm, requestMapper);
    }

    @Test void brokenCrossTenantCrossPersonInactiveOrUnacceptedSourcesDenyCommands() {
        account(); var source = relation();
        source.setTenantId(2L);
        assertThrows(ServiceException.class, () -> service.deleteDirectly(1L, "原因", 20L));
        source.setTenantId(1L); source.setPersonId(41L);
        assertThrows(ServiceException.class, () -> service.submit(1L, "原因", 10L));
        source.setPersonId(40L); source.setStatus("paused");
        assertThrows(ServiceException.class, () -> service.deleteDirectly(1L, "原因", 20L));
        source.setStatus("active"); source.setAcceptanceStatus("pending");
        assertThrows(ServiceException.class, () -> service.submit(1L, "原因", 10L));
        when(relations.selectByIdForUpdate(30L, 1L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.deleteDirectly(1L, "原因", 20L));
        verifyNoInteractions(bpm, requestMapper, taskMapper);
    }

    @Test void pendingAndDeletedAccountsRejectBothPaths() {
        var account = account();
        for (String status : List.of("delete_pending", "deleted")) {
            account.setRunStatus(status);
            assertEquals(MEDIA_ACCOUNT_STATE_INVALID.getCode(), assertThrows(ServiceException.class,
                    () -> service.deleteDirectly(1L, "原因", 20L)).getCode());
            assertThrows(ServiceException.class, () -> service.submit(1L, "原因", 10L));
        }
        verifyNoInteractions(bpm, relations, taskMapper);
    }

    @Test void legacyDirectorRemainsExplicitOwner() {
        var account = account().setCreateServiceRelationId(null);
        when(mapper.deleteDirectly(1L, 3, 901L, "原因")).thenReturn(1);
        service.deleteDirectly(1L, "原因", 901L);
        verifyNoInteractions(relations, bpm);
    }

    @Test void versionConflictDoesNotCancelLinkedWork() {
        account(); relation();
        assertEquals(MEDIA_ACCOUNT_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.deleteDirectly(1L, "原因", 20L)).getCode());
        verifyNoInteractions(taskMapper, tickets, reviewBatches, events);
    }

    @Test void cleanupFailurePropagatesInsteadOfReportingDirectDeletionSuccess() {
        account(); relation(); when(mapper.deleteDirectly(1L, 3, 20L, "原因")).thenReturn(1);
        when(tickets.excludeDeletedAccount(1L)).thenThrow(new IllegalStateException("cleanup failed"));
        assertThrows(IllegalStateException.class, () -> service.deleteDirectly(1L, "原因", 20L));
        verifyNoInteractions(events, reviewBatches);
    }
    @Test void pendingRebindPreventsBothDeletionPathsWithoutChangingLinkedWork() {
        var account=account();
        for(String state:List.of("pending","starting")) {
            account.setRebindStatus(state);
            assertEquals(MEDIA_ACCOUNT_STATE_INVALID.getCode(),assertThrows(ServiceException.class,
                    () -> service.deleteDirectly(1L,"原因",20L)).getCode());
            assertThrows(ServiceException.class,() -> service.submit(1L,"原因",10L));
        }
        verifyNoInteractions(relations,bpm,taskMapper,tickets,reviewBatches,events);
    }

}
