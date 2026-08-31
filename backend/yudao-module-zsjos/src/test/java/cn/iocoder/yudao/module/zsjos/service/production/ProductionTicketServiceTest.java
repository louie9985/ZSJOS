package cn.iocoder.yudao.module.zsjos.service.production;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAssignmentService;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderService;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderSceneRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ProductionTicketServiceTest {
    @InjectMocks private ProductionTicketService service;
    @Mock private ProductionTicketMapper mapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private MediaWorkflowEventService workflowEventService;
    @Mock private MediaDataScopeService dataScopeService;
    @Mock private PersonMapper personMapper;
    @Mock private PositioningCardSubmissionMapper positioningSubmissionMapper;
    @Mock private LeadAssignmentService relationService;
    @Mock private ProductionTicketCommandService commandService;
    @Mock private WorkOrderService workOrderService;

    @Test
    void createRegistersCommandAndCompletesWithTicketId() {
        ProductionTicketSaveReqVO req = new ProductionTicketSaveReqVO();
        req.setSceneCode("production"); req.setAccountId(7L); req.setTargetDeptId(9L);
        req.setOperatorRemark("  注意前三秒节奏  "); req.setIdempotencyKey("create-key");
        when(commandService.fingerprint(any(Object[].class))).thenReturn("fp");
        when(commandService.begin(eq("create-key"), any(), eq(Long.class)))
                .thenReturn(new ProductionTicketCommandService.Claim<>(true, null));
        when(accountMapper.selectById(7L)).thenReturn(new MediaAccountDO().setId(7L)
                .setAccountNo("MA-7").setNickname("账号七").setOwnerOperatorUserId(20L));
        when(positioningSubmissionMapper.selectCurrentConfirmedByAccount(7L))
                .thenReturn(new PositioningCardSubmissionDO().setId(70L).setSubmissionNo(70));
        stubTemplate();
        doAnswer(invocation -> {
            invocation.<ProductionTicketDO>getArgument(0).setId(100L);
            return 1;
        }).when(mapper).insert(any(ProductionTicketDO.class));

        assertEquals(100L, service.create(req, 20L));

        ArgumentCaptor<ProductionTicketDO> ticketCaptor = ArgumentCaptor.forClass(ProductionTicketDO.class);
        verify(mapper).insert(ticketCaptor.capture());
        assertTrue(ticketCaptor.getValue().getDispatchContextSnapshotJson()
                .contains("\"operatorRemark\":\"注意前三秒节奏\""));
        verify(commandService).complete("create-key", 20L, 100L);
        verify(workflowEventService).transition("production-ticket", 100L, 20L, null,
                "public_pool", null, "ticket-created:100");
        verify(workOrderService).createProductionEnvelope("production", 100L, 7L, 20L,
                null, 9L, "注意前三秒节奏", null, null, "create-key");
        verify(workflowEventService, never()).createTaskAndNotify(anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyMap());
    }

    @Test
    void createExactRetryReplaysTicketIdWithoutSideEffects() {
        ProductionTicketSaveReqVO req = new ProductionTicketSaveReqVO();
        req.setSceneCode("production"); req.setAccountId(7L); req.setAssigneeUserId(30L); req.setIdempotencyKey("create-key");
        when(commandService.fingerprint(any(Object[].class))).thenReturn("fp");
        when(commandService.begin(eq("create-key"), any(), eq(Long.class)))
                .thenReturn(new ProductionTicketCommandService.Claim<>(false, 100L));

        assertEquals(100L, service.create(req, 20L));

        verifyNoInteractions(accountMapper, positioningSubmissionMapper, relationService, workflowEventService, workOrderService);
        verify(mapper, never()).insert(any(ProductionTicketDO.class));
        verify(commandService, never()).complete(anyString(), anyLong(), any());
    }

    @Test
    void rejectRequiresReasonBeforeLoadingTicket() {
        assertThrows(RuntimeException.class, () -> service.reject(1L, 4, "  "));
        verifyNoInteractions(mapper, workflowEventService);
    }

    @Test
    void rejectStoresReasonAndPublishesReworkResult() {
        var ticket = new cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO()
                .setId(1L).setTicketNo("PT-1").setStatus("checking").setVersion(4)
                .setRevisionCount(0).setMaxRevisionCount(2).setReviewerUserId(230L)
                .setAssigneeFilmingEditorUserId(251L);
        when(mapper.selectById(1L)).thenReturn(ticket);
        when(mapper.rejectForRevision(1L, 4, "补充字幕并调整节奏")).thenReturn(1);

        service.reject(1L, 4, "  补充字幕并调整节奏  ");

        verify(mapper).rejectForRevision(1L, 4, "补充字幕并调整节奏");
        verify(workflowEventService).transition("production-ticket", 1L, null, "checking", "rejected",
                "补充字幕并调整节奏", "ticket:1:4:rejected");
        verify(workflowEventService).notify(eq("media.ticket.rejected"), eq("production-ticket"), eq(1L),
                eq(251L), isNull(), eq("ticket-result:1:4:rejected"), anyMap());
    }

    @Test
    void submitCreatesOperatorCheckTaskAndMessageImmediately() {
        var ticket = new cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO()
                .setId(6L).setTicketNo("PT-6").setStatus("in_production").setVersion(2)
                .setReviewerUserId(230L).setAssigneeFilmingEditorUserId(251L);
        when(mapper.selectById(6L)).thenReturn(ticket);
        when(mapper.transition(6L, 2, "in_production", "submitted")).thenReturn(1);

        service.submit(6L, 2);

        verify(workflowEventService).createTaskAndNotify("media.ticket.pending_check", "MEDIA_TICKET_CHECK",
                "production-ticket", 6L, 230L, "拍剪工单待核对", "START_TICKET_CHECK", null,
                "ticket-check:6:2", java.util.Map.of("bizNo", "PT-6",
                        "deepLink", "/zsjos/production-tickets?ticketId=6"));
    }

    @Test
    void createDoesNotCompleteCommandWhenEnvelopeCreationFails() {
        ProductionTicketSaveReqVO req = new ProductionTicketSaveReqVO();
        req.setSceneCode("production"); req.setAccountId(7L); req.setTargetDeptId(9L);
        req.setOperatorRemark("交接说明"); req.setIdempotencyKey("create-envelope-failure");
        when(commandService.fingerprint(any(Object[].class))).thenReturn("fp");
        when(commandService.begin(eq("create-envelope-failure"), any(), eq(Long.class)))
                .thenReturn(new ProductionTicketCommandService.Claim<>(true, null));
        when(accountMapper.selectById(7L)).thenReturn(new MediaAccountDO().setId(7L)
                .setAccountNo("MA-7").setOwnerOperatorUserId(20L));
        when(positioningSubmissionMapper.selectCurrentConfirmedByAccount(7L))
                .thenReturn(new PositioningCardSubmissionDO().setId(70L).setSubmissionNo(70));
        stubTemplate();
        doAnswer(invocation -> { invocation.<ProductionTicketDO>getArgument(0).setId(100L); return 1; })
                .when(mapper).insert(any(ProductionTicketDO.class));
        doThrow(new IllegalStateException("envelope failed")).when(workOrderService)
                .createProductionEnvelope(anyString(), anyLong(), anyLong(), anyLong(), any(), anyLong(),
                        anyString(), any(), any(), anyString());

        assertThrows(IllegalStateException.class, () -> service.create(req, 20L));

        verify(commandService, never()).complete(anyString(), anyLong(), any());
    }

    private void stubTemplate() {
        WorkOrderSceneRespVO template = new WorkOrderSceneRespVO();
        template.setCode("production"); template.setName("拍剪工单"); template.setProcessorType("PRODUCTION_TICKET");
        template.setAllowedAssignmentTypes(java.util.List.of("PERSON", "DEPARTMENT"));
        when(workOrderService.catalog(1, 500, 20L)).thenReturn(new PageResult<>(java.util.List.of(template), 1L));
        when(workOrderService.candidatePage(any(), eq(20L))).thenReturn(PageResult.empty());
    }
}
