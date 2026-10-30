package cn.iocoder.yudao.module.zsjos.service.homemetric;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Bounded arithmetic AST; expressions never enter a script or SQL interpreter. */
public final class MetricFormula {
    private MetricFormula() {}

    public enum Operator { CONSTANT, VARIABLE, ADD, SUBTRACT, MULTIPLY, DIVIDE }
    public enum Status { OK, EMPTY, FORBIDDEN, INVALID_CONFIG, DIVIDE_BY_ZERO, SOURCE_ERROR, TIMEOUT, OVERFLOW }
    public record Node(Operator operator, String value, Node left, Node right) {}
    public record Result(Status status, BigDecimal value) {
        public Result {
            if (status == null || (status == Status.OK) != (value != null))
                throw new IllegalArgumentException("结果状态与数值不一致");
        }
        public static Result ok(BigDecimal value) { return new Result(Status.OK, value); }
        public static Result failure(Status status) { return new Result(status, null); }
    }

    public static Set<String> variables(Node root) {
        Set<String> names = new LinkedHashSet<>();
        validate(root, 0, new int[]{0}, names);
        return java.util.Collections.unmodifiableSet(names);
    }

    private static void validate(Node node, int depth, int[] count, Set<String> names) {
        if (node == null || node.operator() == null || depth > 16 || ++count[0] > 127)
            throw new IllegalArgumentException("公式为空或超过复杂度限制");
        switch (node.operator()) {
            case CONSTANT, VARIABLE -> {
                if (node.left() != null || node.right() != null || node.value() == null)
                    throw new IllegalArgumentException("公式叶节点无效");
                if (node.operator() == Operator.CONSTANT) {
                    if (!node.value().matches("-?[0-9]{1,24}(\\.[0-9]{1,12})?"))
                        throw new IllegalArgumentException("公式常量无效");
                } else {
                    if (!node.value().matches("[A-Za-z][A-Za-z0-9_]{0,31}"))
                        throw new IllegalArgumentException("操作数名称无效");
                    names.add(node.value());
                    if (names.size() > 16) throw new IllegalArgumentException("操作数过多");
                }
            }
            default -> {
                if (node.value() != null) throw new IllegalArgumentException("运算节点不能携带常量");
                validate(node.left(), depth + 1, count, names);
                validate(node.right(), depth + 1, count, names);
            }
        }
    }

    public static Result evaluate(Node root, Map<String, Result> operands) {
        Set<String> names;
        try { names = variables(root); }
        catch (IllegalArgumentException e) { return Result.failure(Status.INVALID_CONFIG); }
        if (operands == null) return Result.failure(Status.INVALID_CONFIG);
        // Check all participating inputs before arithmetic: even 0 * forbidden must not produce 0.
        for (String name : names) {
            Result input = operands.get(name);
            if (input != null && input.status() == Status.FORBIDDEN) return Result.failure(Status.FORBIDDEN);
        }
        for (String name : names) {
            Result input = operands.get(name);
            if (input == null) return Result.failure(Status.INVALID_CONFIG);
            if (input.status() != Status.OK) return input;
            if (!bounded(input.value())) return Result.failure(Status.OVERFLOW);
        }
        return calculate(root, operands);
    }

    private static Result calculate(Node node, Map<String, Result> operands) {
        if (node.operator() == Operator.CONSTANT) return Result.ok(new BigDecimal(node.value()));
        if (node.operator() == Operator.VARIABLE) return operands.get(node.value());
        Result left = calculate(node.left(), operands), right = calculate(node.right(), operands);
        if (left.status() != Status.OK) return left;
        if (right.status() != Status.OK) return right;
        if (node.operator() == Operator.DIVIDE && right.value().signum() == 0)
            return Result.failure(Status.DIVIDE_BY_ZERO);
        BigDecimal value = switch (node.operator()) {
            case ADD -> left.value().add(right.value());
            case SUBTRACT -> left.value().subtract(right.value());
            case MULTIPLY -> left.value().multiply(right.value());
            case DIVIDE -> left.value().divide(right.value(), MathContext.DECIMAL128);
            default -> throw new IllegalStateException("Unreachable operator");
        };
        return bounded(value) ? Result.ok(value) : Result.failure(Status.OVERFLOW);
    }

    private static boolean bounded(BigDecimal value) {
        return value.precision() <= 128 && Math.abs((long) value.scale()) <= 128;
    }
}
