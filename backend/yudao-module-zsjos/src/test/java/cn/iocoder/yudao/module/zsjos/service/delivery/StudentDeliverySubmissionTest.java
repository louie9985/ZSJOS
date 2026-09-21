package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliverySubmissionReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentDeliverySubmissionTest {
    @org.mockito.Mock private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
    @InjectMocks StudentDeliverySubmissionServiceImpl service;
    @Mock StudentDeliverySubmissionMapper submissionMapper;
    @Mock StudentDeliveryStageMapper stageMapper;
    @Mock StudentDeliveryPlanMapper plans;
    @Mock MediaAccountMapper accounts;
    @Mock DeliveryPositioningSource sources;
    @Mock StudentDeliveryPlanService planService;
    @Mock BusinessTaskCommandService taskService;
    StudentDeliveryStageDO stage;
    @BeforeEach void init() {
        TenantContextHolder.setTenantId(991L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setUserType(2),new MockHttpServletRequest());
        stage=new StudentDeliveryStageDO().setId(1L).setAccountId(2L).setPlanId(3L).setDirectorUserId(10L).setStageCode("S0").setStatus("PENDING").setVersion(4);
        when(stageMapper.selectByIdForUpdate(1L,991L)).thenReturn(stage);
    }
    @AfterEach void clear(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    StudentDeliverySubmissionReqVO req(){return new StudentDeliverySubmissionReqVO().setStageId(1L).setVersion(4).setIdempotencyKey("submission-1").setSubmittedBy(999L).setFieldValuesJson("{\"deliveryCompleted\":\"是\",\"diagnosis\":\"交付完成\"}").setAttachmentSnapshotJson("forged");}
    void active(){when(plans.selectById(3L)).thenReturn(new StudentDeliveryPlanDO().setStatus("ACTIVE"));}
    @Test void freezesServerAgreementAndUsesLoginIdentity(){
        active();when(sources.snapshot(null,"S0")).thenReturn(Map.of("agreement","服务端约定","submissionId",8L));
        var result=service.submit(req());assertEquals(10L,result.getSubmittedBy());assertTrue(result.getAttachmentSnapshotJson().contains("服务端约定"));assertFalse(result.getAttachmentSnapshotJson().contains("forged"));
        assertEquals("COMPLETED",stage.getStatus());verify(planService).createNextStages(eq(3L),eq(2L),eq(10L),eq("S0"),any());
    }
    @Test void emptyAgreementCannotComplete(){active();when(sources.snapshot(null,"S0")).thenReturn(Map.of());assertThrows(RuntimeException.class,()->service.submit(req()));verify(submissionMapper,never()).insert(any(StudentDeliverySubmissionDO.class));}
    @Test void unfinishedDeliveryCannotBeSubmittedAsCompleted(){active();assertThrows(RuntimeException.class,()->service.submit(req().setFieldValuesJson("{\"deliveryCompleted\":\"否\",\"diagnosis\":\"未完成\"}")));verifyNoInteractions(sources,taskService);}
    @Test void retryDoesNotAdvanceStageAgain(){var previous=new StudentDeliverySubmissionDO().setIdempotencyKey("submission-1").setFieldValuesJson(req().getFieldValuesJson());when(submissionMapper.selectOne(any(Wrapper.class))).thenReturn(previous);assertSame(previous,service.submit(req()));verifyNoInteractions(plans,taskService,planService);}
    @Test void staleVersionOrAnotherDirectorCannotWrite(){active();assertThrows(RuntimeException.class,()->service.submit(req().setVersion(3)));stage.setDirectorUserId(11L);assertThrows(RuntimeException.class,()->service.submit(req()));verify(submissionMapper,never()).insert(any(StudentDeliverySubmissionDO.class));}
}
