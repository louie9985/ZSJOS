package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PerformanceReportQuery extends PerformanceDetailQuery {
    public record Interval(String key, LocalDateTime start, LocalDateTime end) {}
    private List<Interval> intervals = List.of();
    private String grouping = "total";
}
