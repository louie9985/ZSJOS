package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RegistrationCaseRespVO {
    private Long id;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private String orderStatusLabel;
    private String studentName;
    private String studentMobile;
    private String leadNo;
    private String status;
    private String statusLabel;
    private Long studyPlannerUserId;
    private String studyPlannerUserName;
    private LocalDateTime registrationApprovedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Integer version;
    private Boolean completable;
    private String completionBlockCode;
    private String completionBlockReason;
    private List<ItemVO> items;
    private List<RouteVO> routes;

    @Data
    public static class ItemVO {
        private Long id;
        private String itemKey;
        private String itemType;
        private String title;
        private Integer sort;
        private Boolean checked;
        private Long checkedByUserId;
        private String checkedByUserName;
        private LocalDateTime checkedAt;
        private Boolean attachmentRequired;
        private List<AttachmentVO> attachments;
    }

    @Data
    public static class AttachmentVO {
        private Long id;
        private Long infraFileId;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private Long uploadedByUserId;
        private String uploadedByUserName;
        private LocalDateTime uploadedAt;
    }

    @Data
    public static class RouteVO {
        private Long id;
        private String optionKey;
        private Long departmentId;
        private String departmentName;
        private String assigneeType;
        private String assigneeTypeLabel;
        private Boolean selected;
        private Long assigneeUserId;
        private String assigneeUserName;
        private Integer sort;
    }
}
