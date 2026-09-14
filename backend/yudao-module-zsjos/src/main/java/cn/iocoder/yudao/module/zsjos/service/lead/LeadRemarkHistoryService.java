package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadRemarkRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadRemarkAttachmentRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LeadRemarkHistoryService {
    @Resource private BusinessEventMapper eventMapper;

    public History get(LeadDO lead, String submitterName, boolean masked) {
        return project(lead, eventMapper.selectByLeadId(lead.getId()), submitterName, masked, List.of());
    }

    public History get(LeadDO lead, String submitterName, boolean masked, List<LeadAttachmentDO> initialAttachments) {
        return project(lead, eventMapper.selectByLeadId(lead.getId()), submitterName, masked, initialAttachments, Map.of());
    }

    /** Signed URLs are keyed by the attachment row id, which is what the initial-submission rows are keyed by. */
    public History get(LeadDO lead, String submitterName, boolean masked, List<LeadAttachmentDO> initialAttachments,
                       Map<Long, String> signedUrls) {
        return project(lead, eventMapper.selectByLeadId(lead.getId()), submitterName, masked, initialAttachments, signedUrls);
    }

    public static History project(LeadDO lead, List<BusinessEventDO> events, String submitterName, boolean masked) {
        return project(lead, events, submitterName, masked, List.of());
    }

    public static History project(LeadDO lead, List<BusinessEventDO> events, String submitterName, boolean masked,
                                  List<LeadAttachmentDO> initialAttachments) {
        return project(lead, events, submitterName, masked, initialAttachments, Map.of());
    }

    public static History project(LeadDO lead, List<BusinessEventDO> events, String submitterName, boolean masked,
                                  List<LeadAttachmentDO> initialAttachments, Map<Long, String> signedUrls) {
        List<BusinessEventDO> supplements = events.stream()
                .filter(e -> LeadSupplementSnapshot.EVENT.equals(e.getEventType()))
                .sorted(Comparator.comparing(BusinessEventDO::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BusinessEventDO::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<LeadRemarkRespVO> appended = new ArrayList<>();
        Map<String, LeadRemarkRespVO> legacy = new LinkedHashMap<>();
        Map<String, List<String>> evidence = new LinkedHashMap<>();
        boolean hasLegacy = false, incomplete = false;
        for (BusinessEventDO event : supplements) {
            Map<?, ?> payload = payload(event);
            if (payload == null) { hasLegacy = true; incomplete = true; continue; }
            if (LeadSupplementSnapshot.MODE.equals(payload.get("remarkMode"))) {
                Object content = payload.get("remark");
                if (!payload.containsKey("remark") || (content != null && !(content instanceof String))) incomplete = true;
                if (content instanceof String text && !text.isBlank()) {
                    String name = masked ? submitterName : text(payload.get("submitterName"));
                    appended.add(new LeadRemarkRespVO("event:" + event.getId(), "supplement", text,
                            event.getOccurredAt(), name, attachments(event.getEvidenceRefs())));
                }
            } else {
                hasLegacy = true;
                if (payload.containsKey("remarkMode")) incomplete = true;
                Object before = payload.get("before");
                if (before instanceof Map<?, ?> values && values.containsKey("remark")
                        && (values.get("remark") == null || values.get("remark") instanceof String)) {
                    addLegacy(legacy, evidence, text(values.get("remark")), "event:" + event.getId());
                } else incomplete = true;
            }
        }
        List<LeadRemarkRespVO> result = new ArrayList<>();
        // The initial submission leads the stream; legacy material is retained without fabricated metadata.
        if (hasLegacy) {
            addLegacy(legacy, evidence, lead.getRemark(), "lead:" + lead.getId());
            result.addAll(legacy.values());
        } else if ((lead.getRemark() != null && !lead.getRemark().isBlank()) || !initialAttachments.isEmpty()) {
            result.add(new LeadRemarkRespVO("lead:" + lead.getId(), "submission", lead.getRemark(),
                    lead.getSubmittedAt() == null ? lead.getCreateTime() : lead.getSubmittedAt(), submitterName,
                    initialAttachments.stream().map(file -> attachment(file, signedUrls)).toList()));
        }
        result.addAll(appended);
        return new History(List.copyOf(result), incomplete, hasLegacy, evidence);
    }

    private static List<LeadRemarkAttachmentRespVO> attachments(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            @SuppressWarnings("unchecked") List<Map<String, Object>> refs = (List<Map<String, Object>>) (List<?>) JsonUtils.parseArray(json, Map.class);
            if (refs == null) return List.of();
            return refs.stream().map(ref -> new LeadRemarkAttachmentRespVO(
                    number(ref.get("infraFileId")), text(ref.get("url")), text(ref.get("name")), text(ref.get("type")), number(ref.get("size")))).toList();
        } catch (RuntimeException ignored) { return List.of(); }
    }

    private static LeadRemarkAttachmentRespVO attachment(LeadAttachmentDO item) {
        return attachment(item, Map.of());
    }

    private static LeadRemarkAttachmentRespVO attachment(LeadAttachmentDO item, Map<Long, String> signedUrls) {
        String signed = item.getId() == null ? null : signedUrls.get(item.getId());
        return new LeadRemarkAttachmentRespVO(item.getInfraFileId(), signed != null ? signed : item.getFileUrl(),
                item.getOriginalName(), item.getContentType(), item.getFileSize());
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static void addLegacy(Map<String, LeadRemarkRespVO> rows, Map<String, List<String>> evidence,
                                  String content, String source) {
        if (content == null || content.isBlank()) return;
        LeadRemarkRespVO row = rows.computeIfAbsent(content,
                key -> new LeadRemarkRespVO("legacy:" + source, "legacy", key, null, null, List.of()));
        evidence.computeIfAbsent(row.id(), key -> new ArrayList<>()).add(source);
    }

    public static Map<?, ?> payload(BusinessEventDO event) {
        try {
            return JsonUtils.parseObjectQuietly(event.getRelatedObjectRefs(), Map.class);
        } catch (RuntimeException ignored) { return null; }
    }

    public static String appendedRemark(BusinessEventDO event) {
        Map<?, ?> payload = payload(event);
        return payload != null && LeadSupplementSnapshot.MODE.equals(payload.get("remarkMode"))
                ? text(payload.get("remark")) : null;
    }

    private static String text(Object value) { return value instanceof String s ? s : null; }

    // Evidence references stay internal: clients receive only the safe remark projection.
    public record History(List<LeadRemarkRespVO> items, boolean incomplete, boolean hasLegacy,
                          Map<String, List<String>> evidence) {}
}
