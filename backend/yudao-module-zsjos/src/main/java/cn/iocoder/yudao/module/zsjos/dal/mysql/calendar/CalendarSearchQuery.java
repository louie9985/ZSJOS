package cn.iocoder.yudao.module.zsjos.dal.mysql.calendar;

import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarSearchReqVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;

/** SQL expressions below are owned by each mapper; none come from request text. */
public final class CalendarSearchQuery {
    private CalendarSearchQuery() {}
    public static <T> void keyword(LambdaQueryWrapperX<T> query, String keyword, String... columns) {
        // LOCATE treats %, _ and backslashes literally without depending on the connection's escape mode.
        query.apply("(" + Arrays.stream(columns).map(c -> "LOCATE({0}, " + c + ") > 0")
                .collect(Collectors.joining(" OR ")) + ")", keyword);
    }
    public static <T> void dates(LambdaQueryWrapperX<T> query, CalendarSearchReqVO req,
                                String start, String end, boolean timed) {
        if (req.getRangeEnd() != null) {
            if (timed) query.apply(start + " < {0}", req.getRangeEnd().plusDays(1).atStartOfDay());
            else query.apply(start + " <= {0}", req.getRangeEnd());
        }
        if (req.getRangeStart() != null) {
            if (timed) query.apply("(" + end + " > {0} OR (" + start + " = " + end + " AND " + start + " >= {0}))",
                    req.getRangeStart().atStartOfDay());
            else query.apply(end + " >= {0}", req.getRangeStart());
        }
    }
    public static String order(CalendarSearchReqVO req, String start, String end, String id, LocalDate today) {
        String primary = switch (req.getSort()) {
            case "asc" -> start + " ASC";
            case "desc" -> start + " DESC";
            default -> "GREATEST(DATEDIFF(" + start + ", '" + today + "'), DATEDIFF('" + today + "', " + end + "), 0) ASC";
        };
        return "ORDER BY " + primary + ", " + start + " ASC, " + id + " ASC";
    }
}

