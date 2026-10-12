package cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ConversionDetailVO {
    private ConversionDetailVO() {}

    @Data @EqualsAndHashCode(callSuper = true)
    public static class Query extends PerformanceVO.Query {
        @Pattern(regexp = "converted|unconverted") private String conversionStatus;
        @Pattern(regexp = "current|previous") private String populationKind;
        @Size(max = 2048) private String categoryKey;
        @Size(max = 2048) private String channelKey;
        @Size(max = 4096) private String productKey;
        @Size(max = 100) private String keyword;
    }

    public record Option(String key, String label, long count) {}
    public record Options(List<Option> conversionStatus, List<Option> populationKind,
                          List<Option> categories, List<Option> channels, List<Option> products) {}
    public record Product(String key, String ref, String label, String snapshot) {}
    public record Order(Long id, String number, LocalDateTime submittedAt, BigDecimal amount, List<Product> products) {}
    public record Row(Long leadId, String leadNo, String categoryKey, String categoryLabel,
                      String channelKey, String channelLabel, String populationKind,
                      LocalDateTime receivedAt, boolean converted, Order order) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Summary(Long converted, Long denominator, BigDecimal rate, String sourceStatus, long sourceReviewCount) {
        public Summary(long converted, long denominator, BigDecimal rate) { this(converted, denominator, rate, "ready", 0); }
    }
    public record Response(List<Row> list, long total, LocalDateTime asOf, LocalDateTime start,
                           LocalDateTime end, Summary summary, Options options) {}
}
