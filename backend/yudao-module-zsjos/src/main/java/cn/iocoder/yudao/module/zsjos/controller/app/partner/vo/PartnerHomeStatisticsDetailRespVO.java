package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PartnerHomeStatisticsDetailRespVO {
    private String period;
    private String metric;
    private List<Object> list;
    private Long total;
    private BigDecimal totalAmount;

    @Data
    public static class TimelineItem {
        private String id;
        private String title;
        private String description;
        private LocalDateTime occurredAt;
    }

    @Data
    public static class LeadItem {
        private String kind = "lead";
        private Long id;
        private String leadNo;
        private String submittedName;
        private String status;
        private String courseName;
        private LocalDateTime submittedAt;
        private String sourceLabel;
        private String mobileMasked;
        private String location;
        private List<TimelineItem> timeline;
    }

    @Data
    public static class WithdrawalItem {
        private String kind = "withdrawal";
        private Long id;
        private String withdrawalNo;
        private String status;
        private BigDecimal applicationAmount;
        private BigDecimal approvedAmount;
        private LocalDateTime submittedAt;
        private LocalDateTime paidAt;
        private String accountNameSnapshot;
        private String bankNameSnapshot;
        private String maskedCardNumber;
    }
}
