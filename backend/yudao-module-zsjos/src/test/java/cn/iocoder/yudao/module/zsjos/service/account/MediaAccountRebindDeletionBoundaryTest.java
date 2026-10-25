package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_STATE_INVALID;

@ExtendWith(MockitoExtension.class)
class MediaAccountRebindDeletionBoundaryTest {
    @InjectMocks MediaAccountService service;
    @Mock MediaAccountMapper mapper;
    @Mock MediaAccountStudentLinkMapper linkMapper;
    @Mock MediaWorkflowEventService workflowEventService;
    @BeforeEach void tenant(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    MediaAccountDO pending(String status){
        var account=new MediaAccountDO().setId(1L).setVersion(4).setRunStatus(status)
                .setRebindStatus("pending").setRebindProcessInstanceId("process")
                .setRebindTargetStudentPersonId(22L).setRebindReviewerUserId(9L);
        when(mapper.selectByRebindProcessInstanceId("process")).thenReturn(account);
        when(mapper.selectByIdForUpdate(1L,1L)).thenReturn(account);
        return account;
    }
    @Test void lateApprovalCannotAddLinksOrEmitSuccessForDeletedAccount(){
        var account=pending("deleted");
        for(String state:java.util.List.of("deleted","delete_pending")){
            account.setRunStatus(state);
            assertEquals(MEDIA_ACCOUNT_STATE_INVALID.getCode(),assertThrows(ServiceException.class,
                    ()->service.handleRebindProcessResult("process",BpmProcessInstanceStatusEnum.APPROVE.getStatus(),"ok")).getCode());
        }
        verifyNoInteractions(linkMapper,workflowEventService);
        verify(mapper,never()).completeRebind(anyLong(),anyInt(),anyString(),anyString(),anyString(),any());
    }
    @Test void activeExactRoundStillBindsAndRepeatedResultDoesNothing(){
        var account=pending("active");
        when(mapper.completeRebind(1L,4,"process","approved","ok",22L)).thenReturn(1);
        service.handleRebindProcessResult("process",BpmProcessInstanceStatusEnum.APPROVE.getStatus(),"ok");
        verify(linkMapper).insert(any(MediaAccountStudentLinkDO.class));
        account.setRebindStatus("approved");
        service.handleRebindProcessResult("process",BpmProcessInstanceStatusEnum.APPROVE.getStatus(),"ok");
        verify(linkMapper,times(1)).insert(any(MediaAccountStudentLinkDO.class));
    }
}
