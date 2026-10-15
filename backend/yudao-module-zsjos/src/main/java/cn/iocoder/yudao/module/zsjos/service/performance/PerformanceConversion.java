package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import java.util.*;
import java.math.BigDecimal;
import static cn.iocoder.yudao.module.zsjos.service.performance.PerformancePeriods.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSourceInterpretation;

/** One population for both the displayed ratio and its lead-level drilldown. */
public final class PerformanceConversion {
    private static final BigDecimal MINIMUM_ORDER_AMOUNT = new BigDecimal("1280");
    private PerformanceConversion() {}
    public record Row(PerformanceFact receipt, PerformanceFact order, boolean previous) {}
    private record ReceiptKey(Long lead, Long user, java.time.LocalDateTime received) {}
    public static final class Population {
        private final List<PerformanceFact> orders;
        private final List<PerformanceFact> receipts;
        private final Map<ReceiptKey, PerformanceFact> byReceipt = new HashMap<>();
        private final Map<Window, Assessment> windows = new HashMap<>();
        public Population(List<PerformanceFact> orders, List<PerformanceFact> receipts) {
            this.orders = orders.stream().filter(PerformanceConversion::currentlyOwned).toList();
            this.receipts = receipts.stream().filter(PerformanceConversion::currentlyOwned).toList();
            for (var r : this.receipts) byReceipt.putIfAbsent(new ReceiptKey(r.getLeadId(), r.getUserId(), r.getReceivedAt()), r);
        }
        public Map<Long, Row> at(Window window) {
            return assessment(window).eligible();
        }
        public Assessment assessment(Window window) {
            return windows.computeIfAbsent(window, w -> classify(calculate(w, orders, receipts, byReceipt)));
        }
    }
    // Apply before source review and both population paths; an old order must not reintroduce a transferred lead.
    private static boolean currentlyOwned(PerformanceFact fact) {
        return fact.getUserId() != null && fact.getUserId().equals(fact.getOwnerUserId())
                && Boolean.TRUE.equals(fact.getCurrentAssignment());
    }

    public record Assessment(Map<Long, Row> eligible, Map<Long, Row> review) {
        public boolean ready() { return review.isEmpty(); }
        public String sourceStatus() { return ready() ? "ready" : "review_required"; }
        public long sourceReviewCount() { return review.size(); }
    }
    private static LeadSourceInterpretation.Result source(PerformanceFact fact) {
        return LeadSourceInterpretation.resolve(fact.getSourceType(), fact.getSubmissionSourceType(),
                fact.getSourceProviderRecorded(), fact.getSourceProviderUserId(), fact.getProviderOwnerType(), fact.getProviderOwnerId());
    }
    // Classify only after period/cohort/amount eligibility: unrelated historical defects must not block a period.
    private static Assessment classify(Map<Long, Row> candidates) {
        Map<Long, Row> eligible = new LinkedHashMap<>(), review = new LinkedHashMap<>();
        candidates.forEach((id, row) -> {
            var facts = java.util.stream.Stream.of(row.receipt(), row.order()).filter(Objects::nonNull).map(PerformanceConversion::source).toList();
            if (facts.stream().anyMatch(LeadSourceInterpretation.Result::needsReview)) review.put(id, row);
            else if (facts.stream().noneMatch(LeadSourceInterpretation.Result::unlinked)) eligible.put(id, row);
        });
        return new Assessment(Collections.unmodifiableMap(eligible), Collections.unmodifiableMap(review));
    }

    public static boolean rolling(String key) {
        return Set.of("last7", "last30", "last60", "last90").contains(key);
    }

    public static Map<Long, Row> population(Window window, List<PerformanceFact> orders,
                                           List<PerformanceFact> receipts) {
        return new Population(orders, receipts).at(window);
    }
    private static Map<Long, Row> calculate(Window window, List<PerformanceFact> orders,
                                           List<PerformanceFact> receipts, Map<ReceiptKey, PerformanceFact> byReceipt) {
        Map<Long, Row> result = new LinkedHashMap<>();
        for (var receipt : receipts) {
            if (receipt.getLeadId() != null && window.contains(receipt.getReceivedAt())
                    && Set.of("valid", "converted", "won").contains(Objects.toString(receipt.getStatus(), ""))) {
                result.putIfAbsent(receipt.getLeadId(), new Row(receipt, null, false));
            }
        }
        for (var order : orders) {
            if (!"first_purchase".equals(order.getOrderType()) || order.getLeadId() == null
                    || order.getAmount() == null || order.getAmount().compareTo(MINIMUM_ORDER_AMOUNT) < 0
                    || !window.contains(order.getOccurredAt()) || order.getReceivedAt() == null) continue;
            boolean cohort = rolling(window.key());
            if (cohort ? !window.contains(order.getReceivedAt()) || order.getOccurredAt().isBefore(order.getReceivedAt())
                    : !withinValidity(order.getReceivedAt(), order.getOccurredAt())) continue;
            // A later transfer of the same Lead is not evidence of this seller's conversion.
            var receipt = byReceipt.get(new ReceiptKey(order.getLeadId(), order.getUserId(), order.getReceivedAt()));
            var existing = result.get(order.getLeadId());
            if (cohort && (receipt == null || existing == null)) continue;
            if (existing != null && existing.order() != null
                    && !order.getOccurredAt().isBefore(existing.order().getOccurredAt())) continue;
            result.put(order.getLeadId(), new Row(receipt != null ? receipt : existing == null ? null : existing.receipt(),
                    order, order.getReceivedAt().isBefore(window.start())));
        }
        return result;
    }
}
