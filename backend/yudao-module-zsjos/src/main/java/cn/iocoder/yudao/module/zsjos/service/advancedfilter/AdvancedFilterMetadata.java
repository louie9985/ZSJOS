package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;

/** Describes implemented capabilities; these values do not grant access or replace query authorization. */
final class AdvancedFilterMetadata {
    private AdvancedFilterMetadata() {}

    // Known pageKey integrations, not a menu tree or permission allowlist.
    private static final Map<String, List<String>> PAGES = Map.of(
            "lead", List.of("lead_management", "lead_claim_pool", "lead_aging_pool", "subordinate_sales_leads"),
            "order", List.of("sales_order_management", "sales_order_approval:registration",
                    "sales_order_approval:finance", "sales_order_approval", "sales_order_supervisor_confirm"),
            "lead_appeal", List.of("lead_appeal"),
            "duplicate_review", List.of("lead_duplicate_review"),
            "registration", List.of("registration_pool"),
            "student", List.of("student_my"),
            "subordinate_sales", List.of("subordinate_sales"),
            "cashback", List.of("cashback"),
            "withdrawal", List.of("withdrawal"));

    static List<String> pages(Collection<String> scenes) {
        return scenes.stream().sorted().flatMap(scene -> {
            var pages = PAGES.get(scene);
            if (pages == null) throw new IllegalStateException("Missing filter page metadata for scene: " + scene);
            return pages.stream();
        }).distinct().toList();
    }

    static AdvancedFilterCatalogRespVO.FieldVO describe(Field field) {
        return describe(field.key(), field.group(), field.label(), field.type(), field.operators(),
                field.optionSource(), field.options(), field.bindings().keySet(), field.sensitivity(),
                sourceType(field.optionSource(), field.options()));
    }

    static AdvancedFilterCatalogRespVO.FieldVO duration(String scene, List<String> operators,
                                                        List<AdvancedFilterCatalogRespVO.OptionVO> dateFields) {
        return describe("duration.diff", TIME, "时间作差", "duration", operators, null, dateFields,
                List.of(scene), Sensitivity.STANDARD, "catalog_dates");
    }

    private static AdvancedFilterCatalogRespVO.FieldVO describe(
            String key, String group, String label, String type, List<String> operators, String source,
            List<AdvancedFilterCatalogRespVO.OptionVO> options, Collection<String> scenes,
            Sensitivity sensitivity, String sourceType) {
        return new AdvancedFilterCatalogRespVO.FieldVO(key, group, label, type, operators, source, options,
                source != null && options.isEmpty() ? "unresolved" : options.isEmpty() ? "empty" : "ready", null,
                scenes.stream().sorted().toList(), pages(scenes), "inherit_query_authorization",
                "inherit_query_data_scope", sensitivity != Sensitivity.STANDARD,
                sensitivity.name().toLowerCase(java.util.Locale.ROOT), false, false, sourceType, source);
    }

    private static String sourceType(String source, List<AdvancedFilterCatalogRespVO.OptionVO> options) {
        if (source == null) return options.isEmpty() ? "none" : "business_contract";
        if (source.startsWith("dict:") && source.length() > 5) return "dictionary";
        if (source.startsWith("product-catalog:")) return "business_api";
        if (source.equals("visible-users")) return "visible_users";
        if (source.equals("visible-departments")) return "visible_departments";
        throw new IllegalStateException("Unsupported filter option source: " + source);
    }
}
