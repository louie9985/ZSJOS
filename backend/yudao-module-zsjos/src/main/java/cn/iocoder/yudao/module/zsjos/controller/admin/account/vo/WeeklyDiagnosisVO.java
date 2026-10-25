package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

/** Weekly nodes are commands over existing diagnosis tasks, not a second task system. */
public final class WeeklyDiagnosisVO {
    private WeeklyDiagnosisVO() {}
    public record Part(String templateType, int cycle, int days, LocalDateTime from, LocalDateTime to,
                       Long taskId, Metric metric) {}
    public record Node(String key, String roundKey, int week, LocalDateTime dueAt, List<Part> parts,
                       List<Long> taskIds) {}
    public record Overview(List<MediaAccountProfileVO.DiagnosisTodo> tasks, List<Node> nodes) {}
    @Data public static class Metric {
        private String state;
        private String reason;
        private LocalDateTime asOf;
        private LocalDateTime settledAt;
        private Long publishedCount;
        private Long missingPublishTimeCount;
        private Long validLeads;
        private Long deals;
        private BigDecimal conversionRate;
        private Long highestLikes;
        private Integer likesCoverage;
        private String publicationExplanation = "按本时间段内预计发布且已正式提交审核的作品去重统计，不代表实际发布数量。";
        private String conversionExplanation = "学员绑定兼职账号维度，未按媒体账号拆分。有效客资按本周期提交、周期截止时有效的客资统计；成交数为这批客资截至当前已成交的数量，提交后仍会更新。";
    }
    @Data public static class Submit {
        @NotBlank private String nodeKey;
        @NotNull @Valid private MediaAccountProfileVO.DiagnosisRequest common;
        @NotBlank private String changeDirection;
        @NotBlank @Size(max=200) private String directionSummary;
        @NotBlank private String trafficNormal;
        @PositiveOrZero private Long highestLikes;
        @NotBlank private String metricToken;
    }
    public record Preview(Node node, String metricToken) {}
}
