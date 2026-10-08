package cn.iocoder.yudao.module.zsjos.service.sorting;

import java.util.Map;

/** Only values identical to the visible projection may take the database pagination fast path. */
public final class BusinessSortSql {
    private BusinessSortSql() {}
    private static final Map<String, String> CASHBACK = Map.of(
            "amount", "c.amount", "baseAmount", "CASE WHEN c.type='valid' THEN NULL ELSE c.base_amount END",
            "rateSnapshot", "CASE WHEN c.type='valid' THEN NULL ELSE c.rate_snapshot END",
            "generatedAt", "c.generated_at", "availableAt", "c.available_at");
    private static final Map<String, String> WITHDRAWAL = Map.of(
            "applicationAmount", "application_amount", "submittedAt", "submitted_at", "reviewedAt", "reviewed_at");
    private static final Map<String, String> ORDER = Map.of("submittedAt", "submitted_at", "effectiveAt", "effective_at");
    public static String cashback(String field, String order) { return clause(CASHBACK, field, order, "c.id"); }
    public static String withdrawal(String field, String order) { return clause(WITHDRAWAL, field, order, "id"); }
    public static String order(String field, String order) { return clause(ORDER, field, order, "id"); }
    private static String clause(Map<String,String> fields, String field, String order, String id) {
        if (field == null || !fields.containsKey(field)) return null;
        if (!"ascend".equals(order) && !"descend".equals(order)) return null;
        String expression = fields.get(field);
        return "(" + expression + ") IS NULL ASC, " + expression + ("ascend".equals(order) ? " ASC, " : " DESC, ") + id + " DESC";
    }
}
