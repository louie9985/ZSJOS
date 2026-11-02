package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_INVALID;

/** Technical date-expression contract; values remain expressions in saved templates. */
final class RelativeTimeOperand {
    static Object resolve(Object raw, LocalDateTime now) {
        if (!(raw instanceof Map<?, ?> value)) return raw;
        try {
            if (!value.keySet().equals(Set.of("kind", "base", "offset", "unit"))
                    || !"relative".equals(value.get("kind"))) throw new IllegalArgumentException();
            int offset = new BigDecimal(String.valueOf(value.get("offset"))).intValueExact();
            if (Math.abs((long) offset) > 10000) throw new IllegalArgumentException();
            LocalDate today = now.toLocalDate();
            LocalDateTime base = switch (String.valueOf(value.get("base"))) {
                case "now" -> now;
                case "today_start" -> today.atStartOfDay();
                case "week_start" -> today.with(DayOfWeek.MONDAY).atStartOfDay();
                case "month_start" -> today.withDayOfMonth(1).atStartOfDay();
                case "quarter_start" -> LocalDate.of(today.getYear(), (today.getMonthValue()-1)/3*3+1, 1).atStartOfDay();
                case "year_start" -> today.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay();
                default -> throw new IllegalArgumentException();
            };
            return switch (String.valueOf(value.get("unit"))) {
                case "minute" -> base.plusMinutes(offset);
                case "hour" -> base.plusHours(offset);
                case "day" -> base.plusDays(offset);
                case "week" -> base.plusWeeks(offset);
                case "month" -> base.plusMonths(offset);
                case "year" -> base.plusYears(offset);
                default -> throw new IllegalArgumentException();
            };
        } catch (IllegalArgumentException | ArithmeticException | DateTimeException e) {
            throw exception(ADVANCED_FILTER_INVALID);
        }
    }
}
