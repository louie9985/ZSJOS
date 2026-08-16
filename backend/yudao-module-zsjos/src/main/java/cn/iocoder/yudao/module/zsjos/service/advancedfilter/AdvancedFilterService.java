package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter.AdvancedFilterMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_INVALID;

@Service
public class AdvancedFilterService {
    private static final List<String> TEXT_OPS = List.of("contains", "eq", "ne", "is_empty", "is_not_empty");
    private static final List<String> SELECT_OPS = List.of("in", "not_in", "is_empty", "is_not_empty");
    private static final List<String> RANGE_OPS = List.of("eq", "gt", "lt", "between", "is_empty", "is_not_empty");
    private static final Map<String, Field> FIELDS = fields();
    @Resource private AdvancedFilterMapper mapper;

    public AdvancedFilterCatalogRespVO catalog(String scene) {
        if (!Set.of("lead", "order").contains(scene)) throw exception(ADVANCED_FILTER_INVALID);
        return new AdvancedFilterCatalogRespVO(FIELDS.values().stream()
                .filter(field -> field.scenes.contains(scene))
                .map(field -> new AdvancedFilterCatalogRespVO.FieldVO(field.key, field.group, field.label,
                        field.type, field.operators, field.optionSource, field.options)).toList());
    }

    public boolean hasConditions(AdvancedFilterGroupReqVO group) {
        return group != null && ((group.getConditions() != null && !group.getConditions().isEmpty())
                || (group.getGroups() != null && group.getGroups().stream().anyMatch(this::hasConditions)));
    }

    public List<Long> matchLeadIds(AdvancedFilterGroupReqVO group) {
        return validateAndHasConditions(group) ? mapper.selectLeadIds(build(group, "lead")) : null;
    }

    public List<Long> matchOrderIds(AdvancedFilterGroupReqVO group) {
        AdvancedFilterQuery query = buildOrderQuery(group);
        return query == null ? null : mapper.selectOrderIds(query);
    }

    public AdvancedFilterQuery buildOrderQuery(AdvancedFilterGroupReqVO group) {
        return validateAndHasConditions(group) ? build(group, "order") : null;
    }

    private AdvancedFilterQuery build(AdvancedFilterGroupReqVO group, String scene) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", TenantContextHolder.getTenantId());
        String sql = groupSql(group, scene, params);
        if (sql.isBlank()) throw exception(ADVANCED_FILTER_INVALID);
        return new AdvancedFilterQuery(sql, params);
    }

    private boolean validateAndHasConditions(AdvancedFilterGroupReqVO group) {
        if (group == null) return false;
        validateShape(group, 0, new int[]{0});
        return hasConditions(group);
    }

    private void validateShape(AdvancedFilterGroupReqVO group, int depth, int[] count) {
        if (group == null || group.getConditions() == null || group.getGroups() == null
                || group.getConditions().stream().anyMatch(Objects::isNull)
                || group.getGroups().stream().anyMatch(Objects::isNull)
                || depth > 1 || group.getGroups().size() > 5) throw exception(ADVANCED_FILTER_INVALID);
        count[0] += group.getConditions().size();
        if (count[0] > 20 || (depth == 1 && !group.getGroups().isEmpty())) throw exception(ADVANCED_FILTER_INVALID);
        group.getGroups().forEach(child -> validateShape(child, depth + 1, count));
    }

    private String groupSql(AdvancedFilterGroupReqVO group, String scene, Map<String, Object> params) {
        List<String> parts = new ArrayList<>();
        group.getConditions().forEach(condition -> parts.add(conditionSql(condition, scene, params)));
        group.getGroups().forEach(child -> parts.add("(" + groupSql(child, scene, params) + ")"));
        if (parts.isEmpty()) return "";
        return String.join(" " + ("OR".equals(group.getLogic()) ? "OR" : "AND") + " ", parts);
    }

    private String conditionSql(AdvancedFilterConditionReqVO condition, String scene, Map<String, Object> params) {
        Field field = FIELDS.get(condition.getFieldKey());
        if (field == null || !field.scenes.contains(scene) || !field.operators.contains(condition.getOperator())) {
            throw exception(ADVANCED_FILTER_INVALID);
        }
        String expression = field.expression.get(scene);
        String relation = field.relation.get(scene);
        String operator = condition.getOperator();
        if ("is_empty".equals(operator)) return wrap(relation, expression + " IS NOT NULL", true);
        if ("is_not_empty".equals(operator)) return wrap(relation, expression + " IS NOT NULL", false);
        if ("between".equals(operator)) {
            Object from = typed(field.type, condition.getValueFrom());
            Object to = typed(field.type, condition.getValueTo());
            if (from == null || to == null) throw exception(ADVANCED_FILTER_INVALID);
            String a = add(params, from), b = add(params, to);
            return wrap(relation, expression + " BETWEEN #{query.parameters." + a + "} AND #{query.parameters." + b + "}", false);
        }
        if ("in".equals(operator) || "not_in".equals(operator)) {
            List<?> values = condition.getValue() instanceof Collection<?> collection ? collection.stream().toList() : List.of();
            if (values.isEmpty() || values.size() > 100) throw exception(ADVANCED_FILTER_INVALID);
            List<String> refs = values.stream().map(value -> "#{query.parameters." + add(params, typed(field.type, value)) + "}").toList();
            String predicate = expression + " IN (" + String.join(",", refs) + ")";
            return wrap(relation, predicate, "not_in".equals(operator));
        }
        Object value = typed(field.type, condition.getValue());
        if (value == null) throw exception(ADVANCED_FILTER_INVALID);
        String ref = "#{query.parameters." + add(params, value) + "}";
        return switch (operator) {
            case "contains" -> wrap(relation, expression + " LIKE CONCAT('%'," + ref + ",'%')", false);
            case "eq" -> wrap(relation, expression + " = " + ref, false);
            case "ne" -> wrap(relation, expression + " = " + ref, true);
            case "gt" -> wrap(relation, expression + " > " + ref, false);
            case "lt" -> wrap(relation, expression + " < " + ref, false);
            default -> throw exception(ADVANCED_FILTER_INVALID);
        };
    }

    private String wrap(String relation, String predicate, boolean negate) {
        if (relation == null) return negate ? "NOT (" + predicate + ")" : predicate;
        return (negate ? "NOT EXISTS (" : "EXISTS (") + relation + " AND " + predicate + ")";
    }

    private Object typed(String type, Object raw) {
        if (raw == null || raw.toString().isBlank()) return null;
        try {
            return switch (type) {
                case "number" -> new BigDecimal(raw.toString());
                case "date" -> parseDate(raw.toString());
                default -> raw.toString().trim();
            };
        } catch (NumberFormatException | DateTimeParseException | ArithmeticException ex) { throw exception(ADVANCED_FILTER_INVALID); }
    }

    private String add(Map<String, Object> params, Object value) {
        String key = "p" + params.size(); params.put(key, value); return key;
    }

    private static Map<String, Field> fields() {
        Map<String, Field> result = new LinkedHashMap<>();
        java.util.function.Consumer<Field> add = field -> result.put(field.key, field);
        add.accept(text("person.personNo", "客户", "客户编号", "p.person_no"));
        add.accept(text("person.name", "客户", "客户姓名", "p.name"));
        add.accept(text("person.mobile", "客户", "客户手机号", "p.mobile"));
        add.accept(text("person.wechatId", "客户", "客户微信号", "p.wechat_id"));
        add.accept(select("person.identityStatus", "客户", "身份状态", "p.identity_status", options(
                "lead", "客资客户", "active", "成交客户")));
        add.accept(date("person.firstSeenAt", "客户", "首次出现时间", "p.first_seen_at"));
        add.accept(date("person.lastSeenAt", "客户", "最近出现时间", "p.last_seen_at"));
        add.accept(text("lead.name", "客资", "客资姓名", "l.submitted_name"));
        add.accept(text("lead.mobile", "客资", "客资手机号", "l.submitted_mobile"));
        add.accept(text("lead.wechatId", "客资", "客资微信号", "l.submitted_wechat_id"));
        add.accept(select("lead.status", "客资", "客资状态", "l.status", options(
                "submitted", "已提交", "suspended", "已挂起", "valid", "有效", "invalid", "无效",
                "won", "已成交", "closed", "已关闭")));
        add.accept(select("lead.assignmentStatus", "客资", "分配状态", "l.assignment_status", options(
                "unassigned", "未分配", "pending_acceptance", "待接单", "owned", "已归属",
                "public_pool", "抢单池", "recycle_pending", "回收待处理", "closed", "已关闭")));
        add.accept(select("lead.sourceChannel", "客资", "来源渠道", "l.source_channel_id", "dict:zsjos_lead_source_channel"));
        add.accept(select("lead.category", "客资", "客资分类", "l.lead_category", "dict:zsjos_lead_category"));
        add.accept(select("lead.sourceUserId", "客资", "提交人", "l.source_user_id", "visible-users"));
        add.accept(select("lead.ownerUserId", "客资", "负责人", "l.owner_user_id", "visible-users"));
        add.accept(date("lead.submittedAt", "客资", "提交时间", "l.submitted_at"));
        add.accept(date("lead.nextFollowUpAt", "客资", "下次跟进时间", "l.next_follow_up_at"));
        add.accept(text("opportunity.expectedProduct", "商机", "预计产品", "op.expected_product_summary"));
        add.accept(select("opportunity.status", "商机", "商机状态", "op.status", options(
                "open", "待推进", "following", "跟进中", "lost", "已流失",
                "deal_pending_approval", "成交审批中", "won", "已赢单")));
        add.accept(select("opportunity.ownerUserId", "商机", "商机负责人", "op.owner_user_id", "visible-users"));
        add.accept(date("opportunity.nextFollowUpAt", "商机", "商机下次跟进", "op.next_follow_up_at"));
        add.accept(text("opportunity.lostReason", "商机", "流失原因", "op.lost_reason"));
        add.accept(text("order.orderNo", "订单", "订单号", "so.order_no"));
        add.accept(select("order.status", "订单", "订单状态", "so.status", options(
                "pending_approval", "审批中", "revision_required", "待修改", "effective", "已生效",
                "superseded", "已被接续", "terminated", "已终止")));
        add.accept(select("order.type", "订单", "订单类型", "so.order_type", options(
                "first_purchase", "首购", "repurchase", "复购")));
        add.accept(text("order.studentName", "订单", "学员姓名", "so.student_name"));
        add.accept(text("order.studentMobile", "订单", "学员手机号", "so.student_mobile"));
        add.accept(select("order.submitterUserId", "订单", "订单提交人", "so.submitter_user_id", "visible-users"));
        add.accept(select("order.formalSalesUserId", "订单", "正式销售", "so.formal_sales_user_id", "visible-users"));
        add.accept(number("order.totalAmount", "订单", "订单总金额", "so.total_amount"));
        add.accept(date("order.submittedAt", "订单", "订单提交时间", "so.submitted_at"));
        add.accept(date("order.effectiveAt", "订单", "订单生效时间", "so.effective_at"));
        add.accept(text("leadProduct.name", "商品明细", "意向商品", "lip.spu_name_snapshot"));
        add.accept(text("orderItem.product", "商品明细", "订单商品", "oi.product_snapshot"));
        add.accept(number("orderItem.payableAmount", "商品明细", "订单商品金额", "oi.payable_amount"));
        return result;
    }

    private static Field text(String key, String group, String label, String column) { return field(key, group, label, "text", TEXT_OPS, null, column); }
    private static Field select(String key, String group, String label, String column, String source) { return field(key, group, label, "select", SELECT_OPS, source, column); }
    private static Field select(String key, String group, String label, String column,
                                List<AdvancedFilterCatalogRespVO.OptionVO> options) {
        Field field = field(key, group, label, "select", SELECT_OPS, null, column);
        return new Field(field.key, field.group, field.label, field.type, field.operators, null,
                options, field.scenes, field.expression, field.relation);
    }

    private LocalDateTime parseDate(String raw) {
        if (raw.matches("^-?\\d+$")) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(raw)), ZoneId.systemDefault());
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        }
    }
    private static Field number(String key, String group, String label, String column) { return field(key, group, label, "number", RANGE_OPS, null, column); }
    private static Field date(String key, String group, String label, String column) { return field(key, group, label, "date", RANGE_OPS, null, column); }
    private static Field field(String key, String group, String label, String type, List<String> ops, String source, String column) {
        Map<String, String> expressions = new HashMap<>(); Map<String, String> relations = new HashMap<>();
        Set<String> scenes = new LinkedHashSet<>(List.of("lead", "order"));
        expressions.put("lead", column); expressions.put("order", column);
        if (key.startsWith("person.")) {
            relations.put("lead", "SELECT 1 FROM zsjos_person p WHERE p.id=l.person_id AND p.tenant_id=l.tenant_id AND p.deleted=b'0'");
            relations.put("order", "SELECT 1 FROM zsjos_person p WHERE p.id=o.person_id AND p.tenant_id=o.tenant_id AND p.deleted=b'0'");
        } else if (key.startsWith("lead.")) {
            expressions.put("lead", column);
            relations.put("order", "SELECT 1 FROM zsjos_lead l WHERE l.person_id=o.person_id AND l.tenant_id=o.tenant_id AND l.deleted=b'0'");
        } else if (key.startsWith("opportunity.")) {
            relations.put("lead", "SELECT 1 FROM zsjos_opportunity op WHERE op.person_id=l.person_id AND op.tenant_id=l.tenant_id AND op.deleted=b'0'");
            relations.put("order", "SELECT 1 FROM zsjos_opportunity op WHERE op.person_id=o.person_id AND op.tenant_id=o.tenant_id AND op.deleted=b'0'");
        } else if (key.startsWith("order.")) {
            relations.put("lead", "SELECT 1 FROM zsjos_order so WHERE so.person_id=l.person_id AND so.tenant_id=l.tenant_id AND so.deleted=b'0'");
            expressions.put("order", column.replace("so.", "o."));
        } else if (key.startsWith("leadProduct.")) {
            relations.put("lead", "SELECT 1 FROM zsjos_lead_intended_product lip WHERE lip.lead_id=l.id AND lip.tenant_id=l.tenant_id AND lip.deleted=b'0'");
            relations.put("order", "SELECT 1 FROM zsjos_lead_intended_product lip JOIN zsjos_lead lp ON lp.id=lip.lead_id AND lp.deleted=b'0' WHERE lp.person_id=o.person_id AND lip.tenant_id=o.tenant_id AND lip.deleted=b'0'");
        } else {
            relations.put("lead", "SELECT 1 FROM zsjos_order_item oi JOIN zsjos_order so ON so.id=oi.order_id AND so.deleted=b'0' WHERE so.person_id=l.person_id AND oi.tenant_id=l.tenant_id AND oi.deleted=b'0'");
            relations.put("order", "SELECT 1 FROM zsjos_order_item oi WHERE oi.order_id=o.id AND oi.tenant_id=o.tenant_id AND oi.deleted=b'0'");
        }
        return new Field(key, group, label, type, ops, source, List.of(), scenes, expressions, relations);
    }

    private static List<AdvancedFilterCatalogRespVO.OptionVO> options(String... valuesAndLabels) {
        List<AdvancedFilterCatalogRespVO.OptionVO> result = new ArrayList<>();
        for (int i = 0; i < valuesAndLabels.length; i += 2) {
            result.add(new AdvancedFilterCatalogRespVO.OptionVO(valuesAndLabels[i], valuesAndLabels[i + 1]));
        }
        return List.copyOf(result);
    }

    private record Field(String key, String group, String label, String type, List<String> operators,
                         String optionSource, List<AdvancedFilterCatalogRespVO.OptionVO> options,
                         Set<String> scenes, Map<String, String> expression, Map<String, String> relation) {}
}
