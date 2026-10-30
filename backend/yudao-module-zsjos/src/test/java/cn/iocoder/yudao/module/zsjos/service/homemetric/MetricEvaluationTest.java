package cn.iocoder.yudao.module.zsjos.service.homemetric;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.service.homemetric.MetricEvaluation.*;

class MetricEvaluationTest {
    @Test void expiredTransactionIsTimeoutRatherThanSourceError() {
        Source source=new Source(){@Override public MetricFormula.Result aggregate(Operand operand,MetricPeriod.Range range){
            throw new org.springframework.transaction.TransactionTimedOutException("expired");
        }};
        assertEquals(MetricFormula.Status.TIMEOUT,new MetricEvaluation(clock,Duration.ofSeconds(3)).evaluate(definition(),source).result().status());
    }
    @Test void resolvedWindowsSurviveMonthBoundaryAndCarryRemainingBudget() {
        var old=new MetricPeriod.Range(LocalDateTime.of(2026,9,1,0,0),LocalDateTime.of(2026,10,1,0,0));
        var elapsed=new java.util.concurrent.atomic.AtomicLong();
        var windows=new java.util.ArrayList<MetricPeriod.Range>();
        var budgets=new java.util.ArrayList<Duration>();
        Source source=new Source(){@Override public MetricFormula.Result aggregate(Operand operand,MetricPeriod.Range range,Duration remaining){
            windows.add(range);budgets.add(remaining);elapsed.addAndGet(Duration.ofSeconds(1).toNanos());return super.aggregate(operand,range);
        }};
        var result=new MetricEvaluation(clock,Duration.ofSeconds(5),elapsed::get).evaluateResolved(definition(),source,java.util.Map.of("a",old,"b",old));
        assertEquals(MetricFormula.Status.OK,result.result().status());
        assertEquals(List.of(old,old),windows);
        assertEquals(List.of(Duration.ofSeconds(5),Duration.ofSeconds(4)),budgets);
        assertEquals(clock.instant(),result.evaluatedAt());
    }
    @Test void resolvedWindowsRejectMissingOrIncorrectNullBoundaryBeforeQuery() {
        Source source=new Source();
        var invalid=new java.util.LinkedHashMap<String,MetricPeriod.Range>();invalid.put("a",null);invalid.put("b",null);
        var engine=new MetricEvaluation(clock,Duration.ofSeconds(5));
        assertEquals(MetricFormula.Status.INVALID_CONFIG,engine.evaluateResolved(definition(),source,invalid).result().status());
        assertEquals(MetricFormula.Status.INVALID_CONFIG,engine.evaluateResolved(definition(),source,java.util.Map.of()).result().status());
        assertEquals(0,source.queries);
    }
    @Test void fixedBusinessTimeDoesNotDisableElapsedTimeBudget() {
        var elapsed=new java.util.concurrent.atomic.AtomicLong();
        Source source = new Source() { @Override public MetricFormula.Result aggregate(Operand operand, MetricPeriod.Range range) {
            elapsed.addAndGet(Duration.ofSeconds(4).toNanos());
            return super.aggregate(operand,range);
        } };
        Evaluation result = new MetricEvaluation(clock,Duration.ofSeconds(3),elapsed::get).evaluate(definition(),source);
        assertEquals(MetricFormula.Status.TIMEOUT,result.result().status());
        assertEquals(1,source.queries);
        assertTrue(result.inputs().isEmpty());
    }
    @Test void databaseTimeoutRemainsActionableAndDoesNotExposePartialValues() {
        Source source = new Source() { @Override public MetricFormula.Result aggregate(Operand operand, MetricPeriod.Range range) {
            throw new org.springframework.dao.QueryTimeoutException("private database detail");
        } };
        Evaluation result = new MetricEvaluation(clock, Duration.ofSeconds(3)).evaluate(definition(), source);
        assertEquals(MetricFormula.Status.TIMEOUT, result.result().status());
        assertNull(result.result().value());
        assertTrue(result.inputs().isEmpty());
    }
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T05:00:00Z"), ZoneOffset.UTC);
    private Operand operand(String name) {
        return new Operand(name, "order", MetricAggregate.COUNT, null, MetricPeriod.Kind.MONTH,
                false, null, MetricPeriod.Comparison.CURRENT);
    }
    private Definition definition() {
        return new Definition(new MetricFormula.Node(MetricFormula.Operator.DIVIDE, null,
                new MetricFormula.Node(MetricFormula.Operator.VARIABLE, "a", null, null),
                new MetricFormula.Node(MetricFormula.Operator.VARIABLE, "b", null, null)),
                List.of(operand("a"), operand("b")), true, 2);
    }
    private static class Source implements Sources {
        int queries;
        @Override public MetricUnit validate(Operand operand) { return MetricUnit.of("COUNT"); }
        @Override public boolean canRead(Operand operand) { return true; }
        @Override public MetricFormula.Result aggregate(Operand operand, MetricPeriod.Range range) {
            queries++;
            return MetricFormula.Result.ok(new BigDecimal("a".equals(operand.name()) ? "12" : "30"));
        }
    }
    @Test void rejectsForbiddenInputBeforeQueryingAnySource() {
        Source source = new Source() { @Override public boolean canRead(Operand operand) { return !"b".equals(operand.name()); } };
        Evaluation result = new MetricEvaluation(clock, Duration.ofSeconds(3)).evaluate(definition(), source);
        assertEquals(MetricFormula.Status.FORBIDDEN, result.result().status());
        assertTrue(result.inputs().isEmpty());
        assertEquals(0, source.queries);
    }
    @Test void resolvesOneTimestampAndReturnsUnscaledRatio() {
        Evaluation result = new MetricEvaluation(clock, Duration.ofSeconds(3)).evaluate(definition(), new Source());
        assertEquals(0, new BigDecimal("0.4").compareTo(result.result().value()));
        assertEquals(clock.instant(), result.evaluatedAt());
        assertEquals(result.inputs().getFirst().range(), result.inputs().getLast().range());
    }
    @Test void revocationDuringQueryDiscardsAllIntermediateValues() {
        AtomicInteger checks = new AtomicInteger();
        Source source = new Source() { @Override public boolean canRead(Operand operand) { return checks.incrementAndGet() <= 2; } };
        Evaluation result = new MetricEvaluation(clock, Duration.ofSeconds(3)).evaluate(definition(), source);
        assertEquals(MetricFormula.Status.FORBIDDEN, result.result().status());
        assertNull(result.result().value());
        assertTrue(result.inputs().isEmpty());
    }
    @Test void databaseErrorsDoNotEscapeAsSensitivePayloadOrZero() {
        Source source = new Source() { @Override public MetricFormula.Result aggregate(Operand operand, MetricPeriod.Range range) {
            throw new IllegalStateException("private SQL payload");
        } };
        Evaluation result = new MetricEvaluation(clock, Duration.ofSeconds(3)).evaluate(definition(), source);
        assertEquals(MetricFormula.Status.SOURCE_ERROR, result.result().status());
        assertNull(result.result().value());
        assertTrue(result.inputs().isEmpty());
    }
    @Test void unusedOperandAndPercentageOfCurrencyAreInvalid() {
        Definition unused = new Definition(new MetricFormula.Node(MetricFormula.Operator.VARIABLE,"a",null,null),
                definition().operands(), false, 2);
        assertThrows(IllegalArgumentException.class, () -> new MetricEvaluation(clock,Duration.ofSeconds(3)).validate(unused,new Source()));
        Definition money = new Definition(unused.formula(),List.of(operand("a")),true,2);
        assertThrows(IllegalArgumentException.class, () -> new MetricEvaluation(clock,Duration.ofSeconds(3)).validate(money,
                new Source() { @Override public MetricUnit validate(Operand operand) { return MetricUnit.of("CNY"); } }));
    }
}
