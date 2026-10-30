package cn.iocoder.yudao.module.zsjos.service.homemetric;

import java.math.BigDecimal;

/** SQL adapters must deduplicate by their business identity before evaluating these aggregates. */
public enum MetricAggregate {
    COUNT, DISTINCT_COUNT, SUM, AVG, MIN, MAX;

    public boolean requiresField() { return this != COUNT && this != DISTINCT_COUNT; }

    public MetricFormula.Result result(BigDecimal databaseValue) {
        if (databaseValue != null) return MetricFormula.Result.ok(databaseValue);
        return switch (this) {
            case COUNT, DISTINCT_COUNT, SUM -> MetricFormula.Result.ok(BigDecimal.ZERO);
            case AVG, MIN, MAX -> MetricFormula.Result.failure(MetricFormula.Status.EMPTY);
        };
    }
}
