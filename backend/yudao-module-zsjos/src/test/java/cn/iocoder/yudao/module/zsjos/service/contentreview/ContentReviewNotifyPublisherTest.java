package cn.iocoder.yudao.module.zsjos.service.contentreview;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmPendingTaskRespDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class ContentReviewNotifyPublisherTest {
    @InjectMocks ContentReviewNotifyPublisher publisher;
    @Mock ContentReviewBatchMapper batchMapper;
    @Mock ContentReviewBatchItemMapper itemMapper;
    @Mock ContentReviewRelationMapper relationMapper;
    @Mock BpmProcessTaskApi processTaskApi;
    @Mock NotifyBusinessEventApi notifyBusinessEventApi;
    @BeforeEach void tenant(){TenantContextHolder.setTenantId(7L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    ContentReviewBatchDO batch(){var b=new ContentReviewBatchDO();b.setId(1L);b.setBatchNo("CR-1");b.setOperatorUserId(2L);b.setProcessInstanceId("p");b.setStatus("FINAL_REVIEW");b.setContextSnapshotJson("{\"finalTaskKey\":\"customFinal\"}");return b;}
    @Test void finalNotificationUsesAllActualBpmAssigneesAndConfiguredTaskKey(){
        when(batchMapper.selectById(1L)).thenReturn(batch());
        when(processTaskApi.getPendingTasks("p")).thenReturn(List.of(
          new BpmPendingTaskRespDTO("a","customFinal",3L,LocalDateTime.now()),
          new BpmPendingTaskRespDTO("b","customFinal",4L,LocalDateTime.now()),
          new BpmPendingTaskRespDTO("c","other",5L,LocalDateTime.now())));
        publisher.publishDirectorCompleted(1L,9L,LocalDateTime.now());
        var captor=ArgumentCaptor.forClass(NotifyBusinessEvent.class);verify(notifyBusinessEventApi).publish(captor.capture());
        assertEquals(Set.of(3L,4L),captor.getValue().getPayload().get("finalReviewerUserIds"));
        assertEquals(7L,captor.getValue().getTenantId());
    }
    @Test void timeoutUsesRegisteredSceneRuleAndStableTaskIdentity(){
        var now=LocalDateTime.of(2026,9,20,12,0);
        var task=new BpmPendingTaskRespDTO("task","customFinal",3L,now.minusHours(25));
        var rule=new NotifyTimingRuleRespDTO(8L,"zsjos.content_review.review_timeout_reminder","overdue",1440);
        publisher.publishReviewTimeout(batch(),task,rule,now);
        var captor=ArgumentCaptor.forClass(NotifyBusinessEvent.class);verify(notifyBusinessEventApi).publish(captor.capture());
        var event=captor.getValue();assertEquals(rule.getSceneCode(),event.getSceneCode());assertEquals(8L,event.getTargetRuleId());
        assertEquals("content-review-timeout:task:8",event.getSourceEventKey());
        assertEquals(25L,event.getPayload().get("pendingHours"));
        assertEquals(Set.of(NotifyRecipientDTO.admin(3L)),new ContentReviewNotifySceneProvider().resolveRecipients(event,Set.of("final_reviewer")));
    }
}
