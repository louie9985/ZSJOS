package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Accessors(chain = true)
public class PerformanceDetailQuery {
    private Long tenant;
    private String type;
    private Long id;
    private boolean allDepartments;
    private boolean missingDepartment;
    private Set<Long> departments = Set.of();
    private Set<Long> conversionLeadIds = Set.of();
    private String metric;
    private String dimension;
    private String groupKey;
    private LocalDateTime start;
    private LocalDateTime end;
    private LocalDateTime dueEnd;
    private LocalDateTime now;
    private LocalDateTime today;
    private LocalDateTime tomorrow;
    private long offset;
    private int size;
}
