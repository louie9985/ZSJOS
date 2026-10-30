package cn.iocoder.yudao.module.zsjos.service.homemetric;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Coordinates authorized aggregates without returning intermediate values on a denied formula. */
public final class MetricEvaluation {
    public record Operand(String name, String source, MetricAggregate aggregate, String field,
                          MetricPeriod.Kind period, boolean toDate, MetricPeriod.Range customRange,
                          MetricPeriod.Comparison comparison) {}
    public record Definition(MetricFormula.Node formula, List<Operand> operands, boolean percentage, int decimals) {}
    public record Input(String name, MetricPeriod.Range range, MetricFormula.Result result) {}
    public record Evaluation(Instant evaluatedAt, MetricFormula.Result result, MetricUnit unit, List<Input> inputs) {}

    /** Implementations own source/field allowlists, business permissions, filters and SQL scope. */
    public interface Sources {
        MetricUnit validate(Operand operand);
        boolean canRead(Operand operand);
        MetricFormula.Result aggregate(Operand operand, MetricPeriod.Range range);
        default MetricFormula.Result aggregate(Operand operand, MetricPeriod.Range range, Duration remaining) {
            return aggregate(operand,range);
        }
    }

    private final Clock clock;
    private final Duration budget;
    private final java.util.function.LongSupplier elapsedClock;

    public MetricEvaluation(Clock clock, Duration budget) {
        this(clock,budget,System::nanoTime);
    }

    MetricEvaluation(Clock clock, Duration budget, java.util.function.LongSupplier elapsedClock) {
        this.clock = Objects.requireNonNull(clock);
        this.elapsedClock = Objects.requireNonNull(elapsedClock);
        if (budget == null || budget.isNegative() || budget.isZero() || budget.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("统计查询时间预算无效");
        this.budget = budget;
    }

    public MetricUnit validate(Definition definition, Sources sources) {
        return validate(definition,sources,true);
    }

    private MetricUnit validate(Definition definition, Sources sources, boolean resolvePeriods) {
        if (definition == null || definition.operands() == null || definition.operands().isEmpty()
                || definition.operands().size() > 16 || definition.decimals() < 0 || definition.decimals() > 8)
            throw new IllegalArgumentException("指标定义无效");
        Set<String> required = MetricFormula.variables(definition.formula());
        Map<String, MetricUnit> units = new LinkedHashMap<>();
        for (Operand operand : definition.operands()) {
            if (operand == null || operand.name() == null || !operand.name().matches("[A-Za-z][A-Za-z0-9_]{0,31}")
                    || operand.source() == null || operand.source().isBlank() || operand.aggregate() == null
                    || operand.period() == null || operand.comparison() == null
                    || operand.aggregate().requiresField() != (operand.field() != null && !operand.field().isBlank()))
                throw new IllegalArgumentException("指标操作数无效");
            if (units.containsKey(operand.name())) throw new IllegalArgumentException("操作数名称重复");
            units.put(operand.name(), Objects.requireNonNull(sources.validate(operand)));
            if(resolvePeriods) MetricPeriod.compare(MetricPeriod.resolve(operand.period(), operand.toDate(), clock.instant(), operand.customRange()),
                    operand.period(), operand.comparison());
        }
        if (!required.equals(units.keySet())) throw new IllegalArgumentException("公式必须恰好使用配置的操作数");
        MetricUnit unit = MetricUnit.resolve(definition.formula(), units);
        if (definition.percentage() && !unit.equals(MetricUnit.scalar()))
            throw new IllegalArgumentException("只有无量纲比率可显示为百分比");
        return unit;
    }

    public Evaluation evaluate(Definition definition, Sources sources) {
        return evaluate(definition,sources,null);
    }

    public Evaluation evaluateResolved(Definition definition,Sources sources,Map<String,MetricPeriod.Range> ranges) {
        if(ranges==null)return failed(clock.instant(),MetricFormula.Status.INVALID_CONFIG);
        return evaluate(definition,sources,new LinkedHashMap<>(ranges));
    }

    private Evaluation evaluate(Definition definition,Sources sources,Map<String,MetricPeriod.Range> ranges) {
        Instant now = clock.instant();
        long started = elapsedClock.getAsLong();
        MetricUnit unit;
        try {
            unit = validate(definition, sources,ranges==null);
            if(ranges!=null) {
                if(!ranges.keySet().equals(MetricFormula.variables(definition.formula())))throw new IllegalArgumentException("冻结区间与操作数不匹配");
                for(var operand:definition.operands())
                    if((operand.period()==MetricPeriod.Kind.ALL)!=(ranges.get(operand.name())==null))
                        throw new IllegalArgumentException("冻结区间类型不匹配");
            }
        }
        catch (IllegalArgumentException | NullPointerException e) {
            return failed(now, MetricFormula.Status.INVALID_CONFIG);
        }
        // Do not issue any aggregate query if even one input is forbidden.
        for (Operand operand : definition.operands()) {
            if (!sources.canRead(operand)) return failed(now, MetricFormula.Status.FORBIDDEN);
        }
        Map<String, MetricFormula.Result> values = new LinkedHashMap<>();
        List<Input> inputs = new ArrayList<>();
        for (Operand operand : definition.operands()) {
            if (elapsedClock.getAsLong()-started >= budget.toNanos()) return failed(now, MetricFormula.Status.TIMEOUT);
            MetricPeriod.Range range = ranges!=null ? ranges.get(operand.name()) : MetricPeriod.compare(MetricPeriod.resolve(operand.period(), operand.toDate(), now,
                    operand.customRange()), operand.period(), operand.comparison());
            MetricFormula.Result value;
            try {
                long remaining=budget.toNanos()-(elapsedClock.getAsLong()-started);
                if(remaining<=0)return failed(now,MetricFormula.Status.TIMEOUT);
                value = Objects.requireNonNull(sources.aggregate(operand, range,Duration.ofNanos(remaining)));
            } catch (org.springframework.dao.QueryTimeoutException | org.springframework.transaction.TransactionTimedOutException exception) {
                return failed(now, MetricFormula.Status.TIMEOUT);
            } catch (RuntimeException exception) {
                // SQL adapters log a sanitized trace separately; do not leak source exception text.
                return failed(now, MetricFormula.Status.SOURCE_ERROR);
            }
            if (value.status() == MetricFormula.Status.FORBIDDEN) return failed(now, value.status());
            values.put(operand.name(), value);
            inputs.add(new Input(operand.name(), range, value));
        }
        if (elapsedClock.getAsLong()-started >= budget.toNanos()) return failed(now, MetricFormula.Status.TIMEOUT);
        // Revocation during execution cannot expose already-computed operand values.
        for (Operand operand : definition.operands()) {
            if (!sources.canRead(operand)) return failed(now, MetricFormula.Status.FORBIDDEN);
        }
        return new Evaluation(now, MetricFormula.evaluate(definition.formula(), values), unit, List.copyOf(inputs));
    }

    private Evaluation failed(Instant now, MetricFormula.Status status) {
        return new Evaluation(now, MetricFormula.Result.failure(status), null, List.of());
    }
}
