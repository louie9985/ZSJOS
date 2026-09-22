package cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class PerformanceVO {
 private PerformanceVO() {}
 @Data public static class Query {
  @Pattern(regexp="SELF|USER|DEPT|CENTER") private String scopeType="SELF";
  private Long scopeId;
  @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) private LocalDate start;
  @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) private LocalDate end;
  @Pattern(regexp="day|week|month") private String grain="day";
  @Min(2000) @Max(2100) private Integer year;
  @Min(1) private Integer pageNo=1;
  @Min(1) @Max(100) private Integer pageSize=20;
  @Pattern(regexp="orders|leads|valid|conversion|tasks|accept|qualification|todayFollowUp|overdueFollowUp|followUps|assigned|missed") private String metric="orders";
  @Pattern(regexp="today|week|lastWeek|month|lastMonth|quarter|year|last7|last30|last60|last90") private String periodKey;
  @Pattern(regexp="source|product|contributor|category|stage") private String dimension;
  @Size(max=512) private String groupKey;
  private boolean calendar;
  private boolean cumulative;
 }
 public record Organization(Long deptId,Long centerId,String kind) {}
 public record Revision(Long id,String reason,String before,String after,String operatorName,LocalDateTime at) {}
 public record Node(String key,String parentKey,String title,String scopeType,Long scopeId,boolean selectable) {}
 public record Metric(String key,String label,LocalDateTime start,LocalDateTime end,BigDecimal amount,long orders,
   long converted,long denominator,BigDecimal rate,BigDecimal average) {}
 public record Group(String key,String label,BigDecimal amount,long count,BigDecimal share) {}
 public record Target(Long id,String scopeType,Long scopeId,String name,String periodType,LocalDate periodStart,
   BigDecimal automaticFloor,BigDecimal automaticSprint,BigDecimal floorAmount,BigDecimal sprintAmount,
   boolean manual,boolean complete,int missing,Integer version) {}
 public record TargetProgress(String key,String label,Metric actual,Target target) {}
 public record Overview(LocalDateTime asOf,LocalDateTime attributionAvailableSince,List<TargetProgress> targets,List<Metric> performance,List<Metric> conversion,
   Map<String,Long> pending,long missingAttributionOrders,BigDecimal missingAttributionAmount,boolean canDetail) {}
 public record Analysis(LocalDateTime asOf,LocalDate start,LocalDate end,List<Metric> averages,List<Metric> trend,
   List<Group> sources,List<Group> products,List<Group> contributors,List<Contribution> contributionMetrics,Map<String,List<Metric>> averageTrends,Target target) {}
 public record Contribution(Long userId,String name,Metric actual,BigDecimal floorRate,BigDecimal sprintRate,BigDecimal share) {}
 public record CalendarDay(LocalDate date,long received,long valid,long invalid,long pending,long overdue,long ended,
   long lateCompleted,long onTime,long dueCount,long unknown) {}
 public record LeadReport(LocalDateTime asOf,LocalDate start,LocalDate end,Map<String,Long> workload,List<Group> categories,List<Group> stages,
   List<CalendarDay> calendar,List<Group> funnel,List<Group> followUp,List<Group> categoryTrend) {}
 public record Detail(Long id,String number,String kind,String label,LocalDateTime occurredAt,BigDecimal amount,String state) {}
 public record HistoryMonth(int month,BigDecimal amount,BigDecimal previousAmount) {}
 @Data public static class TargetEdit {
  private Long id;
  @NotNull @Pattern(regexp="USER|DEPT|CENTER") private String scopeType;
  @NotNull private Long scopeId;
  @NotNull @Pattern(regexp="week|month") private String periodType;
  @NotNull private LocalDate periodStart;
  @DecimalMin("0.00") @Digits(integer=12,fraction=2) private BigDecimal floorAmount;
  @DecimalMin("0.00") @Digits(integer=12,fraction=2) private BigDecimal sprintAmount;
  private boolean restoreAutomatic;
  @NotBlank @Size(max=500) private String reason;
  private Integer version;
 }
 @Data public static class TargetBatch { @NotEmpty @Size(max=200) @Valid private List<TargetEdit> items; }
 @Data public static class OrgEdit {
  @NotNull private Long deptId; @NotNull private Long centerId;
  @NotNull @Pattern(regexp="DEPT|CENTER") private String kind;
 }
}
