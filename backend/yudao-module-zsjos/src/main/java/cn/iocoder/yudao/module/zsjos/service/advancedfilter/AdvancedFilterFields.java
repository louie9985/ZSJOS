package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;
import java.util.*;

final class AdvancedFilterFields {
    private AdvancedFilterFields() {}

    static final String IDENTITY = "身份与联系";
    static final String STATUS = "状态与进度";
    static final String PEOPLE = "归属与人员";
    static final String PRODUCT = "产品与服务";
    static final String MONEY = "金额与付款";
    static final String TIME = "时间";
    static final String EXTRA = "补充信息";
    static final List<String> TEXT_OPS = List.of("contains", "not_contains", "eq", "ne", "is_empty", "is_not_empty");
    static final List<String> SELECT_OPS = List.of("in", "not_in", "is_empty", "is_not_empty");
    static final List<String> RANGE_OPS = List.of("eq", "gt", "gte", "lt", "lte", "between", "is_empty", "is_not_empty");
    static final List<String> DATE_OPS = List.of("eq", "gt", "gte", "lt", "lte", "between", "relative", "is_empty", "is_not_empty");

    static Map<String, Binding> bind(Object... values) {
        Map<String, Binding> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 3) result.put((String) values[i], new Binding((String) values[i + 1], (String) values[i + 2]));
        return result;
    }
    static void add(Map<String, Field> fields, Field field) { if (fields.putIfAbsent(field.key(), field) != null) throw new IllegalStateException("Duplicate advanced filter field: " + field.key()); }
    static Field text(Sensitivity sensitivity, String key, String group, String label, Map<String, Binding> bindings) { return field(sensitivity, key, group, label, "text", TEXT_OPS, null, List.of(), bindings); }
    static Field number(Sensitivity sensitivity, String key, String group, String label, Map<String, Binding> bindings) { return field(sensitivity, key, group, label, "number", RANGE_OPS, null, List.of(), bindings); }
    static Field date(Sensitivity sensitivity, String key, String group, String label, Map<String, Binding> bindings) { return field(sensitivity, key, group, label, "date", DATE_OPS, null, List.of(), bindings); }
    static Field select(Sensitivity sensitivity, String key, String group, String label, List<AdvancedFilterCatalogRespVO.OptionVO> options, Map<String, Binding> bindings) { return field(sensitivity, key, group, label, "select", SELECT_OPS, null, options, bindings); }
    static Field selectSource(Sensitivity sensitivity, String key, String group, String label, String source, Map<String, Binding> bindings) { return field(sensitivity, key, group, label, "select", SELECT_OPS, source, List.of(), bindings); }
    static Field field(Sensitivity sensitivity, String key, String group, String label, String type, List<String> operators, String optionSource, List<AdvancedFilterCatalogRespVO.OptionVO> options, Map<String, Binding> bindings) { return new Field(sensitivity, key, group, label, type, operators, optionSource, options, bindings); }
    static List<AdvancedFilterCatalogRespVO.OptionVO> options(String... valuesAndLabels) {
        List<AdvancedFilterCatalogRespVO.OptionVO> result = new ArrayList<>();
        for (int i = 0; i < valuesAndLabels.length; i += 2) result.add(new AdvancedFilterCatalogRespVO.OptionVO(valuesAndLabels[i], valuesAndLabels[i + 1]));
        return List.copyOf(result);
    }
    record Binding(String expression, String relation) {}
    enum Sensitivity { STANDARD, PERSONAL, FINANCIAL, FREE_TEXT }

    record Field(Sensitivity sensitivity, String key, String group, String label, String type, List<String> operators, String optionSource, List<AdvancedFilterCatalogRespVO.OptionVO> options, Map<String, Binding> bindings) {
        Field {
            Objects.requireNonNull(sensitivity, "Field sensitivity must be explicitly classified");
            if (bindings.isEmpty()) throw new IllegalArgumentException("Field must have a scene binding: " + key);
            operators = List.copyOf(operators);
            options = List.copyOf(options);
            bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
        }
    }

}
