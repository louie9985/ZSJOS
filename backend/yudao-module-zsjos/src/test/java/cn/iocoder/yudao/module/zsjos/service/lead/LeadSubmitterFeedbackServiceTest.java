package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterFeedbackReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.personnel.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class LeadSubmitterFeedbackServiceTest {
    @InjectMocks private LeadSubmitterFeedbackService service;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadSubmitterFeedbackMapper feedbackMapper;
    @Mock private LeadSubmitterFeedbackAttachmentMapper attachmentMapper;
    @Mock private LeadSubmitterFeedbackPermissionProvider permission;
    @Mock private LeadObjectPermissionService identityPermission;
    @Mock private PartnerAccountService partnerAccountService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private FileApi fileApi;
    @Mock private LeadNotifyEventPublisher publisher;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "feedback-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LeadDO.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LeadSubmitterFeedbackAttachmentDO.class);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private LeadDO lead() {
        var lead = new LeadDO(); lead.setId(1L); lead.setOwnerUserId(20L); lead.setVersion(3);
        lead.setStatus("valid"); lead.setProviderOwnerType("system_user"); lead.setProviderOwnerId(10L);
        lead.setProviderOwnerNameSnapshot("提交人"); return lead;
    }
    private LeadSubmitterFeedbackReqVO request() {
        var request = new LeadSubmitterFeedbackReqVO(); request.setFeedback("  已联系\n等待资料  ");
        request.setVersion(3); request.setIdempotencyKey("intent"); request.setAttachmentIds(List.of()); return request;
    }
    private void prepareCreate(LeadDO lead) {
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(adminUserApi.getUser(20L)).thenReturn(new AdminUserRespDTO().setId(20L).setNickname("销售"));
        if ("system_user".equals(lead.getProviderOwnerType()))
            when(adminUserApi.getUser(10L)).thenReturn(new AdminUserRespDTO().setId(10L).setStatus(0));
        when(leadMapper.updateVersionAndTouchActivity(eq(1L), eq(3), any())).thenReturn(1);
        doAnswer(call -> { call.<LeadSubmitterFeedbackDO>getArgument(0).setId(77L); return 1; })
                .when(feedbackMapper).insert(any(LeadSubmitterFeedbackDO.class));
    }
    @Test void savesMultilineFeedbackAndNotifiesEmployee() {
        prepareCreate(lead());
        assertEquals(77L, service.create(1L, 20L, request()));
        verify(feedbackMapper).insert(argThat((LeadSubmitterFeedbackDO row) -> "已联系\n等待资料".equals(row.getFeedback())
                && "ADMIN".equals(row.getSubmitterSubjectType()) && Long.valueOf(10L).equals(row.getSubmitterUserId())));
        verify(publisher).publish(eq("zsjos.lead.submitter_feedback_created"), eq(1L), eq("lead-submitter-feedback:77"),
                eq(20L), any(), argThat(payload -> Long.valueOf(10L).equals(payload.get("feedback.recipientId"))));
        ArgumentCaptor<LocalDateTime> activityAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(publisher).publish(anyString(), eq(1L), anyString(), eq(20L), activityAt.capture(), anyMap());
        verify(leadMapper).updateVersionAndTouchActivity(1L, 3, activityAt.getValue());
    }
    @Test void partnerNotificationUsesAccountIdNotPartnerId() {
        LeadDO lead = lead(); lead.setProviderOwnerType("partner"); lead.setProviderOwnerId(50L);
        prepareCreate(lead);
        when(partnerAccountService.getByPartnerId(50L)).thenReturn(new PartnerAccountDO().setId(90L).setPartnerId(50L).setStatus(0));
        service.create(1L, 20L, request());
        verify(publisher).publish(anyString(), eq(1L), anyString(), eq(20L), any(),
                argThat(payload -> "PARTNER".equals(payload.get("feedback.recipientType"))
                        && Long.valueOf(90L).equals(payload.get("feedback.recipientId"))));
    }
    @Test void rechecksOwnershipUnderLock() {
        var lead = lead(); lead.setOwnerUserId(21L); when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        assertEquals(LEAD_PERMISSION_DENIED.getCode(), assertThrows(ServiceException.class,
                () -> service.create(1L, 20L, request())).getCode());
        verifyNoInteractions(feedbackMapper, attachmentMapper, publisher);
    }
    @Test void rejectsTerminalStatesAndStaleVersionBeforeMutation() {
        var lead = lead(); when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        for (String status : List.of("invalid", "closed", "won")) {
            lead.setStatus(status);
            assertEquals(LEAD_FEEDBACK_STATE_INVALID.getCode(), assertThrows(ServiceException.class,
                    () -> service.create(1L, 20L, request())).getCode());
        }
        lead.setStatus("valid"); lead.setVersion(4);
        assertEquals(LEAD_FEEDBACK_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.create(1L, 20L, request())).getCode());
        verify(feedbackMapper, never()).insert(any(LeadSubmitterFeedbackDO.class)); verifyNoInteractions(publisher);
    }
    @Test void retryReturnsOriginalAndChangedPayloadConflicts() {
        prepareCreate(lead()); service.create(1L, 20L, request());
        var captor = ArgumentCaptor.forClass(LeadSubmitterFeedbackDO.class); verify(feedbackMapper).insert(captor.capture());
        when(feedbackMapper.findReplay(1L, 20L, "intent")).thenReturn(captor.getValue());
        assertEquals(77L, service.create(1L, 20L, request()));
        var changed = request(); changed.setFeedback("其他内容");
        assertEquals(LEAD_FEEDBACK_IDEMPOTENCY_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.create(1L, 20L, changed)).getCode());
        verify(publisher, times(1)).publish(anyString(), anyLong(), anyString(), anyLong(), any(), anyMap());
    }
    @Test void rejectsMissingDuplicateExpiredBoundAndForeignAttachments() {
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead());
        var request = request(); request.setAttachmentIds(List.of(7L, 7L));
        assertThrows(ServiceException.class, () -> service.create(1L, 20L, request));
        request.setAttachmentIds(List.of(7L));
        assertThrows(ServiceException.class, () -> service.create(1L, 20L, request));
        var file = new LeadSubmitterFeedbackAttachmentDO().setFileId(7L).setLeadId(1L).setUploaderUserId(20L)
                .setExpiresAt(LocalDateTime.now().minusHours(1));
        when(attachmentMapper.findFile(7L)).thenReturn(file);
        assertThrows(ServiceException.class, () -> service.create(1L, 20L, request));
        file.setExpiresAt(LocalDateTime.now().plusHours(1)); file.setFeedbackId(8L);
        assertThrows(ServiceException.class, () -> service.create(1L, 20L, request));
        file.setFeedbackId(null); file.setUploaderUserId(21L);
        assertThrows(ServiceException.class, () -> service.create(1L, 20L, request));
        file.setUploaderUserId(20L); file.setLeadId(2L);
        assertThrows(ServiceException.class, () -> service.create(1L, 20L, request));
        file.setLeadId(1L);
        when(fileApi.getFileInfo(7L)).thenReturn(new FileInfoRespDTO(7L, 1L, "a.pdf",
                "zsjos/lead-feedback/2/1/20/a.pdf", "", "application/pdf", 10L, "20"));
        assertEquals(LEAD_FEEDBACK_ATTACHMENT_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.create(1L, 20L, request)).getCode());
        verifyNoInteractions(publisher);
    }
    @Test void validatesTextAndFileCount() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator(); var req = request();
            assertTrue(validator.validate(req).isEmpty());
            req.setFeedback(" "); assertFalse(validator.validate(req).isEmpty());
            req.setFeedback("x".repeat(5001)); assertFalse(validator.validate(req).isEmpty());
            req.setFeedback("ok"); req.setAttachmentIds(Collections.nCopies(21, 1L));
            assertFalse(validator.validate(req).isEmpty());
        }
    }
    @Test void bindsValidAttachmentBeforePublishing() {
        prepareCreate(lead());
        var request = request(); request.setAttachmentIds(List.of(7L));
        when(attachmentMapper.findFile(7L)).thenReturn(new LeadSubmitterFeedbackAttachmentDO()
                .setId(8L).setFileId(7L).setLeadId(1L).setUploaderUserId(20L)
                .setExpiresAt(LocalDateTime.now().plusHours(1)));
        when(fileApi.getFileInfo(7L)).thenReturn(new FileInfoRespDTO(7L, 1L, "a.pdf",
                "zsjos/lead-feedback/1/1/20/a.pdf", "", "application/pdf", 10L, "20"));
        when(attachmentMapper.update(isNull(), any())).thenReturn(1);
        assertEquals(77L, service.create(1L, 20L, request));
        var ordered = inOrder(feedbackMapper, attachmentMapper, publisher);
        ordered.verify(feedbackMapper).insert(any(LeadSubmitterFeedbackDO.class));
        ordered.verify(attachmentMapper).update(isNull(), any());
        ordered.verify(publisher).publish(anyString(), eq(1L), anyString(), eq(20L), any(), anyMap());
    }
    @Test void partnerReadIsScopedToRecipientAccount() {
        when(partnerAccountService.requireContext(90L)).thenReturn(new PartnerContext(90L, 50L));
        var lead = lead(); lead.setProviderOwnerType("partner"); lead.setProviderOwnerId(50L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(feedbackMapper.page(eq(1L), any(), eq("PARTNER"), eq(90L))).thenReturn(new PageResult<>(List.of(), 0L));
        assertTrue(service.pagePartner(1L, 90L, new PageParam()).getList().isEmpty());
        lead.setProviderOwnerId(51L);
        assertThrows(ServiceException.class, () -> service.pagePartner(1L, 90L, new PageParam()));
    }
}
