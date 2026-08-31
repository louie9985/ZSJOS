package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_ACCEPTANCE;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_REJECTED;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @InjectMocks private ContentService service;
    @Mock private ContentMapper mapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ContentObjectPermissionProvider objectPermissionProvider;
    @Mock private MediaDataScopeService dataScopeService;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private MediaWorkflowEventService workflowEventService;

    @Test
    void rejectAcceptanceRequiresReason() {
        assertThrows(RuntimeException.class, () -> service.rejectAcceptance(2L, 3, "  "));
        verifyNoInteractions(mapper, workflowEventService);
    }

    @Test
    void rejectAcceptanceIncrementsRejectCountAndAuditsReason() {
        ContentDO content = new ContentDO().setId(2L).setContentNo("CT-2").setStatus(CONTENT_ACCEPTANCE)
                .setOwnerOperatorUserId(230L).setVersion(3);
        when(mapper.selectById(2L)).thenReturn(content);
        when(mapper.rejectTransition(2L, 3, CONTENT_ACCEPTANCE, CONTENT_REJECTED)).thenReturn(1);

        service.rejectAcceptance(2L, 3, "  画面需要补充字幕  ");

        verify(mapper).rejectTransition(2L, 3, CONTENT_ACCEPTANCE, CONTENT_REJECTED);
        verify(workflowEventService).transition("content", 2L, null, CONTENT_ACCEPTANCE, CONTENT_REJECTED,
                "画面需要补充字幕", "content:2:3:rejected");
        verify(workflowEventService).notify(eq("media.content.rejected"), eq("content"), eq(2L),
                eq(230L), isNull(), eq("content-result:2:3:rejected"), anyMap());
    }
}
