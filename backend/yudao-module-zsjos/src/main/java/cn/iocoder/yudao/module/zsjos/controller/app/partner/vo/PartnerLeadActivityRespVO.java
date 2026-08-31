package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PartnerLeadActivityRespVO {
    private CurrentStatus currentStatus;
    private List<FollowUpItem> followUps;
    private List<TimelineItem> timeline;
    private List<CashbackItem> cashbackItems;
    private List<ComplaintItem> complaints;
    private List<OrderItem> orders;

    @Data
    public static class CurrentStatus {
        private String code;
        private String text;
        private String description;
        private String tone;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class TimelineItem {
        private String id;
        private String type;
        private String title;
        private String description;
        private LocalDateTime occurredAt;
        private String tone;
        private Boolean current;
    }

    @Data
    public static class FollowUpItem {
        private Long id;
        private Long leadId;
        private Long assignmentHistoryId;
        private LocalDateTime occurredAt;
        private Boolean firstInAssignment;
        private String result;
        private String resultLabel;
        private String method;
        private String methodLabel;
        private String nextFollowUpAt;
        private List<Object> images;
    }

    @Data
    public static class CashbackItem {
        private Long id;
        private String typeText;
        private String statusText;
        private BigDecimal amount;
        private LocalDateTime availableAt;
    }

    @Data
    public static class ComplaintItem {
        private Long id;
        private String recordNo;
        private String status;
        private String statusText;
        private String content;
        private String result;
        private LocalDateTime createdAt;
        private List<Object> attachments;
    }

    @Data
    public static class OrderItem {
        private Long id;
        private String orderNo;
        private String status;
        private String statusText;
        private String purchaseTypeText;
        private BigDecimal totalAmount;
        private LocalDateTime createdAt;
    }
}
