package cn.iocoder.yudao.module.zsjos.service.homemetric;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MetricFormulaParserTest {
    private BigDecimal calculate(String text) {
        return MetricFormula.evaluate(MetricFormulaParser.parse(text), Map.of()).value();
    }
    @Test void parenthesesAndPrecedenceArePreserved() {
        assertEquals(new BigDecimal("14"), calculate("2 + 3 * 4"));
        assertEquals(new BigDecimal("20"), calculate("(2 + 3) * 4"));
        assertEquals(new BigDecimal("2"), calculate("12 / 3 / 2"));
    }
    @Test void unaryNegativePreservesDimensionalUnit() {
        var formula = MetricFormulaParser.parse("-amount");
        assertEquals(MetricUnit.of("CNY"), MetricUnit.resolve(formula, Map.of("amount", MetricUnit.of("CNY"))));
        assertEquals(new BigDecimal("-3"), calculate("-(1 + 2)"));
    }
    @Test void acceptsCrossSourceNamesAndDecimalConstants() {
        var formula = MetricFormulaParser.parse("(orders_current - orders_previous) / orders_previous");
        assertEquals(java.util.Set.of("orders_current", "orders_previous"), MetricFormula.variables(formula));
        assertEquals(new BigDecimal("0.25"), calculate("0.5 * 0.5"));
    }
    @Test void rejectsCodeFunctionsAndMalformedArithmetic() {
        for (String expression : new String[]{"a.b", "a[0]", "a()", "1; DROP TABLE x", "2**3", "1e4", "1.", "(1", "1)", "1 2", "a ? b : c", "1 /", "()"}) {
            assertThrows(IllegalArgumentException.class, () -> MetricFormulaParser.parse(expression), expression);
        }
    }
    @Test void rejectsDeepParenthesesBeforeStackOverflow() {
        assertThrows(IllegalArgumentException.class, () -> MetricFormulaParser.parse("(".repeat(1000) + "1" + ")".repeat(1000)));
        assertThrows(IllegalArgumentException.class, () -> MetricFormulaParser.parse("1+".repeat(1000) + "1"));
    }
}
