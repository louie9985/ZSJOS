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
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import lombok.Data;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ATTACHMENT_URL_EXPIRATION_SECONDS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_NOT_EXISTS;

@Service
public class LeadFlowHistoryService {
    @Resource private LeadMapper leadMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadAssignmentHistoryMapper assignmentMapper;
    @Resource private LeadFollowUpRecordMapper followUpMapper;
    @Resource private LeadAgingPoolEventMapper agingEventMapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private FileApi fileApi;

    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "flow-read")
    public List<LeadFlowHistoryRespVO> getHistory(Long leadId) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        List<BusinessEventDO> events = eventMapper.selectByLeadId(leadId);
        List<LeadAssignmentHistoryDO> assignments = assignmentMapper.selectByLeadId(leadId);
        Map<Long, LeadAssignmentHistoryDO> assignmentsById = assignments.stream()
                .filter(item -> item.getId() != null)
                .collect(java.util.stream.Collectors.toMap(LeadAssignmentHistoryDO::getId, item -> item,
                        (first, ignored) -> first));
        Map<Long, LeadFollowUpRecordDO> followUps = followUpMapper.selectListByLeadId(leadId).stream()
                .collect(java.util.stream.Collectors.toMap(LeadFollowUpRecordDO::getId, item -> item));
        List<LeadAgingPoolEventDO> agingEvents = agingEventMapper.selectByLeadId(leadId);

        Set<Long> referencedAssignments = new HashSet<>();
        events.forEach(event -> optionalLong(event.getRelatedObjectRefs(), "assignmentHistoryId")
                .ifPresent(referencedAssignments::add));
        Set<Long> userIds = new HashSet<>();
        addUser(userIds, lead.getSourceUserId());
        events.forEach(event -> addUser(userIds, event.getOperatorUserId()));
        assignments.forEach(item -> {
            addUser(userIds, item.getOperatorUserId()); addUser(userIds, item.getFromOwnerUserId());
            addUser(userIds, item.getToOwnerUserId()); addUser(userIds, item.getCandidateUserId());
        });
        agingEvents.forEach(item -> {
            addUser(userIds, item.getOperatorUserId()); addUser(userIds, item.getPreviousCollaboratorUserId());
            addUser(userIds, item.getCollaboratorUserId());
        });
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(userIds);

        List<LeadFlowHistoryRespVO> result = new ArrayList<>();
        PartnerDO partner = lead.getPartnerId() == null ? null : partnerMapper.selectById(lead.getPartnerId());
        LeadFlowHistoryRespVO submitted = submission(lead, users, partner);
        var remarks = LeadRemarkHistoryService.project(lead, events, null, true);
        if (remarks.hasLegacy()) submitted.setRemark(null);
        result.add(submitted);
        events.forEach(event -> result.add(fromEvent(event, users, followUps, assignmentsById)));
        assignments.stream().filter(item -> !referencedAssignments.contains(item.getId()))
                .forEach(item -> result.add(fromAssignment(item, users)));
        agingEvents.forEach(item -> result.add(fromAging(item, users)));
        result.sort(Comparator.comparing(LeadFlowHistoryRespVO::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(LeadFlowHistoryService::sameTimeOrder)
                .thenComparing(LeadFlowHistoryService::rawId, Comparator.reverseOrder())
                .thenComparing(LeadFlowHistoryRespVO::getId, Comparator.reverseOrder()));
        return result;
    }

    private LeadFlowHistoryRespVO submission(LeadDO lead, Map<Long, AdminUserRespDTO> users, PartnerDO partner) {
        LeadFlowHistoryRespVO vo = base("lead:" + lead.getId(),
                lead.getSubmittedAt() == null ? lead.getCreateTime() : lead.getSubmittedAt(), "客资", "客资提交",
                lead.getPartnerId() != null ? "兼职端" : "员工工作台",
                partner == null ? name(users, lead.getSourceUserId()) : partner.getName(), null);
        vo.setLeadStatusAfter("已提交");
        vo.setAssignmentStatusAfter("未分配");
        vo.setRemark(lead.getRemark());
        return vo;
    }

    private LeadFlowHistoryRespVO fromEvent(BusinessEventDO event, Map<Long, AdminUserRespDTO> users,
                                             Map<Long, LeadFollowUpRecordDO> followUps,
                                             Map<Long, LeadAssignmentHistoryDO> assignmentsById) {
        String node = eventLabel(event.getEventType());
        boolean system = event.getOperatorUserId() == null || event.getOperatorUserId() == 0;
        LeadAssignmentHistoryDO assignment = optionalLong(event.getRelatedObjectRefs(), "assignmentHistoryId")
                .map(assignmentsById::get).orElse(null);
        LeadFlowHistoryRespVO vo = base("event:" + event.getId(), event.getOccurredAt(), businessObject(event.getEventType()),
                node, eventSource(event, assignment, system),
                system ? "系统" : name(users, event.getOperatorUserId()), eventReason(event, assignment));
        vo.setRemark(eventRemark(event, followUps));
        if (LeadSupplementSnapshot.EVENT.equals(event.getEventType())) {
            Map<?, ?> payload = LeadRemarkHistoryService.payload(event);
            if (payload != null && LeadSupplementSnapshot.MODE.equals(payload.get("remarkMode"))) {
                // The actor may be a Partner; a missing ADMIN user ID does not mean a system action.
                vo.setOperator("提交人");
                vo.setSource("partner".equals(payload.get("submitterType")) ? "兼职端" : "员工工作台");
            }
        }
        applyEventTransitions(vo, event);
        vo.setFromOwner(name(users, optionalLong(event.getRelatedObjectRefs(), "fromOwnerUserId")
                .orElse(assignment == null ? null : assignment.getFromOwnerUserId())));
        vo.setToOwner(name(users, optionalLong(event.getRelatedObjectRefs(), "toOwnerUserId")
                .orElse(assignment == null ? null : firstNonNull(assignment.getToOwnerUserId(), assignment.getCandidateUserId()))));
        if (vo.getAssignmentStatusAfter() == null && assignment != null) {
            vo.setAssignmentStatusBefore(assignmentBefore(assignment.getActionType()));
            vo.setAssignmentStatusAfter(assignmentAfter(assignment.getActionType()));
        }
        vo.setAttachments(attachments(event.getEvidenceRefs()));
        return vo;
    }

    private LeadFlowHistoryRespVO fromAssignment(LeadAssignmentHistoryDO item, Map<Long, AdminUserRespDTO> users) {
        LeadFlowHistoryRespVO vo = base("assignment:" + item.getId(), item.getOccurredAt(), "客资分配",
                assignmentLabel(item.getActionType()), assignmentSource(item),
                name(users, item.getOperatorUserId()), item.getReason());
        vo.setFromOwner(name(users, item.getFromOwnerUserId()));
        vo.setToOwner(name(users, item.getToOwnerUserId() != null ? item.getToOwnerUserId() : item.getCandidateUserId()));
        vo.setAssignmentStatusBefore(assignmentBefore(item.getActionType()));
        vo.setAssignmentStatusAfter(assignmentAfter(item.getActionType()));
        return vo;
    }

    private LeadFlowHistoryRespVO fromAging(LeadAgingPoolEventDO item, Map<Long, AdminUserRespDTO> users) {
        boolean system = item.getOperatorUserId() == null || item.getOperatorUserId() == 0;
        LeadFlowHistoryRespVO vo = base("aging:" + item.getId(), item.getOccurredAt(), "公海",
                agingLabel(item.getEventType()), system ? "系统任务" : "公海处理",
                system ? "系统" : name(users, item.getOperatorUserId()), item.getReason());
        vo.setFromOwner(name(users, item.getPreviousCollaboratorUserId()));
        vo.setToOwner(name(users, item.getCollaboratorUserId()));
        return vo;
    }

    private List<LeadFlowHistoryRespVO.AttachmentVO> attachments(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<EvidenceRef> refs;
        try { refs = JsonUtils.parseArray(json, EvidenceRef.class); } catch (Exception ignored) { return List.of(); }
        if (refs == null) return List.of();
        return refs.stream().map(ref -> {
            LeadFlowHistoryRespVO.AttachmentVO vo = new LeadFlowHistoryRespVO.AttachmentVO();
            Long fileId = ref.getInfraFileId();
            vo.setInfraFileId(fileId);
            vo.setOriginalName(firstText(ref.getOriginalName(), ref.getName()));
            vo.setContentType(firstText(ref.getContentType(), ref.getType()));
            boolean previewable = vo.getContentType() != null && (vo.getContentType().startsWith("image/")
                    || "application/pdf".equals(vo.getContentType()));
            vo.setPreviewable(previewable); vo.setAvailable(false);
            if (previewable && fileId != null) try {
                vo.setPreviewUrl(fileApi.presignGetUrl(fileId, ATTACHMENT_URL_EXPIRATION_SECONDS));
                vo.setAvailable(vo.getPreviewUrl() != null);
            } catch (Exception ignored) { vo.setAvailable(false); }
            return vo;
        }).toList();
    }

    private static LeadFlowHistoryRespVO base(String id, LocalDateTime time, String object, String node,
                                               String source, String operator, String reason) {
        LeadFlowHistoryRespVO vo = new LeadFlowHistoryRespVO();
        vo.setId(id); vo.setOccurredAt(time); vo.setBusinessObject(object); vo.setFlowNode(node);
        vo.setSource(source); vo.setOperator(operator); vo.setReason(reason); return vo;
    }
    private static void addUser(Set<Long> ids, Long id) { if (id != null && id > 0) ids.add(id); }
    private static String name(Map<Long, AdminUserRespDTO> users, Long id) {
        if (id == null || id == 0) return id != null && id == 0 ? "系统" : null;
        AdminUserRespDTO user = users.get(id); return user == null ? "未知账号" : user.getNickname();
    }
    private static Optional<Long> optionalLong(String json, String key) {
        if (json == null || json.isBlank()) return Optional.empty();
        try { return Optional.ofNullable(number(JsonUtils.parseObject(json, Map.class).get(key))); }
        catch (Exception ignored) { return Optional.empty(); }
    }
    private static Optional<String> optionalText(String json, String key) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            Object value = JsonUtils.parseObject(json, Map.class).get(key);
            return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
        } catch (Exception ignored) { return Optional.empty(); }
    }
    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private static String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
    private static Long rawId(LeadFlowHistoryRespVO item) {
        if (item.getId() == null) return Long.MIN_VALUE;
        int separator = item.getId().lastIndexOf(':');
        try { return Long.parseLong(item.getId().substring(separator + 1)); }
        catch (RuntimeException ignored) { return Long.MIN_VALUE; }
    }
    private static int sameTimeOrder(LeadFlowHistoryRespVO item) {
        return item.getId() != null && item.getId().startsWith("lead:") ? 0 : 1;
    }
    private static Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }
    private static void applyEventTransitions(LeadFlowHistoryRespVO vo, BusinessEventDO event) {
        String type = event.getEventType();
        if ("lead_appeal_overturned".equals(type)) {
            setLeadTransition(vo, "invalid", "valid");
            return;
        }
        if ("lead_appeal_submitted".equals(type) || "lead_appeal_upheld".equals(type)) return;
        if (type != null && Set.of("lead_assignment_accepted", "lead_claimed").contains(type)) {
            setAssignmentTransition(vo, event.getFromStatus(), event.getToStatus());
            return;
        }
        if ("lead_transferred".equals(type)) {
            if ("suspended".equals(event.getFromStatus())) setLeadTransition(vo, "suspended", "submitted");
            setAssignmentTransition(vo, "recycle_pending".equals(event.getFromStatus())
                    ? "recycle_pending" : "owned", "owned");
            return;
        }
        if ("lead_recycled".equals(type)) {
            setLeadTransition(vo, "suspended", "submitted");
            setAssignmentTransition(vo, "owned", "recycle_pending");
            return;
        }
        if ("lead_released_to_claim_pool".equals(type)) {
            if ("suspended".equals(event.getFromStatus())) setLeadTransition(vo, "suspended", "submitted");
            setAssignmentTransition(vo, "recycle_pending".equals(event.getFromStatus())
                    ? "recycle_pending" : "owned", "public_pool");
            return;
        }
        if (isLeadStatus(event.getFromStatus()) || isLeadStatus(event.getToStatus())) {
            setLeadTransition(vo, event.getFromStatus(), event.getToStatus());
        }
    }
    private static void setLeadTransition(LeadFlowHistoryRespVO vo, String before, String after) {
        if (Objects.equals(before, after)) return;
        vo.setLeadStatusBefore(leadStatusLabel(before));
        vo.setLeadStatusAfter(leadStatusLabel(after));
    }
    private static void setAssignmentTransition(LeadFlowHistoryRespVO vo, String before, String after) {
        if (Objects.equals(before, after)) return;
        vo.setAssignmentStatusBefore(assignmentStatusLabel(before));
        vo.setAssignmentStatusAfter(assignmentStatusLabel(after));
    }
    private static boolean isLeadStatus(String status) {
        return status != null && Set.of("submitted", "suspended", "valid", "converted", "invalid", "won", "closed").contains(status);
    }
    private static String leadStatusLabel(String status) { return Map.ofEntries(
            Map.entry("submitted", "已提交"), Map.entry("suspended", "已挂起"),
            Map.entry("valid", "有效"), Map.entry("converted", "有效"), Map.entry("invalid", "无效"),
            Map.entry("won", "已成交"), Map.entry("closed", "已关闭")
    ).get(status); }
    private static String eventSource(BusinessEventDO event, LeadAssignmentHistoryDO assignment, boolean system) {
        return assignment == null ? (system ? "系统任务" : "员工工作台") : assignmentSource(assignment);
    }
    private static String eventReason(BusinessEventDO event, LeadAssignmentHistoryDO assignment) {
        return switch (Objects.toString(event.getEventType(), "")) {
            case "lead_qualified_invalid" -> optionalText(event.getRelatedObjectRefs(), "reasonLabel").orElse(null);
            case "lead_qualified_valid", "lead_follow_up_recorded" -> null;
            default -> firstText(event.getReason(), assignment == null ? null : assignment.getReason());
        };
    }
    private static String eventRemark(BusinessEventDO event, Map<Long, LeadFollowUpRecordDO> followUps) {
        return switch (Objects.toString(event.getEventType(), "")) {
            case "lead_submitter_supplemented" -> LeadRemarkHistoryService.appendedRemark(event);
            case "lead_qualified_valid", "lead_qualified_invalid" -> event.getReason();
            case "lead_follow_up_recorded" -> optionalLong(event.getRelatedObjectRefs(), "followUpRecordId")
                    .map(followUps::get).map(LeadFollowUpRecordDO::getRemark).orElse(null);
            default -> null;
        };
    }
    private static String assignmentStatusLabel(String status) { return Map.ofEntries(
            Map.entry("unassigned", "未分配"), Map.entry("pending_acceptance", "待接单"),
            Map.entry("owned", "已归属"), Map.entry("public_pool", "抢单池"),
            Map.entry("recycle_pending", "回收待处理"), Map.entry("closed", "已关闭")
    ).get(status); }
    private static String businessObject(String type) {
        if (type != null && type.contains("appeal")) return "客资申诉";
        if (type != null && type.contains("follow_up")) return "客资跟进";
        if (type != null && (type.contains("assignment") || type.contains("assigned")
                || type.contains("transfer") || type.contains("claim") || type.contains("recycl")
                || type.contains("released"))) return "客资分配";
        return "客资";
    }
    private static String eventLabel(String type) { if (type == null) return "客资流转"; return Map.ofEntries(
            Map.entry("lead_qualified_valid", "判定有效"), Map.entry("lead_qualified_invalid", "判定无效"),
            Map.entry("lead_suspended", "客资挂起"), Map.entry("lead_released_to_claim_pool", "释放到抢单池"),
            Map.entry("lead_recycled", "主管回收"), Map.entry("lead_transferred", "主管转派"),
            Map.entry("lead_assignment_accepted", "接单"), Map.entry("lead_claimed", "抢单"),
            Map.entry("lead_follow_up_recorded", "新增跟进"), Map.entry("lead_appeal_submitted", "提交申诉"),
            Map.entry("lead_appeal_overturned", "申诉改判"), Map.entry("lead_appeal_upheld", "申诉维持原判"),
            Map.entry("lead_restored", "恢复客资"), Map.entry("lead_basic_info_updated", "修改基础信息"),
            Map.entry("lead_qualification_started", "开始有效性判定"),
            Map.entry("lead_submitter_supplemented", "提交人补充资料"),
            Map.entry("lead_submitter_assist_requested", "请求提交人协助"),
            Map.entry("lead_category_changed", "变更客资类别"),
            Map.entry("lead_duplicate_reviewed", "重复客资复核")
    ).getOrDefault(type, type); }
    private static String assignmentLabel(String type) { if (type == null) return "分配流转"; return Map.ofEntries(
            Map.entry("dispatch", "派单"), Map.entry("accept", "接单"), Map.entry("reject", "拒单"),
            Map.entry("timeout", "接单超时"), Map.entry("claim", "抢单"), Map.entry("transfer", "转派"),
            Map.entry("recycle", "回收"), Map.entry("release_to_claim_pool", "释放到抢单池"),
            Map.entry("public_pool", "释放到抢单池"), Map.entry("duplicate_review_reactivate", "重复客资复核激活")
    ).getOrDefault(type, type); }
    private static String assignmentSource(LeadAssignmentHistoryDO item) {
        if (item.getOperatorUserId() == null || item.getOperatorUserId() == 0) return "系统任务";
        if ("dispatch".equals(item.getActionType())) {
            return item.getAssignmentRuleId() == null ? "指定派单" : "自动分配";
        }
        return "员工工作台";
    }
    private static String assignmentBefore(String type) { return "dispatch".equals(type) ? "未分配" : null; }
    private static String assignmentAfter(String type) { if (type == null) return null; return Map.ofEntries(
            Map.entry("dispatch", "待接单"), Map.entry("accept", "已归属"), Map.entry("claim", "已归属"),
            Map.entry("reject", "待重新分配"), Map.entry("timeout", "待重新分配"), Map.entry("transfer", "已归属"),
            Map.entry("recycle", "回收待处理"), Map.entry("release_to_claim_pool", "抢单池"),
            Map.entry("public_pool", "抢单池"), Map.entry("duplicate_review_reactivate", "未分配")
    ).getOrDefault(type, null); }
    private static String agingLabel(String type) { if (type == null) return "公海流转"; return Map.ofEntries(
            Map.entry("entered", "进入公海"), Map.entry("assigned", "分配协作人"),
            Map.entry("reassigned", "变更协作人"), Map.entry("exited", "退出公海"),
            Map.entry("collaborator_cleared", "清除协作人"), Map.entry("deal_pending", "提交成交"),
            Map.entry("converted", "商机成交")
    ).getOrDefault(type, type); }

    @Data
    private static class EvidenceRef {
        private Long infraFileId;
        private String originalName;
        private String contentType;
        private String name;
        private String type;
    }
}
