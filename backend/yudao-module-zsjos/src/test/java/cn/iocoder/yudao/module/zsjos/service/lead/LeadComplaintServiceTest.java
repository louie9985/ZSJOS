package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadComplaintDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadComplaintMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintCreateReqVO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.COMPLAINT_FOUNDED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.COMPLAINT_UNFOUNDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadComplaintServiceTest {

    @InjectMocks private LeadComplaintService service;
    @Mock private LeadComplaintMapper complaintMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadNotifyEventPublisher notifyPublisher;
    @Mock private LeadAttachmentService attachmentService;
    @Mock private LeadSubmissionIdentityService identityService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private FileApi fileApi;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void leadHistoryReturnsOnlyRequestedLeadWithNamesAndSignedEvidence() throws Exception {
        LeadDO lead = new LeadDO().setId(1L);
        lead.setLeadNo("KZ202608170001");
        LeadComplaintDO row = new LeadComplaintDO();
        row.setId(11L); row.setLeadId(1L); row.setComplainantUserId(10L); row.setSalesUserId(20L);
        row.setHandlerUserId(30L); row.setStatus("handled"); row.setResult("founded"); row.setReason("跟进不及时");
        row.setEvidenceRefs("[{\"infraFileId\":101,\"name\":\"evidence.png\",\"type\":\"image/png\",\"size\":12}]");
        row.setHandlerEvidenceRefs("[]");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(complaintMapper.selectListByLeadId(1L)).thenReturn(List.of(row));
        when(adminUserApi.getUserList(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(
                user(10L, "提交人"), user(20L, "销售"), user(30L, "处理人")));
        when(fileApi.presignGetUrls(List.of(101L), 600)).thenReturn(Map.of(101L, "https://example.test/evidence.png"));

        var result = service.getLeadComplaints(1L, 40L);

        assertEquals(1, result.size());
        assertEquals("KZ202608170001", result.get(0).getLeadNo());
        assertEquals("提交人", result.get(0).getComplainantUserName());
        assertEquals("销售", result.get(0).getSalesUserName());
        assertEquals("处理人", result.get(0).getHandlerUserName());
        assertEquals("https://example.test/evidence.png", result.get(0).getEvidence().get(0).getFileUrl());
        verify(complaintMapper).selectListByLeadId(1L);

        ZsjosPermission permission = LeadComplaintService.class
                .getMethod("getLeadComplaints", Long.class, Long.class).getAnnotation(ZsjosPermission.class);
        assertNotNull(permission);
        assertEquals("read", permission.action());
    }

    @Test
    void foundedDecisionNotifiesActualComplainantAndSalesContext() {
        assertDecisionNotification("founded", COMPLAINT_FOUNDED, 10L, null);
    }

    @Test
    void unfoundedPartnerDecisionStillNotifiesActualComplainant() {
        assertDecisionNotification("unfounded", COMPLAINT_UNFOUNDED, null, 70L);
    }

    @Test
    void successfulComplaintCreationAdvancesLeadActivityButReplayDoesNot() {
        LeadDO lead = new LeadDO().setId(1L);
        lead.setStatus("valid"); lead.setOwnerUserId(20L);
        lead.setProviderOwnerType("system_user"); lead.setProviderOwnerId(10L);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(attachmentService.validateReferences(anyList(), eq(10L))).thenReturn(Map.of());
        LeadComplaintCreateReqVO request = new LeadComplaintCreateReqVO();
        request.setReason("跟进不及时"); request.setIdempotencyKey("create-key");

        service.create(1L, 10L, request);

        verify(leadMapper).touchActivity(eq(1L), any(java.time.LocalDateTime.class));
        LeadComplaintDO replay = new LeadComplaintDO(); replay.setId(11L);
        when(complaintMapper.selectByCreateKey("create-key")).thenReturn(replay);
        assertEquals(11L, service.create(1L, 10L, request));
        verify(leadMapper, times(1)).touchActivity(eq(1L), any(java.time.LocalDateTime.class));
    }

    @SuppressWarnings("unchecked")
    private void assertDecisionNotification(String result, String sceneCode, Long complainantUserId, Long partnerId) {
        LeadComplaintDO row = new LeadComplaintDO();
        row.setId(11L); row.setLeadId(1L); row.setComplainantUserId(complainantUserId); row.setPartnerId(partnerId);
        row.setSalesUserId(20L); row.setStatus("pending");
        when(complaintMapper.selectByIdForUpdate(11L, 1L)).thenReturn(row);
        when(attachmentService.validateReferences(anyList(), eq(30L))).thenReturn(Map.of());
        LeadComplaintDecisionReqVO request = new LeadComplaintDecisionReqVO();
        request.setResult(result); request.setOpinion("处理意见"); request.setIdempotencyKey("decision-key");

        service.decide(11L, 30L, request);

        org.mockito.ArgumentCaptor<Map<String, Object>> context = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(notifyPublisher).publish(eq(sceneCode), eq(1L), eq("lead-complaint-" + result + ":11"),
                eq(30L), any(), context.capture());
        verify(leadMapper).touchActivity(eq(1L), any(java.time.LocalDateTime.class));
        assertEquals(result, context.getValue().get("complaint.result"));
        assertEquals("处理意见", context.getValue().get("complaint.handlerOpinion"));
        assertEquals(complainantUserId, context.getValue().get("complaint.complainantUserId"));
        assertEquals(partnerId, context.getValue().get("complaint.partnerId"));
    }

    private static AdminUserRespDTO user(Long id, String name) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id); user.setNickname(name);
        return user;
    }
}
