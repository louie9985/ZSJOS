package cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class MediaLeadVO {
    private MediaLeadVO() {}

    public record Query(@Pattern(regexp = "SELF|USER|DEPT|CENTER") String scopeType,
                        Long scopeId, @NotNull LocalDate start, @NotNull LocalDate end) {}

    @lombok.Data
    public static class DetailPageQuery extends cn.iocoder.yudao.framework.common.pojo.PageParam {
        @NotNull @Pattern(regexp = "SELF|USER|DEPT|CENTER") private String scopeType = "SELF";
        private Long scopeId;
        @NotNull @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        private LocalDate start;
        @NotNull @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        private LocalDate end;
        public Query scopeQuery() { return new Query(scopeType, scopeId, start, end); }
    }

    public record ScopeNode(String key, String parentKey, String title, String scopeType, Long scopeId) {}

    public record Target(Long id, String scopeType, Long scopeId, String name, LocalDate periodStart,
                         Integer targetCount, Integer actualCount, boolean manual, Integer version) {}

    public record TargetEdit(@NotNull @Pattern(regexp = "USER|DEPT|CENTER") String scopeType,
                             @NotNull Long scopeId, @NotNull LocalDate periodStart,
                             @Min(0) Integer targetCount, Integer version,
                             @NotBlank @Size(max = 500) String reason, boolean restoreAutomatic) {}
    public record TargetRevision(Long id, Long targetId, String beforeJson, String afterJson,
                                 String reason, Long operatorId, java.time.LocalDateTime createTime) {}
    public record Detail(String leadNo, java.time.LocalDateTime submittedAt, String contributorName,
                         String status, String statusLabel, String channelLabel, String categoryLabel,
                         boolean converted, java.time.LocalDateTime orderEffectiveAt) {}
    public record OrgEdit(@NotNull Long deptId, @NotNull Long centerId,
                          @NotNull @Pattern(regexp = "CENTER") String kind, Integer version) {}
    public record OrgUnset(@NotNull Long deptId, @NotNull Integer version) {}
    public record Org(Long deptId, Long centerId, String kind, String name, String centerName, Integer version) {}
    public record DeptOption(Long id, String name, Long parentId) {}

    public record PeriodStats(String key, String label, long total, long valid, long invalid,
                              long converted, java.math.BigDecimal validRate, java.math.BigDecimal convertedRate) {}

    public record Member(Long userId, String name, Integer targetCount,
                         long yesterday, long yesterdayConverted, long today, long todayConverted,
                         long week, long weekValid, long weekConverted, long lastWeek, long lastWeekValid,
                         long lastWeekConverted, long month, long monthValid, long monthConverted,
                         long lastMonth, long lastMonthValid, long lastMonthConverted,
                         java.math.BigDecimal monthProgress) {}

    public record CalendarDay(LocalDate date, long total, long valid, long invalid, long pending) {}
    public record Funnel(long submitted, long valid, long converted) {}
    public record Group(String label, long count) {}
    public record Overview(LocalDate asOf, Target target, List<PeriodStats> periods,
                           List<Member> members, List<CalendarDay> calendar, Funnel funnel,
                           List<Group> currentMonthChannels, List<Group> lastMonthChannels,
                           List<Group> currentMonthCategories, List<Group> lastMonthCategories) {}
}
