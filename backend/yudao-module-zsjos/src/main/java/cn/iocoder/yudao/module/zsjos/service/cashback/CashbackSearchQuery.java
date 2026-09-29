package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterQuery;
import java.util.*;

/** Only server-owned expressions enter this builder; all values remain bound parameters. */
public final class CashbackSearchQuery {
    private final Map<String, Object> parameters;
    private final List<String> predicates = new ArrayList<>();

    public CashbackSearchQuery(CashbackPageReqVO request, Long beneficiary, long tenant) {
        parameters = new LinkedHashMap<>();
        parameters.put("tenantId", tenant);
        predicates.add("c.tenant_id=#{query.parameters.tenantId} AND c.deleted=0");
        compare("c.beneficiary_user_id", "=", beneficiary);
        compare("c.beneficiary_user_id", "=", request.getBeneficiaryUserId());
        compare("c.partner_id", "=", request.getPartnerId());
        compare("c.type", "=", request.getType()); compare("c.status", "=", request.getStatus());
        compare("c.amount", ">=", request.getAmountMin()); compare("c.amount", "<=", request.getAmountMax());
        compare("c.generated_at", ">=", request.getGeneratedAtFrom()); compare("c.generated_at", "<=", request.getGeneratedAtTo());
        compare("c.available_at", ">=", request.getAvailableAtFrom()); compare("c.available_at", "<=", request.getAvailableAtTo());
        compare("c.settled_at", ">=", request.getSettledAtFrom()); compare("c.settled_at", "<=", request.getSettledAtTo());
        if (request.getProductName() != null && !request.getProductName().isEmpty())
            and("c.product_name_snapshot LIKE " + bind("%" + request.getProductName() + "%"));
        if (request.getOrderNo() != null && !request.getOrderNo().isBlank())
            and("EXISTS (SELECT 1 FROM zsjos_order fo WHERE fo.id=c.order_id AND fo.tenant_id=c.tenant_id AND fo.deleted=0 AND fo.order_no LIKE "
                    + bind("%" + request.getOrderNo().trim() + "%") + ")");
    }

    public CashbackSearchQuery(AdvancedFilterQuery source) {
        parameters = new LinkedHashMap<>(source.getParameters()); predicates.add(source.getWhereSql());
    }
    private void compare(String column, String operator, Object value) {
        if (value != null && (!(value instanceof String s) || !s.isEmpty())) and(column + " " + operator + " " + bind(value));
    }
    public String bind(Object value) {
        String key = "cb" + parameters.size(); parameters.put(key, value);
        return "#{query.parameters." + key + "}";
    }
    public void and(String predicate) { predicates.add("(" + predicate + ")"); }
    public void advanced(AdvancedFilterQuery query) {
        if (query == null) return;
        parameters.putAll(query.getParameters()); and(query.getWhereSql());
    }
    public AdvancedFilterQuery build() { return new AdvancedFilterQuery(String.join(" AND ", predicates), new LinkedHashMap<>(parameters)); }
    public String ids(String expression, Collection<Long> ids) {
        return ids.isEmpty() ? "1=0" : jsonIds(expression, bind(JsonUtils.toJsonString(ids)));
    }
    public static String jsonIds(String expression, String parameter) {
        // One bound JSON value avoids an unbounded number of SQL placeholders for broad matches.
        return expression + " IN (SELECT cbids.id FROM JSON_TABLE(" + parameter
                + ", '$[*]' COLUMNS(id BIGINT PATH '$')) cbids)";
    }
}
