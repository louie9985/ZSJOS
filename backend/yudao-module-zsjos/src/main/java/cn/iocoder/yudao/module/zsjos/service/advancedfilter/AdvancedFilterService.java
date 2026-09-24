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

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFieldCatalog.SCENES;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_INVALID;

@Service
public class AdvancedFilterService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final String DURATION_FIELD_KEY = "duration.diff";
    private static final List<String> DURATION_OPS = List.of("gt", "gte", "lt", "lte", "between");
    private static final Map<String, BigDecimal> DURATION_UNIT_FACTORS = Map.of(
            "minute", BigDecimal.ONE, "hour", BigDecimal.valueOf(60), "day", BigDecimal.valueOf(1440));
    private static final Map<String, Field> FIELDS = AdvancedFilterFieldCatalog.fields();
    private static final List<AdvancedFilterCatalogRespVO.OptionVO> RELATIVE_DATES = options(
            "today", "今天", "yesterday", "昨天", "last_7_days", "近 7 天", "last_30_days", "近 30 天",
            "this_week", "本周", "this_month", "本月", "this_quarter", "本季度", "this_year", "本年");

    @Resource private AdvancedFilterMapper mapper;
    @Resource private LeadFilterOrganizationService leadFilterOrganizations;
    @Resource private cn.iocoder.yudao.module.zsjos.service.cashback.FinanceTraceService financeTrace;

    public AdvancedFilterCatalogRespVO catalog(String scene) {
        if (!SCENES.contains(scene)) throw exception(ADVANCED_FILTER_INVALID);
        List<AdvancedFilterCatalogRespVO.FieldVO> fields = new ArrayList<>(FIELDS.values().stream()
                .filter(field -> field.bindings().containsKey(scene))
                .map(AdvancedFilterMetadata::describe).toList());
        List<AdvancedFilterCatalogRespVO.OptionVO> dateOptions = fields.stream()
                .filter(field -> "date".equals(field.valueType()))
                .map(field -> new AdvancedFilterCatalogRespVO.OptionVO(field.fieldKey(), field.label()))
                .toList();
        if (dateOptions.size() >= 2) {
            fields.add(AdvancedFilterMetadata.duration(scene, DURATION_OPS, dateOptions));
        }
        return new AdvancedFilterCatalogRespVO(fields, RELATIVE_DATES);
    }

    public AdvancedFilterCatalogRespVO catalog(String scene,
                                                List<AdvancedFilterCatalogRespVO.OptionVO> visibleUsers) {
        if (visibleUsers == null) return catalogWithoutVisibleUsers(scene);
        AdvancedFilterCatalogRespVO catalog = catalog(scene);
        return new AdvancedFilterCatalogRespVO(catalog.fields().stream().map(field ->
                "visible-users".equals(field.optionSource())
                        ? field.withResolvedOptions(visibleUsers)
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

    public void validate(String scene, AdvancedFilterGroupReqVO group) {
        if (group == null) throw exception(ADVANCED_FILTER_INVALID);
        validateShape(group, 0, new int[]{0});
        if (hasConditions(group)) {
            groupSql(group, scene, new LinkedHashMap<>(Map.of("validateOnly", true)));
        } else if (!SCENES.contains(scene)) {
            throw exception(ADVANCED_FILTER_INVALID);
        }
    }

    public boolean supportsScene(String scene) {
        return SCENES.contains(scene);
    }

    public List<Long> matchFinanceIds(String scene, AdvancedFilterGroupReqVO group) {
        if (!Set.of("cashback", "withdrawal").contains(scene)) throw exception(ADVANCED_FILTER_INVALID);
        AdvancedFilterQuery query = buildIfPresent(group, scene, Map.of());
        return query == null ? null : "cashback".equals(scene) ? mapper.selectCashbackIds(query) : mapper.selectWithdrawalIds(query);
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
        return matchStudentPersonIds(group, userId, false);
    }

    public List<Long> matchStudentPersonIds(AdvancedFilterGroupReqVO group, Long userId, boolean tenantReadAll) {
        AdvancedFilterQuery query = buildIfPresent(group, "student", Map.of("userId", userId, "tenantReadAll", tenantReadAll));
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
        if (DURATION_FIELD_KEY.equals(condition.getFieldKey())) return compileDuration(condition, scene, params);
        Field field = FIELDS.get(condition.getFieldKey());
        Binding binding = field == null ? null : field.bindings().get(scene);
        if (field == null || binding == null || !field.operators().contains(condition.getOperator())) throw exception(ADVANCED_FILTER_INVALID);
        validateOperands(field, condition);
        if (financeTrace != null && Set.of("cashback.beneficiaryName", "withdrawal.applicantName").contains(field.key())) {
            if (Boolean.TRUE.equals(params.get("validateOnly"))) return new Compiled(null, "1=1", false);
            var matching = financeTrace.matchIdentityIds(scene, condition.getOperator(), condition.getValue());
            return new Compiled(null, matching.isEmpty() ? "1=0" : ("cashback".equals(scene) ? "c.id" : "w.id") + " IN ("
                    + matching.stream().map(id -> ref(params, id)).collect(java.util.stream.Collectors.joining(",")) + ")", false);
        }
        String financeSource = Set.of("cashback", "withdrawal").contains(scene) ? financeSourceKind(field.key()) : null;
        if (financeTrace != null && financeSource != null && !Boolean.TRUE.equals(params.get("validateOnly"))) {
            @SuppressWarnings("unchecked")
            Set<Long> visible = (Set<Long>) params.computeIfAbsent("financeVisible_" + financeSource,
                    ignored -> financeTrace.visibleSourceIds(financeSource));
            String guard = visible.isEmpty() ? "1=0" : (financeSource.startsWith("lead") ? "fl.id" : "fo.id") + " IN ("
                    + visible.stream().map(id -> ref(params, id)).collect(java.util.stream.Collectors.joining(",")) + ")";
            binding = new Binding(binding.expression(), binding.relation() + " AND " + guard);
        }
        if ("lead.ownerDeptId".equals(field.key())) {
            if ("is_empty".equals(condition.getOperator()) || "is_not_empty".equals(condition.getOperator())) {
                throw exception(ADVANCED_FILTER_INVALID);
            }
            LeadFilterOrganizationService.requestedIds(condition.getValue());
            if (Boolean.TRUE.equals(params.get("validateOnly"))) return new Compiled(null, "1=1", false);
            var ids = leadFilterOrganizations.ownerIds(condition.getValue());
            if (ids.isEmpty()) return new Compiled(null, "1=0", false);
            String predicate = ids.isEmpty() ? "1=0" : "l.owner_user_id IN (" + ids.stream()
                    .map(id -> ref(params, id)).collect(java.util.stream.Collectors.joining(",")) + ")";
            if ("not_in".equals(condition.getOperator())) predicate = "NOT (" + predicate + ")";
            return new Compiled(null, predicate, false);
        }
        String expression = binding.expression();
        String operator = condition.getOperator();
        String predicate;
        boolean negateRelation = false;
        if ("is_empty".equals(operator) || "is_not_empty".equals(operator)) {
            predicate = emptyPredicate(field.type(), expression);
            if ("is_not_empty".equals(operator)) predicate = "NOT (" + predicate + ")";
            negateRelation = binding.relation() != null && "is_empty".equals(operator);
            if (negateRelation) predicate = "NOT (" + predicate + ")";
        } else if ("between".equals(operator)) {
            Object from = typed(field.type(), condition.getValueFrom()), to = typed(field.type(), condition.getValueTo());
            predicate = expression + " BETWEEN " + ref(params, from) + " AND " + ref(params, to);
        } else if ("relative".equals(operator)) {
            LocalDateTime[] range = relativeRange(String.valueOf(condition.getValue()));
            predicate = expression + " >= " + ref(params, range[0]) + " AND " + expression + " < " + ref(params, range[1]);
        } else if ("in".equals(operator) || "not_in".equals(operator)) {
            List<?> values = condition.getValue() instanceof Collection<?> collection ? collection.stream().toList() : List.of();
            List<String> refs = values.stream().map(value -> ref(params, typed(field.type(), value))).toList();
            predicate = expression + " IN (" + String.join(",", refs) + ")";
            if ("not_in".equals(operator)) {
                negateRelation = binding.relation() != null;
                if (!negateRelation) predicate = "NOT (" + predicate + ")";
            }
        } else {
            Object value = typed(field.type(), condition.getValue());
            String valueRef = ref(params, value);
            predicate = switch (operator) {
                case "contains" -> expression + " LIKE CONCAT('%'," + valueRef + ",'%')";
                case "not_contains" -> binding.relation() == null
                        ? "NOT (" + expression + " LIKE CONCAT('%'," + valueRef + ",'%'))"
                        : expression + " LIKE CONCAT('%'," + valueRef + ",'%')";
                case "eq" -> expression + " = " + valueRef;
                case "ne" -> binding.relation() == null
                        ? "NOT (" + expression + " = " + valueRef + ")"
                        : expression + " = " + valueRef;
                case "gt" -> expression + " > " + valueRef;
                case "gte" -> expression + " >= " + valueRef;
                case "lt" -> expression + " < " + valueRef;
                case "lte" -> expression + " <= " + valueRef;
                default -> throw exception(ADVANCED_FILTER_INVALID);
            };
            negateRelation = binding.relation() != null && Set.of("not_contains", "ne").contains(operator);
        }
        Compiled compiled = new Compiled(binding.relation(), predicate, negateRelation);
        if (financeTrace != null && financeSource != null && negateRelation) {
            return new Compiled(null, "(EXISTS (" + binding.relation() + ") AND " + compiled.sql() + ")", false);
        }
        return compiled;
    }

    public static String financeSourceKind(String key) {
        if (Set.of("cashback.leadNo", "withdrawal.leadNo").contains(key)) return "lead";
        if (Set.of("cashback.customerName", "withdrawal.customerName").contains(key)) return "lead_identity";
        if (Set.of("cashback.orderNo", "cashback.studentName", "withdrawal.orderNo", "withdrawal.studentName").contains(key)) return "order";
        return null;
    }

    private Compiled compileDuration(AdvancedFilterConditionReqVO condition, String scene, Map<String, Object> params) {
        if (!SCENES.contains(scene) || !DURATION_OPS.contains(condition.getOperator())) throw exception(ADVANCED_FILTER_INVALID);
        Field startField = FIELDS.get(condition.getStartFieldKey());
        Field endField = FIELDS.get(condition.getEndFieldKey());
        Binding startBinding = startField == null ? null : startField.bindings().get(scene);
        Binding endBinding = endField == null ? null : endField.bindings().get(scene);
        if (startBinding == null || endBinding == null
                || !"date".equals(startField.type()) || !"date".equals(endField.type())
                || !DURATION_UNIT_FACTORS.containsKey(condition.getUnit())) {
            throw exception(ADVANCED_FILTER_INVALID);
        }
        String relation = durationRelation(startBinding.relation(), endBinding.relation());
        String diffExpression = "TIMESTAMPDIFF(MINUTE, " + startBinding.expression() + ", " + endBinding.expression() + ")";
        String durationPredicate = switch (condition.getOperator()) {
            case "gt" -> diffExpression + " > " + ref(params, durationThreshold(condition.getValue(), condition.getUnit()));
            case "gte" -> diffExpression + " >= " + ref(params, durationThreshold(condition.getValue(), condition.getUnit()));
            case "lt" -> diffExpression + " < " + ref(params, durationThreshold(condition.getValue(), condition.getUnit()));
            case "lte" -> diffExpression + " <= " + ref(params, durationThreshold(condition.getValue(), condition.getUnit()));
            case "between" -> durationBetween(diffExpression, condition, params);
            default -> throw exception(ADVANCED_FILTER_INVALID);
        };
        return new Compiled(relation, startBinding.expression() + " IS NOT NULL AND "
                + endBinding.expression() + " IS NOT NULL AND " + durationPredicate, false);
    }

    private String durationRelation(String startRelation, String endRelation) {
        if (startRelation == null) return endRelation;
        if (endRelation == null || startRelation.equals(endRelation)) return startRelation;
        throw exception(ADVANCED_FILTER_INVALID);
    }

    private String durationBetween(String diffExpression, AdvancedFilterConditionReqVO condition, Map<String, Object> params) {
        BigDecimal from = durationThreshold(condition.getValueFrom(), condition.getUnit());
        BigDecimal to = durationThreshold(condition.getValueTo(), condition.getUnit());
        if (from.compareTo(to) > 0) throw exception(ADVANCED_FILTER_INVALID);
        return diffExpression + " BETWEEN " + ref(params, from) + " AND " + ref(params, to);
    }

    private BigDecimal durationThreshold(Object value, String unit) {
        Object typedValue = typed("number", value);
        if (!(typedValue instanceof BigDecimal number)) throw exception(ADVANCED_FILTER_INVALID);
        BigDecimal factor = DURATION_UNIT_FACTORS.get(unit);
        if (factor == null) throw exception(ADVANCED_FILTER_INVALID);
        return number.multiply(factor);
    }

    private boolean matchesGroup(String scene, AdvancedFilterGroupReqVO group, Function<String, Object> values) {
        boolean and = "AND".equals(group.getLogic());
        List<Boolean> matches = new ArrayList<>();
        for (AdvancedFilterConditionReqVO condition : group.getConditions()) {
            if (DURATION_FIELD_KEY.equals(condition.getFieldKey())) throw exception(ADVANCED_FILTER_INVALID);
            Field field = FIELDS.get(condition.getFieldKey());
            if (field == null || !field.bindings().containsKey(scene) || !field.operators().contains(condition.getOperator())) throw exception(ADVANCED_FILTER_INVALID);
            validateOperands(field, condition);
            matches.add(matchesValue(field, condition, values.apply(field.key())));
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
            Object typedActual = typed(field.type(), actual);
            if (typedActual == null) return false;
            boolean found = selected.stream().map(value -> typed(field.type(), value)).anyMatch(typedActual::equals);
            return "in".equals(operator) ? found : !found;
        }
        if ("relative".equals(operator)) {
            LocalDateTime value = (LocalDateTime) typed("date", actual);
            LocalDateTime[] range = relativeRange(String.valueOf(condition.getValue()));
            return !value.isBefore(range[0]) && value.isBefore(range[1]);
        }
        Object expected = typed(field.type(), condition.getValue());
        if (Set.of("contains", "not_contains").contains(operator)) {
            boolean found = actual.toString().contains(String.valueOf(expected));
            return "contains".equals(operator) ? found : !found;
        }
        if ("between".equals(operator)) {
            Object typedActual = typed(field.type(), actual);
            if (typedActual == null) return false;
            Comparable<Object> value = comparable(typedActual);
            return value.compareTo(typed(field.type(), condition.getValueFrom())) >= 0
                    && value.compareTo(typed(field.type(), condition.getValueTo())) <= 0;
        }
        Object typedActual = typed(field.type(), actual);
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
                    || values.stream().map(value -> typed(field.type(), value)).anyMatch(Objects::isNull)) {
                throw exception(ADVANCED_FILTER_INVALID);
            }
            return;
        }
        if ("between".equals(operator)) {
            Object from = typed(field.type(), condition.getValueFrom());
            Object to = typed(field.type(), condition.getValueTo());
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
        if (typed(field.type(), condition.getValue()) == null) throw exception(ADVANCED_FILTER_INVALID);
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

    private record Compiled(String relation, String predicate, boolean negateRelation) {
        String sql() { if (relation == null) return predicate; return (negateRelation ? "NOT EXISTS (" : "EXISTS (") + relation + " AND " + predicate + ")"; }
    }
}
