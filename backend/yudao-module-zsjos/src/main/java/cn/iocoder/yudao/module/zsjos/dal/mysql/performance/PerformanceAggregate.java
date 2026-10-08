package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PerformanceAggregate {
    private String key;
    private String groupKey;
    private String label;
    private Long userId;
    private BigDecimal amount = BigDecimal.ZERO;
    private long count;
    private BigDecimal averageAmount = BigDecimal.ZERO;
    private long averageOrders;
}
