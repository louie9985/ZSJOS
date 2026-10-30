package cn.iocoder.yudao.module.zsjos.service.homemetric;

import java.util.Map;
import java.util.TreeMap;

/** Dimensional arithmetic prevents adding a currency amount to a record count. */
public record MetricUnit(Map<String, Integer> dimensions) {
    public MetricUnit {
        if (dimensions == null || dimensions.size() > 16) throw new IllegalArgumentException("指标单位无效");
        TreeMap<String, Integer> normalized = new TreeMap<>();
        dimensions.forEach((name, exponent) -> {
            if (name == null || !name.matches("[A-Z][A-Z0-9_]{0,31}") || exponent == null || Math.abs((long) exponent) > 127)
                throw new IllegalArgumentException("指标单位无效");
            if (exponent != 0) normalized.put(name, exponent);
        });
        dimensions = Map.copyOf(normalized);
    }
    public static MetricUnit scalar() { return new MetricUnit(Map.of()); }
    public static MetricUnit of(String name) { return new MetricUnit(Map.of(name, 1)); }

    public static MetricUnit resolve(MetricFormula.Node formula, Map<String, MetricUnit> inputs) {
        MetricFormula.variables(formula);
        if (inputs == null) throw new IllegalArgumentException("操作数单位缺失");
        return walk(formula, inputs);
    }

    private static MetricUnit walk(MetricFormula.Node node, Map<String, MetricUnit> inputs) {
        if (node.operator() == MetricFormula.Operator.CONSTANT) return scalar();
        if (node.operator() == MetricFormula.Operator.VARIABLE) {
            MetricUnit unit = inputs.get(node.value());
            if (unit == null) throw new IllegalArgumentException("操作数单位缺失");
            return unit;
        }
        MetricUnit left = walk(node.left(), inputs), right = walk(node.right(), inputs);
        if (node.operator() == MetricFormula.Operator.ADD || node.operator() == MetricFormula.Operator.SUBTRACT) {
            if (!left.equals(right)) throw new IllegalArgumentException("加减运算的单位必须一致");
            return left;
        }
        TreeMap<String, Integer> merged = new TreeMap<>(left.dimensions());
        int sign = node.operator() == MetricFormula.Operator.DIVIDE ? -1 : 1;
        right.dimensions().forEach((name, exponent) -> merged.merge(name, sign * exponent, Integer::sum));
        return new MetricUnit(merged);
    }
}
