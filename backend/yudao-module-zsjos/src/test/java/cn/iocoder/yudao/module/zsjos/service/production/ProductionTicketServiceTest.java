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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Test
    void createRegistersCommandAndCompletesWithTicketId() {
        ProductionTicketSaveReqVO req = new ProductionTicketSaveReqVO();
        req.setAccountId(7L); req.setIdempotencyKey("create-key");
        when(commandService.fingerprint("create", 7L, null, 20L)).thenReturn("fp");
        when(commandService.begin(eq("create-key"), any(), eq(Long.class)))
                .thenReturn(new ProductionTicketCommandService.Claim<>(true, null));
        when(accountMapper.selectById(7L)).thenReturn(new MediaAccountDO().setId(7L)
                .setAccountNo("MA-7").setNickname("账号七").setOwnerOperatorUserId(20L));
        when(positioningSubmissionMapper.selectCurrentConfirmedByAccount(7L))
                .thenReturn(new PositioningCardSubmissionDO().setId(70L).setSubmissionNo(70));
        when(relationService.getConfiguredTargetUsers(anyString(), eq(20L))).thenReturn(java.util.List.of());
        doAnswer(invocation -> {
            invocation.<ProductionTicketDO>getArgument(0).setId(100L);
            return 1;
        }).when(mapper).insert(any(ProductionTicketDO.class));

        assertEquals(100L, service.create(req, 20L));

        verify(commandService).complete("create-key", 20L, 100L);
        verify(workflowEventService).transition("production-ticket", 100L, 20L, null,
                "public_pool", null, "ticket-created:100");
        verify(workflowEventService, never()).createTaskAndNotify(anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyMap());
    }

    @Test
    void createExactRetryReplaysTicketIdWithoutSideEffects() {
        ProductionTicketSaveReqVO req = new ProductionTicketSaveReqVO();
        req.setAccountId(7L); req.setAssigneeUserId(30L); req.setIdempotencyKey("create-key");
        when(commandService.fingerprint("create", 7L, 30L, 20L)).thenReturn("fp");
        when(commandService.begin(eq("create-key"), any(), eq(Long.class)))
                .thenReturn(new ProductionTicketCommandService.Claim<>(false, 100L));

        assertEquals(100L, service.create(req, 20L));

        verifyNoInteractions(accountMapper, positioningSubmissionMapper, relationService, workflowEventService);
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
}
