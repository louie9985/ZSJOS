package cn.iocoder.yudao.module.zsjos.service.homemetric;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** Resolves once per evaluation; the same half-open intervals travel to drill-down queries. */
public final class MetricPeriod {
    private MetricPeriod() {}
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public enum Kind { ALL, DAY, WEEK, MONTH, QUARTER, YEAR, CUSTOM }
    public enum Comparison { CURRENT, PREVIOUS, YEAR_AGO }
    public record Range(
            @tools.jackson.databind.annotation.JsonSerialize(using=MetricDateTimeJson.Serializer.class)
            @tools.jackson.databind.annotation.JsonDeserialize(using=MetricDateTimeJson.Deserializer.class) LocalDateTime start,
            @tools.jackson.databind.annotation.JsonSerialize(using=MetricDateTimeJson.Serializer.class)
            @tools.jackson.databind.annotation.JsonDeserialize(using=MetricDateTimeJson.Deserializer.class) LocalDateTime end) {
        public Range {
            if (start == null || end == null || start.isAfter(end))
                throw new IllegalArgumentException("统计区间必须有序");
            if(start.getYear()<1000 || end.getYear()>9999)
                throw new IllegalArgumentException("统计区间超出数据库日期范围");
            // HTTP and Redis preserve millisecond boundaries; calculations use the same precision.
            start=start.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            end=end.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        }
    }

    public static Range resolve(Kind kind, boolean toDate, Instant now, Range custom) {
        if (kind == null || now == null) throw new IllegalArgumentException("统计周期不能为空");
        if (kind == Kind.ALL) {
            if (toDate || custom != null) throw new IllegalArgumentException("不限时间不能携带周期边界");
            return null;
        }
        if (kind == Kind.CUSTOM) {
            if (custom == null || toDate || custom.start().equals(custom.end()))
                throw new IllegalArgumentException("自定义周期需要非空固定起止时间");
            return custom;
        }
        LocalDateTime local = LocalDateTime.ofInstant(now, ZONE);
        LocalDate date = local.toLocalDate();
        LocalDate start = switch (kind) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case QUARTER -> LocalDate.of(date.getYear(), ((date.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case YEAR -> date.withDayOfYear(1);
            default -> throw new IllegalStateException();
        };
        LocalDateTime end = shift(start.atStartOfDay(), kind, 1);
        // At midnight, a to-date range is legitimately empty rather than an invalid configuration.
        return new Range(start.atStartOfDay(), toDate ? local : end);
    }

    public static Range compare(Range current, Kind kind, Comparison comparison) {
        if (kind == Kind.ALL && comparison == Comparison.CURRENT && current == null) return null;
        if (current == null || kind == null || comparison == null) throw new IllegalArgumentException("比较周期不能为空");
        return switch (comparison) {
            case CURRENT -> current;
            case YEAR_AGO -> {
                LocalDateTime start = current.start().minusYears(1);
                // A full leap-day interval must stay a full day, not collapse after date clamping.
                boolean full = kind != Kind.CUSTOM && current.end().equals(shift(current.start(), kind, 1));
                yield new Range(start, full ? shift(start, kind, 1) : current.end().minusYears(1));
            }
            case PREVIOUS -> kind == Kind.CUSTOM
                    ? new Range(current.start().minus(Duration.between(current.start(), current.end())), current.start())
                    : new Range(shift(current.start(), kind, -1), shift(current.end(), kind, -1));
        };
    }

    private static LocalDateTime shift(LocalDateTime value, Kind kind, int amount) {
        return switch (kind) {
            case DAY -> value.plusDays(amount);
            case WEEK -> value.plusWeeks(amount);
            case MONTH -> value.plusMonths(amount);
            case QUARTER -> value.plusMonths(amount * 3L);
            case YEAR -> value.plusYears(amount);
            case CUSTOM, ALL -> throw new IllegalArgumentException("该周期不能按自然周期偏移");
        };
    }
}
