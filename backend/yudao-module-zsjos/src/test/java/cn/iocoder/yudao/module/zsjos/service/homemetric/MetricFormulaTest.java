package cn.iocoder.yudao.module.zsjos.service.homemetric;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.homemetric.MetricFormula.*;

class MetricFormulaTest {
    private static Node variable(String name) { return new Node(Operator.VARIABLE, name, null, null); }
    private static Node number(String value) { return new Node(Operator.CONSTANT, value, null, null); }
    private static Node binary(Operator op, Node left, Node right) { return new Node(op, null, left, right); }
    private static Result value(String value) { return Result.ok(new BigDecimal(value)); }

    @Test void calculatesCrossSourceRatioWithExplicitGrouping() {
        Node formula = binary(Operator.DIVIDE, binary(Operator.SUBTRACT, variable("current"), variable("previous")), variable("previous"));
        Result result = evaluate(formula, Map.of("current", value("150"), "previous", value("120")));
        assertEquals(Status.OK, result.status());
        assertEquals(0, new BigDecimal("0.25").compareTo(result.value()));
    }
    @Test void preservesDecimalMoneyBeforeDisplayRounding() {
        Node formula = binary(Operator.ADD, number("0.1"), number("0.2"));
        assertEquals(new BigDecimal("0.3"), evaluate(formula, Map.of()).value());
    }
    @Test void refusesToMaskForbiddenOperandWithZeroOrArithmeticError() {
        Node formula = binary(Operator.ADD, binary(Operator.DIVIDE, number("1"), number("0")),
                binary(Operator.MULTIPLY, number("0"), variable("secret")));
        assertEquals(Status.FORBIDDEN, evaluate(formula, Map.of("secret", Result.failure(Status.FORBIDDEN))).status());
    }
    @Test void distinguishesZeroEmptyFailureAndMissingInput() {
        Node formula = binary(Operator.DIVIDE, number("1"), variable("denominator"));
        assertEquals(Status.DIVIDE_BY_ZERO, evaluate(formula, Map.of("denominator", value("0"))).status());
        assertEquals(Status.EMPTY, evaluate(formula, Map.of("denominator", Result.failure(Status.EMPTY))).status());
        assertEquals(Status.TIMEOUT, evaluate(formula, Map.of("denominator", Result.failure(Status.TIMEOUT))).status());
        assertEquals(Status.INVALID_CONFIG, evaluate(formula, Map.of()).status());
    }
    @Test void rejectsScriptsMalformedTreesAndExcessiveNumbers() {
        assertEquals(Status.INVALID_CONFIG, evaluate(variable("T(java.lang.Runtime)"), Map.of()).status());
        assertEquals(Status.INVALID_CONFIG, evaluate(number("1e999999"), Map.of()).status());
        assertEquals(Status.INVALID_CONFIG, evaluate(new Node(Operator.ADD, null, number("1"), null), Map.of()).status());
        Node deep = number("1");
        for (int i = 0; i < 20; i++) deep = binary(Operator.ADD, deep, number("1"));
        assertEquals(Status.INVALID_CONFIG, evaluate(deep, Map.of()).status());
    }
    @Test void rejectsOversizedSourceNumbersBeforeMultiplication() {
        assertEquals(Status.OVERFLOW, evaluate(variable("amount"), Map.of("amount", value("1E+10000"))).status());
    }
    @Test void supportsNegativeResultsAndRepeatingDivision() {
        Result negative = evaluate(binary(Operator.SUBTRACT, number("1"), number("2")), Map.of());
        assertEquals(new BigDecimal("-1"), negative.value());
        Result third = evaluate(binary(Operator.DIVIDE, number("1"), number("3")), Map.of());
        assertEquals(Status.OK, third.status());
        assertEquals(34, third.value().precision());
    }
}
