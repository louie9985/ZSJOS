package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterAssistRequestReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadSubmitterAssistRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadSubmitterAssistRequestMapper;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOwnershipService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.validation.Validation;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadSubmitterActionServiceTest {

    @InjectMocks private LeadSubmitterActionService service;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadSubmitterAssistRequestMapper assistRequestMapper;
    @Mock private LeadAttachmentService attachmentService;
    @Mock private LeadObjectPermissionService objectPermissionService;
    @Mock private PartnerOwnershipService partnerOwnershipService;
    @Mock private BusinessTaskCommandService businessTaskCommandService;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private LeadNotifyEventPublisher notifyPublisher;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        lenient().when(attachmentService.validateReferences(anyList(), anyLong())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void requestsInternalSubmitterAndCreatesTaskForSubmitter() {
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(internalLead());
        assignInsertedId();

        assertEquals(81L, service.requestAssist(1L, 20L, request("key-1")));

        ArgumentCaptor<BusinessTaskCreateCommand> task = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(businessTaskCommandService).create(task.capture());
        assertEquals(10L, task.getValue().assigneeId());
        assertEquals(TASK_TYPE_SUBMITTER_ASSIST, task.getValue().taskType());
        verify(notifyPublisher).publish(eq(SUBMITTER_ASSIST_REQUESTED), eq(1L), anyString(), eq(20L), any(), anyMap());
        verify(notifyPublisher, never()).publish(eq(PARTNER_ASSIST_REMINDER), anyLong(), anyString(), anyLong(), any(), anyMap());
    }

    @Test
    void partnerSubmissionUsesCurrentOwnerForTaskAndReminder() {
        LeadDO lead = partnerLead();
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(partnerOwnershipService.getByPartnerId(70L)).thenReturn(new PartnerOwnershipDO()
                .setPartnerId(70L).setEmployeeUserId(30L).setEmployeeNameSnapshot("当前员工"));
        assignInsertedId();

        service.requestAssist(1L, 20L, request("key-2"));

        ArgumentCaptor<BusinessTaskCreateCommand> task = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(businessTaskCommandService).create(task.capture());
        assertEquals(30L, task.getValue().assigneeId());
        verify(notifyPublisher).publish(eq(PARTNER_ASSIST_REMINDER), eq(1L), anyString(), eq(20L), any(),
                argThat(payload -> Long.valueOf(30L).equals(payload.get("partnerOwnerUserId"))));
    }

    @Test
    void partnerSubmissionFallsBackToHistoricalOwnerWhenCurrentOwnershipHasNoEmployee() {
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(partnerLead());
        when(partnerOwnershipService.getByPartnerId(70L)).thenReturn(new PartnerOwnershipDO().setPartnerId(70L));
        assignInsertedId();

        service.requestAssist(1L, 20L, request("key-partner-fallback"));

        ArgumentCaptor<BusinessTaskCreateCommand> task = ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(businessTaskCommandService).create(task.capture());
        assertEquals(31L, task.getValue().assigneeId());
        verify(notifyPublisher).publish(eq(PARTNER_ASSIST_REMINDER), eq(1L), anyString(), eq(20L), any(),
                argThat(payload -> Long.valueOf(31L).equals(payload.get("partnerOwnerUserId"))));
    }

    @Test
    void partnerSubmissionRejectsWhenNoCurrentOrHistoricalOwnerExists() {
        LeadDO lead = partnerLead().setPartnerOwnerUserIdSnapshot(null).setPartnerOwnerNameSnapshot(null);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(partnerOwnershipService.getByPartnerId(70L)).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> service.requestAssist(1L, 20L, request("key-partner-owner-missing")));

        verify(assistRequestMapper, never()).insert(any(LeadSubmitterAssistRequestDO.class));
        verify(businessTaskCommandService, never()).create(any());
        verify(notifyPublisher, never()).publish(anyString(), anyLong(), anyString(), anyLong(), any(), anyMap());
    }

    @Test
    void exactIdempotencyReplayReturnsExistingRequest() {
        LeadSubmitterAssistRequestDO replay = new LeadSubmitterAssistRequestDO().setId(99L);
        LeadSubmitterAssistRequestReqVO request = request("key-replay");
        when(assistRequestMapper.selectByIdempotencyKey("key-replay")).thenReturn(null, replay);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(internalLead());
        doAnswer(invocation -> {
            LeadSubmitterAssistRequestDO row = invocation.getArgument(0);
            replay.setRequestFingerprint(row.getRequestFingerprint());
            row.setId(99L);
            return 1;
        }).when(assistRequestMapper).insert(any(LeadSubmitterAssistRequestDO.class));

        assertEquals(99L, service.requestAssist(1L, 20L, request));
        assertEquals(99L, service.requestAssist(1L, 20L, request));
        verify(assistRequestMapper, times(1)).insert(any(LeadSubmitterAssistRequestDO.class));
        verify(businessTaskCommandService, times(1)).create(any());
    }

    @Test
    void validatesRequiredPlainTextAndAttachmentLimit() {
        LeadSubmitterAssistRequestReqVO invalid = request("key-validation");
        invalid.setProblem("   "); invalid.setExpectedAssistance("");
        invalid.setAttachments(java.util.stream.LongStream.rangeClosed(1, 10).mapToObj(id -> {
            LeadAttachmentReqVO attachment = new LeadAttachmentReqVO(); attachment.setInfraFileId(id); return attachment;
        }).toList());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var paths = factory.getValidator().validate(invalid).stream()
                    .map(violation -> violation.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
            assertTrue(paths.containsAll(List.of("problem", "expectedAssistance", "attachments")));
        }
    }

    private void assignInsertedId() {
        doAnswer(invocation -> { invocation.<LeadSubmitterAssistRequestDO>getArgument(0).setId(81L); return 1; })
                .when(assistRequestMapper).insert(any(LeadSubmitterAssistRequestDO.class));
    }

    private LeadDO internalLead() {
        return new LeadDO().setId(1L).setLeadNo("KZ202608300001")
                .setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(10L)
                .setProviderOwnerNameSnapshot("内部提交人");
    }

    private LeadDO partnerLead() {
        return new LeadDO().setId(1L).setLeadNo("KZ202608300001")
                .setProviderOwnerType(PROVIDER_OWNER_PARTNER).setProviderOwnerId(70L)
                .setProviderOwnerNameSnapshot("兼职提交人").setPartnerOwnerUserIdSnapshot(31L)
                .setPartnerOwnerNameSnapshot("历史员工");
    }

    private LeadSubmitterAssistRequestReqVO request(String key) {
        LeadSubmitterAssistRequestReqVO request = new LeadSubmitterAssistRequestReqVO();
        request.setProblem("无法确认报考信息"); request.setExpectedAssistance("请联系客户确认");
        request.setRemark("今天处理"); request.setAttachments(List.of()); request.setIdempotencyKey(key);
        return request;
    }
}
