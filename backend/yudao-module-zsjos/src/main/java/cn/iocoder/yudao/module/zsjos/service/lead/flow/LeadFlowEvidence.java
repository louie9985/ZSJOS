package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Freezes evidence at the successful operation, never reconstructing an older operation from current data. */
@Service
public class LeadFlowEvidence {
    @Resource private LeadAttachmentMapper attachmentMapper;
    @Resource private LeadFollowUpImageMapper imageMapper;
    @Resource private LeadFollowUpRecordMapper followUpMapper;
    @Resource private LeadComplaintMapper complaintMapper;
    @Resource private LeadSubmitterFeedbackMapper feedbackMapper;
    @Resource private LeadSubmitterFeedbackAttachmentMapper feedbackAttachmentMapper;
    @Resource private LeadSubmitterAssistRequestMapper assistMapper;
    @Resource private LeadDuplicateReviewMapper reviewMapper;

    public record Evidence(String remark, List<Map<String,Object>> attachments) {}
    public Evidence capture(Long leadId, String type, Long id, String title, Collection<cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO> events, boolean created) {
        List<String> remarks=new ArrayList<>();
        List<Map<String,Object>> files=new ArrayList<>();
        for (var event:events) {
            if ("lead_flow_operation".equals(event.getEventType()) && event.getRelatedObjectRefs()!=null) {
                Object remark=JsonUtils.parseObject(event.getRelatedObjectRefs(),Map.class).get("flowRemark");
                if (remark instanceof String text) remarks.add(text);
            }
            addFiles(files,event.getEvidenceRefs());
            if ("lead_submitter_supplemented".equals(event.getEventType())) {
                String remark=cn.iocoder.yudao.module.zsjos.service.lead.LeadRemarkHistoryService.appendedRemark(event);
                if (remark!=null) remarks.add(remark);
            }
            if ("lead_follow_up_recorded".equals(event.getEventType()) && event.getRelatedObjectRefs()!=null) {
                Object recordId=JsonUtils.parseObject(event.getRelatedObjectRefs(),Map.class).get("followUpRecordId");
                if(recordId instanceof Number number) {
                    var record=followUpMapper.selectById(number.longValue());
                    if(record!=null && record.getRemark()!=null) remarks.add(record.getRemark());
                    imageMapper.selectListByRecordIds(List.of(number.longValue())).forEach(image -> files.add(file(image.getInfraFileId(),image.getOriginalName(),image.getContentType())));
                }
            }
        }
        if(created) attachmentMapper.selectListByLeadId(leadId).forEach(attachment -> files.add(file(attachment.getInfraFileId(),attachment.getOriginalName(),attachment.getContentType())));
        if(id!=null) switch(Objects.toString(type,"lead")) {
            case "complaint" -> {
                var row=complaintMapper.selectById(id);
                if(row!=null) {
                    boolean decision="handled".equals(row.getStatus());
                    remarks.add(decision?row.getHandlerOpinion():row.getReason());
                    addFiles(files,decision?row.getHandlerEvidenceRefs():row.getEvidenceRefs());
                }
            }
            case "feedback" -> {
                var row=feedbackMapper.selectById(id);
                if(row!=null) {
                    remarks.add(row.getFeedback());
                    feedbackAttachmentMapper.listByFeedback(id).forEach(attachment -> files.add(file(attachment.getFileId(),attachment.getOriginalName(),attachment.getContentType())));
                }
            }
            case "assist" -> {
                var row=assistMapper.selectById(id);
                if(row!=null) {
                    boolean reply="completed".equals(row.getStatus());
                    remarks.add(reply?row.getResponseRemark():row.getRemark());
                    addFiles(files,reply?row.getResponseAttachmentSnapshotsJson():row.getAttachmentSnapshotsJson());
                }
            }
            case "review" -> {
                var row=reviewMapper.selectById(id);
                if(row!=null) { remarks.add(row.getReviewOpinion()); addFiles(files,row.getReviewAttachments()); }
            }
            default -> { }
        }
        String remark=String.join("\n",remarks.stream().filter(Objects::nonNull).filter(text -> !text.isBlank()).distinct().toList());
        Map<String,Map<String,Object>> unique=new LinkedHashMap<>();
        for (var file:files) unique.put(Objects.toString(file.get("infraFileId"),JsonUtils.toJsonString(file)),file);
        return new Evidence(remark.isBlank()?null:remark,List.copyOf(unique.values()));
    }
    private static Map<String,Object> file(Long id,String name,String type) {
        var result=new LinkedHashMap<String,Object>();result.put("infraFileId",id);result.put("originalName",name);result.put("contentType",type);return result;
    }
    @SuppressWarnings("unchecked")
    private static void addFiles(List<Map<String,Object>> files,String json) {
        if(json==null || json.isBlank()) return;
        List<?> rows=JsonUtils.parseArray(json,Map.class);
        if(rows!=null) rows.forEach(row -> { if(row instanceof Map<?,?> map) files.add((Map<String,Object>)map); });
    }
}
