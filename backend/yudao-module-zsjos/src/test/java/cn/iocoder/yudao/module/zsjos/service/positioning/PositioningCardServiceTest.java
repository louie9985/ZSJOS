package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerStudentLinkDO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositioningCardServiceTest {
    @Mock private PositioningCardMapper mapper;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private PersonMapper personMapper;
    @Mock private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    @Mock private PostApi postApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PositioningCardObjectPermissionProvider objectPermissionProvider;
    @Mock private cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService dataScopeService;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper partnerStudentLinkMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper partnerAccountMapper;
    @Mock private MediaWorkflowEventService workflowEventService;
    @InjectMocks private PositioningCardService service;

    @Test
    void ordinarySubmitGoesToOperatorWithoutStartingBpm() {
        PositioningCardDO card = card(false, MediaWorkflowConstants.POSITIONING_CO_CREATING, 0);
        when(mapper.selectById(1L)).thenReturn(card);
        when(mapper.transition(1L, 0, MediaWorkflowConstants.POSITIONING_CO_CREATING,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY)).thenReturn(1);

        service.submitReview(1L, 0, 99L);

        verifyNoInteractions(processInstanceApi);
        verify(mapper).transition(1L, 0, MediaWorkflowConstants.POSITIONING_CO_CREATING,
                MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY);
    }

    @Test
    void createRequiresMatchingStudentAndDefaultsUnfilledJsonLayers() {
        when(accountMapper.selectById(10L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO()
                .setId(10L).setStudentPersonId(20L).setDirectorUserId(99L));
        when(personMapper.selectById(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO().setId(20L));
        doAnswer(invocation -> { invocation.<PositioningCardDO>getArgument(0).setId(7L); return 1; })
                .when(mapper).insert(any(PositioningCardDO.class));
        PositioningCardSaveReqVO req = new PositioningCardSaveReqVO();
        req.setAccountId(10L);
        req.setStudentPersonId(20L);
        req.setLayer1Json("{\"persona\":\"test\"}");

        service.create(req, 99L);

        verify(personMapper).selectById(20L);
        verify(mapper).insert(argThat((PositioningCardDO card) -> "{}".equals(card.getLayer2Json())
                && "{}".equals(card.getFormulaJson()) && "{}".equals(card.getComplianceJson())));
    }

    @Test
    void professionalSubmitStartsBpmOnceAndEntersIpReview() {
        PositioningCardDO card = card(true, MediaWorkflowConstants.POSITIONING_CO_CREATING, 0);
        when(mapper.selectById(1L)).thenReturn(card);
        PostRespDTO post = new PostRespDTO();
        post.setId(30L); post.setStatus(0);
        AdminUserRespDTO reviewer = new AdminUserRespDTO();
        reviewer.setId(254L); reviewer.setStatus(0);
        AdminUserRespDTO disabled = new AdminUserRespDTO();
        disabled.setId(255L); disabled.setStatus(1);
        when(postApi.getPostByCode(MediaWorkflowConstants.POST_CODE_IP_TEACHER)).thenReturn(post);
        when(adminUserApi.getUserListByPostIds(java.util.List.of(30L)))
                .thenReturn(java.util.List.of(reviewer, disabled));
        when(processInstanceApi.createProcessInstance(eq(99L), any())).thenReturn("process-1");
        when(mapper.updateByVersion(any(), eq(0), eq(MediaWorkflowConstants.POSITIONING_CO_CREATING))).thenReturn(1);

        service.submitReview(1L, 0, 99L);

        verify(processInstanceApi, times(1)).createProcessInstance(eq(99L), argThat((BpmProcessInstanceCreateReqDTO request) ->
                java.util.List.of(254L).equals(request.getStartUserSelectAssignees().get("ipReviewer"))
                        && Long.valueOf(254L).equals(request.getVariables().get("assignee"))
                        && java.util.List.of(254L).equals(request.getVariables().get("coll_userList"))));
        verify(mapper).updateByVersion(argThat(value ->
                MediaWorkflowConstants.POSITIONING_IP_REVIEW.equals(value.getStatus())
                        && "process-1".equals(value.getIpProcessInstanceId())
                        && Long.valueOf(254L).equals(value.getIpReviewerUserId())), eq(0),
                eq(MediaWorkflowConstants.POSITIONING_CO_CREATING));
    }

    @Test
    void ipAndOperatorRejectionsReturnToCoCreating() {
        PositioningCardDO ip = card(true, MediaWorkflowConstants.POSITIONING_IP_REVIEW, 1);
        when(mapper.selectByIpProcessId("process-1")).thenReturn(ip);
        when(mapper.transition(1L, 1, MediaWorkflowConstants.POSITIONING_IP_REVIEW,
                MediaWorkflowConstants.POSITIONING_CO_CREATING)).thenReturn(1);
        service.handleIpProcessResult("process-1", 3, "rejected");
        verify(mapper).transition(1L, 1, MediaWorkflowConstants.POSITIONING_IP_REVIEW,
                MediaWorkflowConstants.POSITIONING_CO_CREATING);

        PositioningCardDO operator = card(false, MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 2);
        operator.setId(2L);
        when(mapper.selectById(2L)).thenReturn(operator);
        when(mapper.transition(2L, 2, MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY,
                MediaWorkflowConstants.POSITIONING_CO_CREATING)).thenReturn(1);
        service.operatorReject(2L, 2);
        verify(mapper).transition(2L, 2, MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY,
                MediaWorkflowConstants.POSITIONING_CO_CREATING);
    }

    @Test
    void operatorApprovalNotifiesOnlyTheBoundPartnerAccount() {
        PositioningCardDO card = card(false, MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY, 2);
        card.setCardNo("PC-202608210001");
        when(mapper.selectById(1L)).thenReturn(card);
        when(mapper.transition(1L, 2, MediaWorkflowConstants.POSITIONING_OPERATOR_FEASIBILITY,
                MediaWorkflowConstants.POSITIONING_STUDENT_CONFIRM)).thenReturn(1);
        PartnerStudentLinkDO link = new PartnerStudentLinkDO();
        link.setPartnerId(30L);
        link.setStudentPersonId(20L);
        when(partnerStudentLinkMapper.selectActiveByStudent(20L)).thenReturn(link);
        PartnerAccountDO partnerAccount = new PartnerAccountDO();
        partnerAccount.setId(40L);
        when(partnerAccountMapper.selectByPartnerId(30L)).thenReturn(partnerAccount);

        service.operatorApprove(1L, 2);

        verify(workflowEventService).notify(eq("media.positioning.student_confirmation"),
                eq(MediaWorkflowConstants.BIZ_TYPE_POSITIONING_CARD), eq(1L), isNull(), isNull(),
                eq("positioning-student-confirmation:1:2"), argThat(payload -> payload.get("partnerAccountId").equals(40L)));
    }

    private PositioningCardDO card(boolean professionalRisk, String status, int version) {
        return new PositioningCardDO().setId(1L).setAccountId(10L).setStudentPersonId(20L)
                .setProfessionalRisk(professionalRisk).setStatus(status).setVersion(version).setVersionNo(1);
    }
}
