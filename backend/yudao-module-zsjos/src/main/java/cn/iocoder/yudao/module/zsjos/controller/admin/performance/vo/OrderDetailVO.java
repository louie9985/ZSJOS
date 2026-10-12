package cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDetailVO {
    private OrderDetailVO() {}

    @Data @EqualsAndHashCode(callSuper = true)
    public static class Query extends PerformanceVO.Query {
        @Size(max = 4096) private String sourceKey;
        @Size(max = 4096) private String orderTypeKey;
        @Size(max = 4096) private String categoryKey;
        @Size(max = 4096) private String channelKey;
        @Size(max = 4096) private String productKey;
        @Size(max = 100) private String keyword;
    }

    public record Summary(long orders, BigDecimal amount) {}
    public record Option(String key, String label, long count) {}
    public record Options(List<Option> sources, List<Option> orderTypes, List<Option> categories,
                          List<Option> channels, List<Option> products) {}
    public record Product(String key, String ref, String label) {}
    public record Row(Long id, String number, Long leadId, String leadNo, String sourceLabel,
                      String orderTypeLabel, String categoryLabel, String channelLabel,
                      LocalDateTime submittedAt, BigDecimal amount, BigDecimal totalAmount, List<Product> products) {}
    public record Response(List<Row> list, long total, LocalDateTime asOf, LocalDateTime start,
                           LocalDateTime end, String amountKind, Summary summary, Summary filteredSummary, Options options) {}
}
