package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.flow;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadFlowHistoryRespVO {
    private String id;
    private LocalDateTime occurredAt;
    private String businessObject;
    private String flowNode;
    private String source;
    private String operator;
    private String fromOwner;
    private String toOwner;
    private String leadStatusBefore;
    private String leadStatusAfter;
    private String assignmentStatusBefore;
    private String assignmentStatusAfter;
    private String reason;
    private String remark;
    private Integer snapshotVersion;
    private String operationResult;
    private String relatedType;
    private Long relatedId;
    private String relatedNo;
    private List<FieldChangeVO> changes = List.of();
    private List<AttachmentVO> attachments = List.of();

    public record FieldValueVO(String state, String text) {}
    public record FieldChangeVO(String field, String label, FieldValueVO before, FieldValueVO after) {}

    @Data
    public static class AttachmentVO {
        private Long infraFileId;
        private String originalName;
        private String contentType;
        private String previewUrl;
        private Boolean previewable;
        private Boolean available;
    }
}
