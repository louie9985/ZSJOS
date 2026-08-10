package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadFollowUpRespVO {
    private Long id;
    private Long leadId;
    private Long assignmentHistoryId;
    private Long operatorUserId;
    private String operatorName;
    private LocalDateTime occurredAt;
    private Boolean firstInAssignment;
    private String method;
    private String methodLabel;
    private String result;
    private String resultLabel;
    private String categoryBefore;
    private String categoryBeforeLabel;
    private String categoryAfter;
    private String categoryAfterLabel;
    private String remark;
    private LocalDateTime nextFollowUpAt;
    private List<ImageVO> images;

    @Data
    public static class ImageVO {
        private Long infraFileId;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private Integer sort;
        private String url;
    }
}
