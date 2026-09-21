package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {
    @org.mockito.Mock private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;

    @InjectMocks private ContentService service;
    @Mock private ContentMapper mapper;
    @Mock private ContentVersionMapper contentVersionMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ContentObjectPermissionProvider objectPermissionProvider;
    @Mock private MediaDataScopeService dataScopeService;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private MediaWorkflowEventService workflowEventService;

    @Test
    void legacyAcceptanceActionsAreDisabled() {
        assertServiceCode(CONTENT_REVIEW_LEGACY_ENTRY_DISABLED,
                () -> service.approveAcceptance(2L, 3));
        assertServiceCode(CONTENT_REVIEW_LEGACY_ENTRY_DISABLED,
                () -> service.rejectAcceptance(2L, 3, "退回"));
        verifyNoInteractions(mapper, workflowEventService);
    }

    @Test
    void submitAcceptanceRejectsIncompletePackageBeforeTransition() {
        ContentDO content = new ContentDO().setId(2L).setContentNo("CT-2").setStatus(CONTENT_IN_PRODUCTION)
                .setCurrentVersionNo(1).setVersion(3);
        when(mapper.selectById(2L)).thenReturn(content);
        when(contentVersionMapper.selectByContentAndVersionNo(2L, 1))
                .thenReturn(new ContentVersionDO().setId(20L).setVersionNo(1));

        assertServiceCode(CONTENT_REVIEW_PACKAGE_INCOMPLETE,
                () -> service.submitAcceptance(2L, 3));

        verify(mapper, never()).transition(anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void startRevisionAllowsRejectedFrozenVersion() {
        ContentDO content = new ContentDO().setId(2L).setContentNo("CT-2").setStatus(CONTENT_REJECTED)
                .setCurrentVersionNo(1).setOwnerOperatorUserId(230L).setVersion(3);
        ContentVersionDO rejected = new ContentVersionDO().setId(20L).setVersionNo(1)
                .setFrozenAt(LocalDateTime.of(2026, 9, 8, 9, 0)).setReviewDecision("rejected");
        when(mapper.selectById(2L)).thenReturn(content);
        when(contentVersionMapper.selectByContentAndVersionNo(2L, 1)).thenReturn(rejected);
        when(mapper.transition(2L, 3, CONTENT_REJECTED, CONTENT_REVISING)).thenReturn(1);

        service.startRevision(2L, 3);

        verify(mapper).transition(2L, 3, CONTENT_REJECTED, CONTENT_REVISING);
    }

    @Test
    void startRevisionRejectsFrozenVersionWithoutRejectedDecision() {
        ContentDO content = new ContentDO().setId(2L).setStatus(CONTENT_REJECTED)
                .setCurrentVersionNo(1).setVersion(3);
        when(mapper.selectById(2L)).thenReturn(content);
        when(contentVersionMapper.selectByContentAndVersionNo(2L, 1)).thenReturn(
                new ContentVersionDO().setId(20L).setFrozenAt(LocalDateTime.of(2026, 9, 8, 9, 0)));

        assertServiceCode(CONTENT_STATE_INVALID, () -> service.startRevision(2L, 3));

        verify(mapper, never()).transition(anyLong(), anyInt(), anyString(), anyString());
    }

    private void assertServiceCode(cn.iocoder.yudao.framework.common.exception.ErrorCode expected,
                                   org.junit.jupiter.api.function.Executable executable) {
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, executable);
        assertEquals(expected.getCode(), error.getCode());
    }
}
