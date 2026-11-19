package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceCopyMapper;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmProcessInstanceReadAccessTest {
    private static final String INSTANCE_ID = "instance";
    private final BpmProcessInstanceService instances = mock(BpmProcessInstanceService.class);
    private final BpmTaskService tasks = mock(BpmTaskService.class);
    private final BpmProcessInstanceCopyMapper copies = mock(BpmProcessInstanceCopyMapper.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final BpmCommentService comments = mock(BpmCommentService.class);
    private final HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
    private final BpmProcessInstanceReadAccess access = new BpmProcessInstanceReadAccess(instances, tasks, copies, permissions, comments);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(instances.getHistoricProcessInstance(INSTANCE_ID)).thenReturn(instance);
        when(instance.getTenantId()).thenReturn("1");
        when(instance.getStartUserId()).thenReturn("11");
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void permitsStarterAndAssignedParticipants() {
        HistoricTaskInstance task = mock(HistoricTaskInstance.class);
        when(task.getAssignee()).thenReturn("12");
        when(task.getOwner()).thenReturn("13");
        when(tasks.getTaskListByProcessInstanceId(INSTANCE_ID, true)).thenReturn(List.of(task));

        assertSame(instance, access.requireReadable(11L, INSTANCE_ID));
        assertSame(instance, access.requireReadable(12L, INSTANCE_ID));
        assertSame(instance, access.requireReadable(13L, INSTANCE_ID));
    }

    @Test
    void permitsCopiedRecipientAndConfiguredManager() {
        when(copies.selectCount(any())).thenReturn(1L);
        assertSame(instance, access.requireReadable(14L, INSTANCE_ID));

        reset(copies);
        when(permissions.hasAnyPermissions(15L, "bpm:process-instance:manager-query", "bpm:task:manager-query")).thenReturn(true);
        assertSame(instance, access.requireReadable(15L, INSTANCE_ID));
        verifyNoInteractions(copies);
    }

    @Test
    void permitsRecordedDelegateAndTransferActorsButRefusesGenericCommentAuthors() {
        Comment audit = mock(Comment.class);
        when(audit.getUserId()).thenReturn("14");
        when(comments.getCommentListByProcessInstanceId(INSTANCE_ID)).thenReturn(List.of(audit));
        for (BpmCommentTypeEnum type : List.of(BpmCommentTypeEnum.TRANSFER,
                BpmCommentTypeEnum.DELEGATE_START, BpmCommentTypeEnum.DELEGATE_END)) {
            when(audit.getType()).thenReturn(type.getType());
            assertSame(instance, access.requireReadable(14L, INSTANCE_ID));
        }

        when(audit.getType()).thenReturn(BpmCommentTypeEnum.COMMENT.getType());
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> access.requireReadable(14L, INSTANCE_ID)).getCode());
    }

    @Test
    void refusesUnrelatedViewerAndForeignTenant() {
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> access.requireReadable(99L, INSTANCE_ID)).getCode());

        when(instance.getTenantId()).thenReturn("2");
        assertEquals(PROCESS_INSTANCE_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> access.requireReadable(11L, INSTANCE_ID)).getCode());
    }
}
