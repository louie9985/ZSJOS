package cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FeedbackRespVO {

    private Long id;
    private String feedbackType;
    private String feedbackNo;
    private String title;
    private String status;
    private Long submitterUserId;
    private String submitterName;
    private Long assigneeUserId;
    private String assigneeName;
    private String latestReplySummary;
    private LocalDateTime lastActivityAt;
    private Boolean unread;
    private Integer version;
    private LocalDateTime createTime;
    private Boolean canResubmit;
    private Boolean canReply;
    private Boolean canComplete;
    private Boolean canSurvey;
    private Boolean canSubmitSurvey;

    private Long formId;
    private List<FeedbackFormRespVO.Field> fields;
    private Map<String, Object> values;
    private String supportTypeValue;
    private String supportTypeLabel;
    private String processInstanceId;
    private Integer approvalRoundNo;
    private String rejectReason;
    private String completedResult;
    private List<Long> resultAttachmentIds;
    private List<Attachment> resultAttachments;
    private List<Reply> replies;
    private Survey survey;

    @Data
    public static class Reply {
        private Long id;
        private Long authorUserId;
        private String authorName;
        private String authorType;
        private String content;
        private List<Long> attachmentIds;
        private List<Attachment> attachments;
        private LocalDateTime createTime;
    }

    @Data
    public static class Attachment {
        private Long id;
        private String name;
        private String type;
        private Long size;
        private String url;
    }

    @Data
    public static class Survey {
        private String status;
        private Long formId;
        private List<FeedbackFormRespVO.Field> fields;
        private Map<String, Object> values;
        private LocalDateTime requestedAt;
        private LocalDateTime submittedAt;
    }

    @Data
    public static class Portal {
        private List<Entry> entries;
        private List<FeedbackRespVO> recent;
    }

    @Data
    public static class Entry {
        private String feedbackType;
        private String title;
        private String description;
        private Boolean open;
        private String unavailableReason;
    }

    @Data
    public static class FileUpload {
        private Long id;
        private String name;
        private String type;
        private Long size;
        private String url;
    }
}
