package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.flow.LeadFlowHistoryRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadFlowHistoryServiceTest {

    @InjectMocks private LeadFlowHistoryService service;
    @Mock private LeadMapper leadMapper;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private LeadAssignmentHistoryMapper assignmentMapper;
    @Mock private LeadFollowUpRecordMapper followUpMapper;
    @Mock private LeadAgingPoolEventMapper agingEventMapper;
    @Mock private PartnerMapper partnerMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private FileApi fileApi;

    private final LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 19, 9, 0);

    @BeforeEach
    void setUp() {
        LeadDO lead = new LeadDO();
        lead.setId(7L); lead.setLeadNo("L202608190001"); lead.setSubmittedAt(submittedAt);
        lead.setSourceUserId(10L); lead.setStatus("submitted"); lead.setAssignmentStatus("unassigned");
        when(leadMapper.selectById(7L)).thenReturn(lead);
        when(eventMapper.selectByLeadId(7L)).thenReturn(List.of());
        when(assignmentMapper.selectByLeadId(7L)).thenReturn(List.of());
        when(followUpMapper.selectListByLeadId(7L)).thenReturn(List.of());
        when(agingEventMapper.selectByLeadId(7L)).thenReturn(List.of());
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
    }

    @Test
    void mergesSourcesDeduplicatesReferencedAssignmentAndSortsNumericIds() {
        LocalDateTime occurredAt = submittedAt.plusMinutes(10);
        BusinessEventDO accepted = event(9L, "lead_assignment_accepted", occurredAt,
                "pending_acceptance", "owned");
        accepted.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("assignmentHistoryId", 20L)));
        LeadAssignmentHistoryDO duplicate = assignment(20L, "accept", occurredAt);
        LeadAssignmentHistoryDO transfer = assignment(10L, "transfer", occurredAt);
        LeadAgingPoolEventDO aging = new LeadAgingPoolEventDO();
        aging.setId(11L); aging.setLeadId(7L); aging.setEventType("assigned"); aging.setOccurredAt(occurredAt);
        when(eventMapper.selectByLeadId(7L)).thenReturn(List.of(accepted));
        when(assignmentMapper.selectByLeadId(7L)).thenReturn(List.of(duplicate, transfer));
        when(agingEventMapper.selectByLeadId(7L)).thenReturn(List.of(aging));

        List<LeadFlowHistoryRespVO> result = service.getHistory(7L);

        assertEquals(List.of("aging:11", "lead:7", "assignment:10", "event:9"),
                result.stream().map(LeadFlowHistoryRespVO::getId).toList());
        assertEquals("待接单", result.get(3).getAssignmentStatusBefore());
        assertEquals("已归属", result.get(3).getAssignmentStatusAfter());
    }

    @Test
    void mapsMixedTransferStatusesIntoLeadAndAssignmentTransitions() {
        BusinessEventDO transfer = event(1L, "lead_transferred", submittedAt.plusMinutes(1),
                "suspended", "submitted");
        transfer.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of(
                "fromOwnerUserId", 20L, "toOwnerUserId", 30L)));
        AdminUserRespDTO from = user(20L, "原销售");
        AdminUserRespDTO to = user(30L, "新销售");
        when(eventMapper.selectByLeadId(7L)).thenReturn(List.of(transfer));
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of(20L, from, 30L, to));

        LeadFlowHistoryRespVO result = service.getHistory(7L).stream()
                .filter(item -> "event:1".equals(item.getId())).findFirst().orElseThrow();

        assertEquals("主管转派", result.getFlowNode());
        assertEquals("已挂起", result.getLeadStatusBefore());
        assertEquals("已提交", result.getLeadStatusAfter());
        assertNull(result.getAssignmentStatusBefore());
        assertNull(result.getAssignmentStatusAfter());
        assertEquals("原销售", result.getFromOwner());
        assertEquals("新销售", result.getToOwner());
    }

    @Test
    void distinguishesAutomaticAndSpecifiedDispatchFromPersistedRuleReference() {
        LeadAssignmentHistoryDO automatic = assignment(2L, "dispatch", submittedAt.plusMinutes(2));
        automatic.setOperatorUserId(10L); automatic.setAssignmentRuleId(99L);
        LeadAssignmentHistoryDO specified = assignment(1L, "dispatch", submittedAt.plusMinutes(1));
        specified.setOperatorUserId(10L); specified.setAssignmentRuleId(null);
        when(assignmentMapper.selectByLeadId(7L)).thenReturn(List.of(automatic, specified));

        List<LeadFlowHistoryRespVO> result = service.getHistory(7L);

        assertEquals("客资提交", result.get(0).getFlowNode());
        assertEquals("自动分配", result.get(1).getSource());
        assertEquals("指定派单", result.get(2).getSource());
    }

    @Test
    void keepsSubmissionBeforeAssignmentWhenTimestampsMatchOrAssignmentPredatesSubmission() {
        LeadAssignmentHistoryDO sameTime = assignment(2L, "dispatch", submittedAt);
        LeadAssignmentHistoryDO earlier = assignment(1L, "transfer", submittedAt.minusMinutes(1));
        when(assignmentMapper.selectByLeadId(7L)).thenReturn(List.of(sameTime, earlier));

        List<LeadFlowHistoryRespVO> result = service.getHistory(7L);

        assertEquals(List.of("lead:7", "assignment:2", "assignment:1"),
                result.stream().map(LeadFlowHistoryRespVO::getId).toList());
    }

    @Test
    void parsesBothEvidenceShapesAndMarksUnavailableFiles() {
        BusinessEventDO event = event(3L, "lead_appeal_submitted", submittedAt.plusMinutes(3),
                "invalid", "invalid");
        event.setEvidenceRefs("["
                + "{\"infraFileId\":41,\"originalName\":\"证明.pdf\",\"contentType\":\"application/pdf\"},"
                + "{\"infraFileId\":42,\"name\":\"截图.png\",\"type\":\"image/png\"},"
                + "{\"infraFileId\":43,\"name\":\"说明.docx\",\"type\":\"application/vnd.openxmlformats-officedocument.wordprocessingml.document\"}]");
        when(eventMapper.selectByLeadId(7L)).thenReturn(List.of(event));
        when(fileApi.presignGetUrl(41L, 600)).thenReturn("https://files.test/proof");
        when(fileApi.presignGetUrl(42L, 600)).thenThrow(new IllegalStateException("missing"));

        List<LeadFlowHistoryRespVO.AttachmentVO> result = service.getHistory(7L).getFirst().getAttachments();

        assertEquals("证明.pdf", result.get(0).getOriginalName());
        assertTrue(result.get(0).getPreviewable()); assertTrue(result.get(0).getAvailable());
        assertEquals("截图.png", result.get(1).getOriginalName());
        assertTrue(result.get(1).getPreviewable()); assertFalse(result.get(1).getAvailable());
        assertFalse(result.get(2).getPreviewable()); assertFalse(result.get(2).getAvailable());
    }

    @Test
    void mapsHistoricalAppealStatusesToActualLeadTransition() {
        BusinessEventDO overturned = event(4L, "lead_appeal_overturned", submittedAt.plusMinutes(4),
                "sales_manager_reviewing", "overturned");
        overturned.setReason("改判原因");
        BusinessEventDO submitted = event(3L, "lead_appeal_submitted", submittedAt.plusMinutes(3),
                "invalid", "sales_manager_reviewing");
        when(eventMapper.selectByLeadId(7L)).thenReturn(List.of(overturned, submitted));

        List<LeadFlowHistoryRespVO> result = service.getHistory(7L);

        assertEquals("无效", result.get(0).getLeadStatusBefore());
        assertEquals("有效", result.get(0).getLeadStatusAfter());
        assertEquals("改判原因", result.get(0).getReason());
        assertNull(result.get(0).getRemark());
        assertNull(result.get(1).getLeadStatusBefore());
        assertNull(result.get(1).getLeadStatusAfter());
    }

    @Test
    void separatesQualificationReasonAndRemarkAndResolvesFollowUpRemark() {
        BusinessEventDO invalid = event(6L, "lead_qualified_invalid", submittedAt.plusMinutes(6),
                "valid", "invalid");
        invalid.setReason("补充说明");
        invalid.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("reasonLabel", "信息不实")));
        BusinessEventDO followUp = event(5L, "lead_follow_up_recorded", submittedAt.plusMinutes(5), null, null);
        followUp.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("followUpRecordId", 50L)));
        LeadFollowUpRecordDO record = new LeadFollowUpRecordDO();
        record.setId(50L); record.setRemark("跟进备注");
        when(eventMapper.selectByLeadId(7L)).thenReturn(List.of(invalid, followUp));
        when(followUpMapper.selectListByLeadId(7L)).thenReturn(List.of(record));

        List<LeadFlowHistoryRespVO> result = service.getHistory(7L);

        assertEquals("信息不实", result.get(0).getReason());
        assertEquals("补充说明", result.get(0).getRemark());
        assertNull(result.get(1).getReason());
        assertEquals("跟进备注", result.get(1).getRemark());
    }

    private BusinessEventDO event(Long id, String type, LocalDateTime at, String from, String to) {
        BusinessEventDO event = new BusinessEventDO();
        event.setId(id); event.setAggregateId(7L); event.setEventType(type); event.setOccurredAt(at);
        event.setFromStatus(from); event.setToStatus(to);
        return event;
    }

    private LeadAssignmentHistoryDO assignment(Long id, String action, LocalDateTime at) {
        LeadAssignmentHistoryDO item = new LeadAssignmentHistoryDO();
        item.setId(id); item.setLeadId(7L); item.setActionType(action); item.setOccurredAt(at);
        return item;
    }

    private AdminUserRespDTO user(Long id, String nickname) {
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(id); user.setNickname(nickname); return user;
    }
}
