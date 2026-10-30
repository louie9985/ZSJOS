package cn.iocoder.yudao.module.zsjos.service.homemetric;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.homemetric.MetricFormula.*;

class MetricUnitTest {
    private Node variable(String name) { return new Node(Operator.VARIABLE, name, null, null); }
    private Node binary(Operator op) { return new Node(op, null, variable("a"), variable("b")); }

    @Test void rejectsAddingMoneyAndCounts() {
        assertThrows(IllegalArgumentException.class, () -> MetricUnit.resolve(binary(Operator.ADD),
                Map.of("a", MetricUnit.of("CNY"), "b", MetricUnit.of("COUNT"))));
    }
    @Test void ratiosCancelCompatibleUnits() {
        assertEquals(MetricUnit.scalar(), MetricUnit.resolve(binary(Operator.DIVIDE),
                Map.of("a", MetricUnit.of("COUNT"), "b", MetricUnit.of("COUNT"))));
    }
    @Test void averageOrderAmountRetainsCurrencyPerCount() {
        assertEquals(new MetricUnit(Map.of("CNY", 1, "COUNT", -1)), MetricUnit.resolve(binary(Operator.DIVIDE),
                Map.of("a", MetricUnit.of("CNY"), "b", MetricUnit.of("COUNT"))));
    }
    @Test void rejectsMissingUnitAndInvalidDimension() {
        assertThrows(IllegalArgumentException.class, () -> MetricUnit.resolve(variable("missing"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MetricUnit.of("arbitrary script"));
    }
    @Test void emptyAggregateResultsDoNotInventAnAverage() {
        assertEquals(Status.EMPTY, MetricAggregate.AVG.result(null).status());
        assertEquals(Status.EMPTY, MetricAggregate.MIN.result(null).status());
        assertEquals(Status.EMPTY, MetricAggregate.MAX.result(null).status());
        assertEquals(java.math.BigDecimal.ZERO, MetricAggregate.COUNT.result(null).value());
        assertEquals(java.math.BigDecimal.ZERO, MetricAggregate.SUM.result(null).value());
    }
}
