package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter.AdvancedFilterMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_INVALID;

@Service
public class AdvancedFilterService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final String IDENTITY = "身份与联系";
    private static final String STATUS = "状态与进度";
    private static final String PEOPLE = "归属与人员";
    private static final String PRODUCT = "产品与服务";
    private static final String MONEY = "金额与付款";
    private static final String TIME = "时间";
    private static final String EXTRA = "补充信息";
    private static final List<String> TEXT_OPS = List.of("contains", "not_contains", "eq", "ne", "is_empty", "is_not_empty");
    private static final List<String> SELECT_OPS = List.of("in", "not_in", "is_empty", "is_not_empty");
    private static final List<String> RANGE_OPS = List.of("eq", "gt", "gte", "lt", "lte", "between", "is_empty", "is_not_empty");
    private static final List<String> DATE_OPS = List.of("eq", "gt", "gte", "lt", "lte", "between", "relative", "is_empty", "is_not_empty");
    private static final Set<String> SCENES = Set.of("lead", "order", "lead_appeal", "duplicate_review", "registration", "student", "subordinate_sales");
    private static final Map<String, Field> FIELDS = fields();
    private static final List<AdvancedFilterCatalogRespVO.OptionVO> RELATIVE_DATES = options(
            "today", "今天", "yesterday", "昨天", "last_7_days", "近 7 天", "last_30_days", "近 30 天",
            "this_week", "本周", "this_month", "本月", "this_quarter", "本季度", "this_year", "本年");

    @Resource private AdvancedFilterMapper mapper;

    public AdvancedFilterCatalogRespVO catalog(String scene) {
        if (!SCENES.contains(scene)) throw exception(ADVANCED_FILTER_INVALID);
        return new AdvancedFilterCatalogRespVO(FIELDS.values().stream()
                .filter(field -> field.bindings.containsKey(scene))
                .map(field -> new AdvancedFilterCatalogRespVO.FieldVO(field.key, field.group, field.label,
                        field.type, field.operators, field.optionSource, field.options)).toList(), RELATIVE_DATES);
    }

    public AdvancedFilterCatalogRespVO catalog(String scene,
                                                List<AdvancedFilterCatalogRespVO.OptionVO> visibleUsers) {
        if (visibleUsers == null) return catalogWithoutVisibleUsers(scene);
        AdvancedFilterCatalogRespVO catalog = catalog(scene);
        return new AdvancedFilterCatalogRespVO(catalog.fields().stream().map(field ->
                "visible-users".equals(field.optionSource())
                        ? new AdvancedFilterCatalogRespVO.FieldVO(field.fieldKey(), field.group(), field.label(),
                        field.valueType(), field.operators(), null, visibleUsers)
                        : field).toList(), catalog.relativeDateOptions());
    }

    public AdvancedFilterCatalogRespVO catalogWithoutVisibleUsers(String scene) {
        AdvancedFilterCatalogRespVO catalog = catalog(scene);
        return new AdvancedFilterCatalogRespVO(catalog.fields().stream()
                .filter(field -> !"visible-users".equals(field.optionSource()))
                .toList(), catalog.relativeDateOptions());
    }

    public boolean hasConditions(AdvancedFilterGroupReqVO group) {
        return group != null && ((group.getConditions() != null && !group.getConditions().isEmpty())
                || (group.getGroups() != null && group.getGroups().stream().anyMatch(this::hasConditions)));
    }

    public List<Long> matchLeadIds(AdvancedFilterGroupReqVO group) {
        AdvancedFilterQuery query = buildIfPresent(group, "lead", Map.of());
        return query == null ? null : mapper.selectLeadIds(query);
    }
    public List<Long> matchOrderIds(AdvancedFilterGroupReqVO group) {
        AdvancedFilterQuery query = buildOrderQuery(group);
        return query == null ? null : mapper.selectOrderIds(query);
    }
    public AdvancedFilterQuery buildOrderQuery(AdvancedFilterGroupReqVO group) { return buildIfPresent(group, "order", Map.of()); }
    public List<Long> matchAppealIds(AdvancedFilterGroupReqVO group) {
        AdvancedFilterQuery query = buildIfPresent(group, "lead_appeal", Map.of());
        return query == null ? null : mapper.selectAppealIds(query);
    }
    public List<Long> matchAppealIds(String keyword, AdvancedFilterGroupReqVO group) {
        return intersect(keywordIds(keyword, value -> mapper.selectAppealIdsByKeyword(TenantContextHolder.getTenantId(), value)),
                matchAppealIds(group));
    }
    public List<Long> matchDuplicateReviewIds(AdvancedFilterGroupReqVO group) {
        AdvancedFilterQuery query = buildIfPresent(group, "duplicate_review", Map.of());
        return query == null ? null : mapper.selectDuplicateReviewIds(query);
    }
    public List<Long> matchDuplicateReviewIds(String keyword, AdvancedFilterGroupReqVO group) {
        return intersect(keywordIds(keyword, value -> mapper.selectDuplicateReviewIdsByKeyword(TenantContextHolder.getTenantId(), value)),
                matchDuplicateReviewIds(group));
    }
    public List<Long> matchRegistrationCaseIds(AdvancedFilterGroupReqVO group) {
        AdvancedFilterQuery query = buildIfPresent(group, "registration", Map.of());
        return query == null ? null : mapper.selectRegistrationCaseIds(query);
    }
    public List<Long> matchStudentPersonIds(AdvancedFilterGroupReqVO group, Long userId) {
        AdvancedFilterQuery query = buildIfPresent(group, "student", Map.of("userId", userId));
        return query == null ? null : mapper.selectStudentPersonIds(query);
    }

    public boolean matches(String scene, AdvancedFilterGroupReqVO group, Function<String, Object> valueProvider) {
        if (group == null) return true;
        validateShape(group, 0, new int[]{0});
        return matchesGroup(scene, group, valueProvider);
    }

    private List<Long> keywordIds(String keyword, Function<String, List<Long>> query) {
        return keyword == null || keyword.isBlank() ? null : query.apply(keyword.trim());
    }

    private List<Long> intersect(List<Long> left, List<Long> right) {
        if (left == null) return right;
        if (right == null) return left;
        Set<Long> allowed = new HashSet<>(right);
        return left.stream().filter(allowed::contains).toList();
    }

    private AdvancedFilterQuery buildIfPresent(AdvancedFilterGroupReqVO group, String scene, Map<String, Object> extra) {
        if (group == null) return null;
        validateShape(group, 0, new int[]{0});
        if (!hasConditions(group)) return null;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", TenantContextHolder.getTenantId());
        params.putAll(extra);
        String sql = groupSql(group, scene, params);
        if (sql.isBlank()) throw exception(ADVANCED_FILTER_INVALID);
        return new AdvancedFilterQuery(sql, params);
    }

    private void validateShape(AdvancedFilterGroupReqVO group, int depth, int[] count) {
        if (group == null || group.getConditions() == null || group.getGroups() == null
                || group.getConditions().stream().anyMatch(Objects::isNull)
                || group.getGroups().stream().anyMatch(Objects::isNull)
                || depth > 1 || group.getGroups().size() > 5
                || !("AND".equals(group.getLogic()) || "OR".equals(group.getLogic()))) throw exception(ADVANCED_FILTER_INVALID);
        count[0] += group.getConditions().size();
        if (count[0] > 20 || depth > 0 && group.getConditions().isEmpty()
                || (depth == 1 && !group.getGroups().isEmpty())) throw exception(ADVANCED_FILTER_INVALID);
        group.getGroups().forEach(child -> validateShape(child, depth + 1, count));
    }

    private String groupSql(AdvancedFilterGroupReqVO group, String scene, Map<String, Object> params) {
        List<String> parts = new ArrayList<>();
        if ("AND".equals(group.getLogic())) {
            Map<String, List<String>> related = new LinkedHashMap<>();
            for (AdvancedFilterConditionReqVO condition : group.getConditions()) {
                Compiled compiled = compile(condition, scene, params);
                if (compiled.relation == null || compiled.negateRelation) parts.add(compiled.sql());
                else related.computeIfAbsent(compiled.relation, ignored -> new ArrayList<>()).add(compiled.predicate);
            }
            related.forEach((relation, predicates) -> parts.add("EXISTS (" + relation + " AND "
                    + String.join(" AND ", predicates) + ")"));
        } else group.getConditions().forEach(condition -> parts.add(compile(condition, scene, params).sql()));
        group.getGroups().forEach(child -> parts.add("(" + groupSql(child, scene, params) + ")"));
        return parts.isEmpty() ? "" : String.join(" " + group.getLogic() + " ", parts);
    }

    private Compiled compile(AdvancedFilterConditionReqVO condition, String scene, Map<String, Object> params) {
        Field field = FIELDS.get(condition.getFieldKey());
        Binding binding = field == null ? null : field.bindings.get(scene);
        if (field == null || binding == null || !field.operators.contains(condition.getOperator())) throw exception(ADVANCED_FILTER_INVALID);
        validateOperands(field, condition);
        String expression = binding.expression;
        String operator = condition.getOperator();
        String predicate;
        boolean negateRelation = false;
        if ("is_empty".equals(operator) || "is_not_empty".equals(operator)) {
            predicate = emptyPredicate(field.type, expression);
            if ("is_not_empty".equals(operator)) predicate = "NOT (" + predicate + ")";
            negateRelation = binding.relation != null && "is_empty".equals(operator);
            if (negateRelation) predicate = "NOT (" + predicate + ")";
        } else if ("between".equals(operator)) {
            Object from = typed(field.type, condition.getValueFrom()), to = typed(field.type, condition.getValueTo());
            predicate = expression + " BETWEEN " + ref(params, from) + " AND " + ref(params, to);
        } else if ("relative".equals(operator)) {
            LocalDateTime[] range = relativeRange(String.valueOf(condition.getValue()));
            predicate = expression + " >= " + ref(params, range[0]) + " AND " + expression + " < " + ref(params, range[1]);
        } else if ("in".equals(operator) || "not_in".equals(operator)) {
            List<?> values = condition.getValue() instanceof Collection<?> collection ? collection.stream().toList() : List.of();
            List<String> refs = values.stream().map(value -> ref(params, typed(field.type, value))).toList();
            predicate = expression + " IN (" + String.join(",", refs) + ")";
            if ("not_in".equals(operator)) {
                negateRelation = binding.relation != null;
                if (!negateRelation) predicate = "NOT (" + predicate + ")";
            }
        } else {
            Object value = typed(field.type, condition.getValue());
            String valueRef = ref(params, value);
            predicate = switch (operator) {
                case "contains" -> expression + " LIKE CONCAT('%'," + valueRef + ",'%')";
                case "not_contains" -> binding.relation == null
                        ? "NOT (" + expression + " LIKE CONCAT('%'," + valueRef + ",'%'))"
                        : expression + " LIKE CONCAT('%'," + valueRef + ",'%')";
                case "eq" -> expression + " = " + valueRef;
                case "ne" -> binding.relation == null
                        ? "NOT (" + expression + " = " + valueRef + ")"
                        : expression + " = " + valueRef;
                case "gt" -> expression + " > " + valueRef;
                case "gte" -> expression + " >= " + valueRef;
                case "lt" -> expression + " < " + valueRef;
                case "lte" -> expression + " <= " + valueRef;
                default -> throw exception(ADVANCED_FILTER_INVALID);
            };
            negateRelation = binding.relation != null && Set.of("not_contains", "ne").contains(operator);
        }
        return new Compiled(binding.relation, predicate, negateRelation);
    }

    private boolean matchesGroup(String scene, AdvancedFilterGroupReqVO group, Function<String, Object> values) {
        boolean and = "AND".equals(group.getLogic());
        List<Boolean> matches = new ArrayList<>();
        for (AdvancedFilterConditionReqVO condition : group.getConditions()) {
            Field field = FIELDS.get(condition.getFieldKey());
            if (field == null || !field.bindings.containsKey(scene) || !field.operators.contains(condition.getOperator())) throw exception(ADVANCED_FILTER_INVALID);
            validateOperands(field, condition);
            matches.add(matchesValue(field, condition, values.apply(field.key)));
        }
        group.getGroups().forEach(child -> matches.add(matchesGroup(scene, child, values)));
        return matches.isEmpty() || (and ? matches.stream().allMatch(Boolean::booleanValue) : matches.stream().anyMatch(Boolean::booleanValue));
    }

    private boolean matchesValue(Field field, AdvancedFilterConditionReqVO condition, Object actual) {
        String operator = condition.getOperator();
        if ("is_empty".equals(operator)) return actual == null || actual.toString().isBlank();
        if ("is_not_empty".equals(operator)) return actual != null && !actual.toString().isBlank();
        if (actual == null) return false;
        if ("in".equals(operator) || "not_in".equals(operator)) {
            Collection<?> selected = condition.getValue() instanceof Collection<?> values ? values : List.of();
            Object typedActual = typed(field.type, actual);
            if (typedActual == null) return false;
            boolean found = selected.stream().map(value -> typed(field.type, value)).anyMatch(typedActual::equals);
            return "in".equals(operator) ? found : !found;
        }
        if ("relative".equals(operator)) {
            LocalDateTime value = (LocalDateTime) typed("date", actual);
            LocalDateTime[] range = relativeRange(String.valueOf(condition.getValue()));
            return !value.isBefore(range[0]) && value.isBefore(range[1]);
        }
        Object expected = typed(field.type, condition.getValue());
        if (Set.of("contains", "not_contains").contains(operator)) {
            boolean found = actual.toString().contains(String.valueOf(expected));
            return "contains".equals(operator) ? found : !found;
        }
        if ("between".equals(operator)) {
            Object typedActual = typed(field.type, actual);
            if (typedActual == null) return false;
            Comparable<Object> value = comparable(typedActual);
            return value.compareTo(typed(field.type, condition.getValueFrom())) >= 0
                    && value.compareTo(typed(field.type, condition.getValueTo())) <= 0;
        }
        Object typedActual = typed(field.type, actual);
        if (typedActual == null) return false;
        Comparable<Object> value = comparable(typedActual);
        int compared = value.compareTo(expected);
        return switch (operator) {
            case "eq" -> compared == 0; case "ne" -> compared != 0; case "gt" -> compared > 0;
            case "gte" -> compared >= 0; case "lt" -> compared < 0; case "lte" -> compared <= 0;
            default -> throw exception(ADVANCED_FILTER_INVALID);
        };
    }

    @SuppressWarnings("unchecked") private Comparable<Object> comparable(Object value) { return (Comparable<Object>) value; }
    private String emptyPredicate(String type, String expression) { return "text".equals(type) ? "(" + expression + " IS NULL OR TRIM(" + expression + ")='')" : expression + " IS NULL"; }

    private void validateOperands(Field field, AdvancedFilterConditionReqVO condition) {
        String operator = condition.getOperator();
        if (Set.of("is_empty", "is_not_empty").contains(operator)) return;
        if (Set.of("in", "not_in").contains(operator)) {
            if (!(condition.getValue() instanceof Collection<?> values)
                    || values.isEmpty() || values.size() > 100
                    || values.stream().map(value -> typed(field.type, value)).anyMatch(Objects::isNull)) {
                throw exception(ADVANCED_FILTER_INVALID);
            }
            return;
        }
        if ("between".equals(operator)) {
            Object from = typed(field.type, condition.getValueFrom());
            Object to = typed(field.type, condition.getValueTo());
            if (from == null || to == null || comparable(from).compareTo(to) > 0) {
                throw exception(ADVANCED_FILTER_INVALID);
            }
            return;
        }
        if ("relative".equals(operator)) {
            if (typed("text", condition.getValue()) == null) throw exception(ADVANCED_FILTER_INVALID);
            relativeRange(String.valueOf(condition.getValue()));
            return;
        }
        if (typed(field.type, condition.getValue()) == null) throw exception(ADVANCED_FILTER_INVALID);
    }

    private Object typed(String type, Object raw) {
        if (raw == null || raw.toString().isBlank()) return null;
        try {
            return switch (type) {
                case "number" -> new BigDecimal(raw.toString());
                case "date" -> parseDate(raw);
                default -> raw.toString().trim();
            };
        } catch (NumberFormatException | DateTimeParseException | ArithmeticException ex) { throw exception(ADVANCED_FILTER_INVALID); }
    }
    private LocalDateTime parseDate(Object raw) {
        if (raw instanceof LocalDateTime value) return value;
        if (raw instanceof LocalDate value) return value.atStartOfDay();
        String value = raw.toString();
        if (value.matches("^-?\\d+$")) return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), BEIJING);
        try { return LocalDateTime.parse(value); }
        catch (DateTimeParseException ignored) { return OffsetDateTime.parse(value).atZoneSameInstant(BEIJING).toLocalDateTime(); }
    }
    private LocalDateTime[] relativeRange(String value) {
        LocalDate today = LocalDate.now(BEIJING), start, end;
        switch (value) {
            case "today" -> { start = today; end = today.plusDays(1); }
            case "yesterday" -> { start = today.minusDays(1); end = today; }
            case "last_7_days" -> { start = today.minusDays(6); end = today.plusDays(1); }
            case "last_30_days" -> { start = today.minusDays(29); end = today.plusDays(1); }
            case "this_week" -> { start = today.with(DayOfWeek.MONDAY); end = start.plusWeeks(1); }
            case "this_month" -> { start = today.withDayOfMonth(1); end = start.plusMonths(1); }
            case "this_quarter" -> { start = LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1); end = start.plusMonths(3); }
            case "this_year" -> { start = today.with(TemporalAdjusters.firstDayOfYear()); end = start.plusYears(1); }
            default -> throw exception(ADVANCED_FILTER_INVALID);
        }
        return new LocalDateTime[]{start.atStartOfDay(), end.atStartOfDay()};
    }
    private String ref(Map<String, Object> params, Object value) {
        if (value == null) throw exception(ADVANCED_FILTER_INVALID);
        String key = "p" + params.size(); params.put(key, value); return "#{query.parameters." + key + "}";
    }

    private static Map<String, Field> fields() {
        Map<String, Field> result = new LinkedHashMap<>();
        String personFromLead = "SELECT 1 FROM zsjos_person p WHERE p.id=l.person_id AND p.tenant_id=l.tenant_id AND p.deleted=b'0'";
        String personFromOrder = "SELECT 1 FROM zsjos_person p WHERE p.id=o.person_id AND p.tenant_id=o.tenant_id AND p.deleted=b'0'";
        String personFromAppeal = "SELECT 1 FROM zsjos_lead al JOIN zsjos_person p ON p.id=al.person_id AND p.deleted=b'0' WHERE al.id=a.lead_id AND al.tenant_id=a.tenant_id AND al.deleted=b'0'";
        String personFromRegistration = "SELECT 1 FROM zsjos_order ro JOIN zsjos_person p ON p.id=ro.person_id AND p.deleted=b'0' WHERE ro.id=rc.order_id AND ro.tenant_id=rc.tenant_id AND ro.deleted=b'0'";
        String leadFromOrder = "SELECT 1 FROM zsjos_lead rl WHERE rl.id=o.lead_id AND rl.tenant_id=o.tenant_id AND rl.deleted=b'0'";
        String leadFromAppeal = "SELECT 1 FROM zsjos_lead rl WHERE rl.id=a.lead_id AND rl.tenant_id=a.tenant_id AND rl.deleted=b'0'";
        String leadFromRegistration = "SELECT 1 FROM zsjos_order ro JOIN zsjos_lead rl ON rl.id=ro.lead_id AND rl.deleted=b'0' WHERE ro.id=rc.order_id AND ro.tenant_id=rc.tenant_id AND ro.deleted=b'0'";
        String leadFromStudent = "SELECT 1 FROM zsjos_lead rl WHERE rl.person_id=p.id AND rl.tenant_id=p.tenant_id AND rl.deleted=b'0'";
        String orderFromLead = "SELECT 1 FROM zsjos_order ro LEFT JOIN zsjos_order_item oi ON oi.order_id=ro.id AND oi.deleted=b'0' WHERE ro.person_id=l.person_id AND ro.tenant_id=l.tenant_id AND ro.deleted=b'0'";
        String orderFromAppeal = "SELECT 1 FROM zsjos_lead al JOIN zsjos_order ro ON ro.person_id=al.person_id AND ro.deleted=b'0' LEFT JOIN zsjos_order_item oi ON oi.order_id=ro.id AND oi.deleted=b'0' WHERE al.id=a.lead_id AND al.tenant_id=a.tenant_id AND al.deleted=b'0'";
        String orderFromRegistration = "SELECT 1 FROM zsjos_order ro LEFT JOIN zsjos_order_item oi ON oi.order_id=ro.id AND oi.deleted=b'0' WHERE ro.id=rc.order_id AND ro.tenant_id=rc.tenant_id AND ro.deleted=b'0'";
        String serviceFromStudent = "SELECT 1 FROM zsjos_service_relation sr JOIN zsjos_order ro ON ro.id=sr.order_id AND ro.tenant_id=sr.tenant_id AND ro.deleted=b'0' LEFT JOIN zsjos_order_item oi ON oi.id=sr.order_item_id AND oi.tenant_id=sr.tenant_id AND oi.deleted=b'0' LEFT JOIN zsjos_registration_case_route scr ON scr.registration_case_id=sr.registration_case_id AND scr.assignee_user_id=#{query.parameters.userId} AND scr.selected=b'1' AND scr.tenant_id=sr.tenant_id AND scr.deleted=b'0' WHERE sr.person_id=p.id AND sr.tenant_id=p.tenant_id AND sr.status='active' AND sr.deleted=b'0' AND (sr.owner_user_id=#{query.parameters.userId} OR scr.id IS NOT NULL)";
        String itemFromOrder = "SELECT 1 FROM zsjos_order_item oi WHERE oi.order_id=o.id AND oi.tenant_id=o.tenant_id AND oi.deleted=b'0'";

        add(result, text("person.name", IDENTITY, "姓名", bind("lead", "p.name", personFromLead, "order", "p.name", personFromOrder, "lead_appeal", "p.name", personFromAppeal, "registration", "p.name", personFromRegistration, "student", "p.name", null)));
        add(result, text("person.mobile", IDENTITY, "手机号", bind("lead", "p.mobile", personFromLead, "order", "p.mobile", personFromOrder, "lead_appeal", "p.mobile", personFromAppeal, "registration", "p.mobile", personFromRegistration, "student", "p.mobile", null)));
        add(result, text("person.wechatId", IDENTITY, "微信号", bind("lead", "p.wechat_id", personFromLead, "order", "p.wechat_id", personFromOrder, "lead_appeal", "p.wechat_id", personFromAppeal, "registration", "p.wechat_id", personFromRegistration, "student", "p.wechat_id", null)));
        add(result, select("person.identityStatus", STATUS, "身份状态", options("lead", "潜在学员", "active", "正式学员"), bind("lead", "p.identity_status", personFromLead, "order", "p.identity_status", personFromOrder, "student", "p.identity_status", null)));

        add(result, text("lead.leadNo", IDENTITY, "客资编号", bind("lead", "l.lead_no", null, "order", "rl.lead_no", leadFromOrder, "lead_appeal", "rl.lead_no", leadFromAppeal, "registration", "rl.lead_no", leadFromRegistration, "student", "rl.lead_no", leadFromStudent, "duplicate_review", "ml.lead_no", "SELECT 1 FROM zsjos_lead ml WHERE ml.id=dr.matched_lead_id AND ml.tenant_id=dr.tenant_id AND ml.deleted=b'0'")));
        add(result, text("lead.name", IDENTITY, "提交姓名", leadBind("submitted_name", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text("lead.mobile", IDENTITY, "提交手机号", leadBind("submitted_mobile", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text("lead.wechatId", IDENTITY, "提交微信号", leadBind("submitted_wechat_id", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, select("lead.status", STATUS, "客资状态", options("submitted", "已提交", "suspended", "已挂起", "valid", "有效", "invalid", "无效", "won", "已成交", "closed", "已关闭"), leadBind("status", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, select("lead.assignmentStatus", STATUS, "分配状态", options("unassigned", "未分配", "pending_acceptance", "待接单", "owned", "已归属", "public_pool", "抢单池", "recycle_pending", "回收待处理", "closed", "已关闭"), leadBind("assignment_status", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource("lead.sourceChannel", EXTRA, "来源渠道", "dict:zsjos_lead_source_channel", leadBind("source_channel_id", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource("lead.category", EXTRA, "客资分类", "dict:zsjos_lead_category", leadBind("lead_category", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource("lead.sourceUserId", PEOPLE, "提交人", "visible-users",
                leadSubmitterFilterBind(leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource("lead.ownerUserId", PEOPLE, "负责人", "visible-users", leadBind("owner_user_id", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date("lead.submittedAt", TIME, "客资提交时间", leadBind("submitted_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date("lead.lastFollowUpAt", TIME, "最近跟进时间", leadBind("last_follow_up_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date("lead.nextFollowUpAt", TIME, "下次跟进时间", leadBind("next_follow_up_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date("lead.ownershipStartedAt", TIME, "持有起点", leadBind("ownership_started_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text("lead.remark", EXTRA, "客资备注", leadBind("remark", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));

        add(result, text("opportunity.expectedProduct", PRODUCT, "预计产品", bind("lead", "op.expected_product_summary", opportunityFrom("l.person_id", "l.tenant_id"), "order", "op.expected_product_summary", opportunityFrom("o.person_id", "o.tenant_id"))));
        add(result, select("opportunity.status", STATUS, "推进状态", options("open", "待推进", "following", "跟进中", "lost", "已流失", "deal_pending_approval", "成交审批中", "won", "已赢单"), bind("lead", "op.status", opportunityFrom("l.person_id", "l.tenant_id"), "order", "op.status", opportunityFrom("o.person_id", "o.tenant_id"))));
        add(result, text("opportunity.lostReason", EXTRA, "流失原因", bind("lead", "op.lost_reason", opportunityFrom("l.person_id", "l.tenant_id"), "order", "op.lost_reason", opportunityFrom("o.person_id", "o.tenant_id"))));

        add(result, text("order.orderNo", IDENTITY, "订单号", orderBind("order_no", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, select("order.status", STATUS, "订单状态", options("pending_approval", "审批中", "revision_required", "待修改", "effective", "已生效", "superseded", "已被接续", "terminated", "已终止"), orderBind("status", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, select("order.type", STATUS, "订单类型", options("first_purchase", "首购", "repurchase", "复购"), orderBind("order_type", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.buyerName", IDENTITY, "购买方", orderBind("buyer_name", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.studentName", IDENTITY, "学员姓名", orderBind("student_name", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.studentMobile", IDENTITY, "学员手机号", orderBind("student_mobile", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.studentWechatId", IDENTITY, "学员微信号", orderBind("student_wechat_id", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource("order.submitterUserId", PEOPLE, "订单提交人", "visible-users", orderBind("submitter_user_id", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource("order.formalSalesUserId", PEOPLE, "正式销售", "visible-users", orderBind("formal_sales_user_id", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, number("order.totalAmount", MONEY, "订单总金额", orderBind("total_amount", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource("order.studentNature", IDENTITY, "学员性质", "dict:zsjos_order_student_nature", orderBind("student_nature", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.region", IDENTITY, "所在地区", orderExpression("CONCAT_WS('/', %s.province_name, %s.city_name)", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource("order.feeMode", MONEY, "缴费方式", "dict:zsjos_order_fee_mode", orderBind("fee_mode", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource("order.paymentMethod", MONEY, "支付方式", "dict:zsjos_order_payment_method", orderBind("payment_method", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, date("order.customerPaidAt", MONEY, "客户付款时间", orderBind("customer_paid_at", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.classType", PRODUCT, "开通班种", orderBind("class_type", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource("order.servicePeriod", PRODUCT, "服务周期", "dict:zsjos_order_service_period", orderBind("service_period", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource("order.studentSource", EXTRA, "学生来源", "dict:zsjos_order_student_source", orderBind("student_source", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, date("order.submittedAt", TIME, "订单提交时间", orderBind("submitted_at", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, date("order.effectiveAt", TIME, "订单生效时间", orderBind("effective_at", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.remark", EXTRA, "订单备注", orderBind("remark", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.specialRequirements", EXTRA, "学员特殊要求", orderBind("student_special_requirements", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("order.materialDelivery", EXTRA, "教材邮递联系", orderBind("material_delivery_contact", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, select("order.hasVoucher", EXTRA, "缴费凭证", options("present", "有附件", "absent", "无附件"), orderExpression("CASE WHEN NULLIF(TRIM(%s.payment_voucher_refs),'') IS NULL THEN 'absent' ELSE 'present' END", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text("orderItem.product", PRODUCT, "成交商品或课程", bind("lead", "oi.product_snapshot", orderFromLead, "order", "oi.product_snapshot", itemFromOrder, "lead_appeal", "oi.product_snapshot", orderFromAppeal, "registration", "oi.product_snapshot", orderFromRegistration, "student", "oi.product_snapshot", serviceFromStudent)));
        add(result, number("orderItem.payableAmount", MONEY, "商品应付金额", bind("lead", "oi.payable_amount", orderFromLead, "order", "oi.payable_amount", itemFromOrder, "lead_appeal", "oi.payable_amount", orderFromAppeal, "registration", "oi.payable_amount", orderFromRegistration, "student", "oi.payable_amount", serviceFromStudent)));

        addAppealFields(result); addDuplicateReviewFields(result); addRegistrationFields(result);
        addStudentFields(result, serviceFromStudent); addSubordinateFields(result);
        return result;
    }

    private static void addAppealFields(Map<String, Field> result) {
        add(result, number("appeal.roundNo", STATUS, "申诉轮次", bind("lead_appeal", "a.round_no", null)));
        add(result, select("appeal.reviewStage", STATUS, "审核阶段", options("sales_manager", "销售主管", "quality", "质控部门", "chairman", "董事长"), bind("lead_appeal", "a.review_stage", null)));
        add(result, select("appeal.status", STATUS, "申诉状态", options("sales_manager_reviewing", "销售主管复核中", "quality_reviewing", "质控复核中", "chairman_reviewing", "董事长终审中", "overturned", "已改判有效", "upheld", "维持无效", "withdrawn", "已撤回"), bind("lead_appeal", "a.status", null)));
        add(result, selectSource("appeal.applicantUserId", PEOPLE, "申请人", "visible-users", bind("lead_appeal", "a.applicant_user_id", null)));
        add(result, selectSource("appeal.reviewerUserId", PEOPLE, "审核人", "visible-users", bind("lead_appeal", "a.reviewer_user_id", null)));
        add(result, text("appeal.reason", EXTRA, "申诉原因", bind("lead_appeal", "a.reason", null)));
        add(result, text("appeal.decisionReason", EXTRA, "裁决意见", bind("lead_appeal", "a.decision_reason", null)));
        add(result, date("appeal.submittedAt", TIME, "申诉提交时间", bind("lead_appeal", "a.submitted_at", null)));
        add(result, date("appeal.decidedAt", TIME, "申诉处理时间", bind("lead_appeal", "a.decided_at", null)));
        add(result, select("appeal.hasEvidence", EXTRA, "申诉附件", options("present", "有附件", "absent", "无附件"), bind("lead_appeal", "CASE WHEN NULLIF(TRIM(a.evidence_refs),'') IS NULL THEN 'absent' ELSE 'present' END", null)));
    }
    private static void addDuplicateReviewFields(Map<String, Field> result) {
        add(result, text("review.submittedName", IDENTITY, "提交姓名", bind("duplicate_review", json("name"), null)));
        add(result, text("review.submittedMobile", IDENTITY, "提交手机号", bind("duplicate_review", json("mobile"), null)));
        add(result, text("review.submittedWechatId", IDENTITY, "提交微信号", bind("duplicate_review", json("wechatId"), null)));
        add(result, select("review.status", STATUS, "复核状态", options("pending", "待处理", "completed", "已处理"), bind("duplicate_review", "dr.status", null)));
        add(result, select("review.resultType", STATUS, "复核结果", options("reuse_person", "复用已有客户", "reactivate_lead", "激活客资", "notify_owner", "提醒所属销售", "create_new", "创建新客资"), bind("duplicate_review", "dr.result_type", null)));
        add(result, selectSource("review.submitterUserId", PEOPLE, "复核提交人", "visible-users", bind("duplicate_review", "dr.submitter_user_id", null)));
        add(result, selectSource("review.reviewerUserId", PEOPLE, "复核人", "visible-users", bind("duplicate_review", "dr.reviewer_user_id", null)));
        add(result, selectSource("review.selectedSalesUserId", PEOPLE, "选定销售", "visible-users", bind("duplicate_review", "dr.selected_sales_user_id", null)));
        add(result, text("review.submissionSource", EXTRA, "提交来源", bind("duplicate_review", "dr.submission_source_type", null)));
        add(result, text("review.matchRules", EXTRA, "匹配规则", bind("duplicate_review", "dr.match_rules", null)));
        add(result, text("review.opinion", EXTRA, "复核意见", bind("duplicate_review", "dr.review_opinion", null)));
        add(result, date("review.submittedAt", TIME, "复核提交时间", bind("duplicate_review", "dr.create_time", null)));
        add(result, date("review.reviewedAt", TIME, "复核完成时间", bind("duplicate_review", "dr.reviewed_at", null)));
        add(result, select("review.hasAttachments", EXTRA, "复核附件", options("present", "有附件", "absent", "无附件"), bind("duplicate_review", "CASE WHEN NULLIF(TRIM(dr.review_attachments),'') IS NULL THEN 'absent' ELSE 'present' END", null)));
    }
    private static void addRegistrationFields(Map<String, Field> result) {
        add(result, select("registration.status", STATUS, "履约状态", options("pending", "待处理", "processing", "处理中", "completed", "已完成", "cancelled", "已取消"), bind("registration", "rc.status", null)));
        add(result, selectSource("registration.plannerUserId", PEOPLE, "学习规划师", "visible-users", bind("registration", "rc.study_planner_user_id", null)));
        add(result, date("registration.approvedAt", TIME, "报名审核时间", bind("registration", "rc.registration_approved_at", null)));
        add(result, date("registration.completedAt", TIME, "履约完成时间", bind("registration", "rc.completed_at", null)));
        add(result, date("registration.cancelledAt", TIME, "履约取消时间", bind("registration", "rc.cancelled_at", null)));
        add(result, text("registration.cancelReason", EXTRA, "履约取消原因", bind("registration", "rc.cancel_reason", null)));
        String checklist = "SELECT 1 FROM zsjos_registration_case_checklist_item ci LEFT JOIN zsjos_registration_item_attachment cia ON cia.checklist_item_id=ci.id AND cia.deleted=b'0' WHERE ci.registration_case_id=rc.id AND ci.tenant_id=rc.tenant_id AND ci.deleted=b'0'";
        add(result, text("registration.checklistTitle", PRODUCT, "清单项目", bind("registration", "ci.title_snapshot", checklist)));
        add(result, select("registration.checklistStatus", STATUS, "清单完成状态", options("completed", "已完成", "pending", "未完成"), bind("registration", "CASE WHEN ci.checked=b'1' THEN 'completed' ELSE 'pending' END", checklist)));
        add(result, select("registration.hasChecklistAttachment", EXTRA, "清单附件", options("present", "有附件", "absent", "无附件"), bind("registration", "CASE WHEN cia.id IS NULL THEN 'absent' ELSE 'present' END", checklist)));
        String route = "SELECT 1 FROM zsjos_registration_case_route rr WHERE rr.registration_case_id=rc.id AND rr.tenant_id=rc.tenant_id AND rr.deleted=b'0' AND rr.selected=b'1'";
        add(result, text("registration.routeDepartment", PRODUCT, "流转部门", bind("registration", "rr.department_name_snapshot", route)));
        add(result, selectSource("registration.routeAssignee", PEOPLE, "流转负责人", "visible-users", bind("registration", "rr.assignee_user_id", route)));
    }
    private static void addStudentFields(Map<String, Field> result, String relation) {
        add(result, select("service.status", STATUS, "服务状态", options("active", "服务中", "paused", "已暂停", "completed", "已完成", "terminated", "已终止"), bind("student", "sr.status", relation)));
        add(result, selectSource("service.ownerUserId", PEOPLE, "服务负责人", "visible-users", bind("student", "sr.owner_user_id", relation)));
        add(result, date("service.activatedAt", TIME, "服务激活时间", bind("student", "sr.activated_at", relation)));
        add(result, date("service.pausedAt", TIME, "服务暂停时间", bind("student", "sr.paused_at", relation)));
        add(result, date("service.completedAt", TIME, "服务完成时间", bind("student", "sr.completed_at", relation)));
        add(result, date("service.terminatedAt", TIME, "服务终止时间", bind("student", "sr.terminated_at", relation)));
        add(result, text("service.pauseReason", EXTRA, "暂停原因", bind("student", "sr.pause_reason", relation)));
        add(result, text("service.terminationReason", EXTRA, "终止原因", bind("student", "sr.termination_reason", relation)));
    }
    private static void addSubordinateFields(Map<String, Field> result) {
        Map<String, Binding> scene = bind("subordinate_sales", "1", null);
        add(result, text("subordinate.name", IDENTITY, "姓名", scene));
        add(result, text("subordinate.username", IDENTITY, "账号", scene));
        add(result, text("subordinate.mobile", IDENTITY, "手机号", scene));
        add(result, select("subordinate.accountStatus", STATUS, "账号状态", options("0", "启用", "1", "停用"), scene));
        add(result, select("subordinate.presence", STATUS, "在岗状态", options("online", "在线", "offline", "离线"), scene));
        add(result, select("subordinate.accepting", STATUS, "接单状态", options("true", "开启", "false", "关闭"), scene));
        add(result, select("subordinate.eligible", STATUS, "接单资格", options("true", "具备资格", "false", "暂无资格"), scene));
        add(result, select("subordinate.newcomerPoolStatus", STATUS, "新人池状态", options("active", "启用", "inactive", "未启用"), scene));
        add(result, number("subordinate.todayPendingCount", "业务指标", "今日待办数", scene));
        add(result, select("subordinate.todayFollowUpStatus", "业务指标", "今日跟进状态", options("completed", "已完成", "pending", "待完成"), scene));
        add(result, number("subordinate.firstFollowTimeoutCount", "业务指标", "首次跟进超时数", scene));
        add(result, number("subordinate.suspendedLeadCount", "业务指标", "挂起客资数", scene));
        add(result, number("subordinate.validLeadCount", "业务指标", "有效客资数", scene));
        add(result, number("subordinate.convertedLeadCount", "业务指标", "成交客资数", scene));
        add(result, number("subordinate.effectiveOrderCount", "业务指标", "生效订单数", scene));
        add(result, number("subordinate.effectiveOrderAmount", "业务指标", "生效订单金额", scene));
    }

    private static Map<String, Binding> leadBind(String column, String order, String appeal, String registration, String student) {
        return bind("lead", "l." + column, null, "order", "rl." + column, order, "lead_appeal", "rl." + column, appeal, "registration", "rl." + column, registration, "student", "rl." + column, student);
    }
    private static Map<String, Binding> leadSubmitterFilterBind(String order, String appeal, String registration, String student) {
        String leadExpression = "CASE WHEN l.source_provider_recorded = b'1' AND l.source_type = 'sales_self_sourced' THEN l.source_provider_user_id ELSE l.source_user_id END";
        String orderExpression = "CASE WHEN rl.source_provider_recorded = b'1' AND rl.source_type = 'sales_self_sourced' THEN rl.source_provider_user_id ELSE rl.source_user_id END";
        return bind("lead", leadExpression, null, "order", orderExpression, order,
                "lead_appeal", orderExpression, appeal, "registration", orderExpression, registration,
                "student", orderExpression, student);
    }
    private static Map<String, Binding> orderBind(String column, String lead, String appeal, String registration, String student) {
        return bind("lead", "ro." + column, lead, "order", "o." + column, null, "lead_appeal", "ro." + column, appeal, "registration", "ro." + column, registration, "student", "ro." + column, student);
    }
    private static Map<String, Binding> orderExpression(String pattern, String lead, String appeal, String registration, String student) {
        return bind("lead", pattern.formatted("ro", "ro"), lead, "order", pattern.formatted("o", "o"), null, "lead_appeal", pattern.formatted("ro", "ro"), appeal, "registration", pattern.formatted("ro", "ro"), registration, "student", pattern.formatted("ro", "ro"), student);
    }
    private static String opportunityFrom(String personExpression, String tenantExpression) { return "SELECT 1 FROM zsjos_opportunity op WHERE op.person_id=" + personExpression + " AND op.tenant_id=" + tenantExpression + " AND op.deleted=b'0'"; }
    private static String json(String key) { return "JSON_UNQUOTE(JSON_EXTRACT(dr.submission_snapshot, '$." + key + "'))"; }
    private static Map<String, Binding> bind(Object... values) {
        Map<String, Binding> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 3) result.put((String) values[i], new Binding((String) values[i + 1], (String) values[i + 2]));
        return result;
    }
    private static void add(Map<String, Field> fields, Field field) { fields.put(field.key, field); }
    private static Field text(String key, String group, String label, Map<String, Binding> bindings) { return field(key, group, label, "text", TEXT_OPS, null, List.of(), bindings); }
    private static Field number(String key, String group, String label, Map<String, Binding> bindings) { return field(key, group, label, "number", RANGE_OPS, null, List.of(), bindings); }
    private static Field date(String key, String group, String label, Map<String, Binding> bindings) { return field(key, group, label, "date", DATE_OPS, null, List.of(), bindings); }
    private static Field select(String key, String group, String label, List<AdvancedFilterCatalogRespVO.OptionVO> options, Map<String, Binding> bindings) { return field(key, group, label, "select", SELECT_OPS, null, options, bindings); }
    private static Field selectSource(String key, String group, String label, String source, Map<String, Binding> bindings) { return field(key, group, label, "select", SELECT_OPS, source, List.of(), bindings); }
    private static Field field(String key, String group, String label, String type, List<String> operators, String optionSource, List<AdvancedFilterCatalogRespVO.OptionVO> options, Map<String, Binding> bindings) { return new Field(key, group, label, type, operators, optionSource, options, bindings); }
    private static List<AdvancedFilterCatalogRespVO.OptionVO> options(String... valuesAndLabels) {
        List<AdvancedFilterCatalogRespVO.OptionVO> result = new ArrayList<>();
        for (int i = 0; i < valuesAndLabels.length; i += 2) result.add(new AdvancedFilterCatalogRespVO.OptionVO(valuesAndLabels[i], valuesAndLabels[i + 1]));
        return List.copyOf(result);
    }
    private record Binding(String expression, String relation) {}
    private record Field(String key, String group, String label, String type, List<String> operators, String optionSource, List<AdvancedFilterCatalogRespVO.OptionVO> options, Map<String, Binding> bindings) {}
    private record Compiled(String relation, String predicate, boolean negateRelation) {
        String sql() { if (relation == null) return predicate; return (negateRelation ? "NOT EXISTS (" : "EXISTS (") + relation + " AND " + predicate + ")"; }
    }
}
