package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.util.Map;

/** Server-owned provenance; neither dictionary labels nor user remarks identify automatic work. */
public final class LeadAutomaticGeneration {
    public static final String SOURCE = "sales_self_sourced_auto";
    public static final String EDUCATION_SOURCE = "education_self_sourced_auto";
    public static final String FIELD = "generationSource";
    // Explicit business decision: reuse enabled dictionary values; labels remain dictionary snapshots.
    public static final String METHOD = "other";
    public static final String RESULT = "interested";
    private LeadAutomaticGeneration() {}
    public static String followUpKey(Long leadId) { return SOURCE + ":follow:" + leadId; }
    public static String qualificationKey(Long leadId) { return SOURCE + ":qualify:" + leadId; }
    public static String qualificationEventKey(Long leadId) { return "lead-qualification:" + qualificationKey(leadId); }
    public static String sourceForLead(String sourceType) {
        return switch (sourceType) {
            case cn.iocoder.yudao.module.zsjos.enums.LeadConstants.SOURCE_SALES_SELF -> SOURCE;
            case cn.iocoder.yudao.module.zsjos.enums.LeadConstants.SOURCE_EDUCATION_SELF -> EDUCATION_SOURCE;
            default -> throw new IllegalArgumentException("Unsupported automatic Lead source");
        };
    }
    public static String followUpKey(Long leadId, String sourceType) { return sourceForLead(sourceType) + ":follow:" + leadId; }
    public static String qualificationKey(Long leadId, String sourceType) { return sourceForLead(sourceType) + ":qualify:" + leadId; }
    public static String qualificationEventKey(Long leadId, String sourceType) { return "lead-qualification:" + qualificationKey(leadId, sourceType); }
    public static boolean isAutomaticSource(Object source) { return SOURCE.equals(source) || EDUCATION_SOURCE.equals(source); }
    public static String sourceFromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Object source = JsonUtils.parseObject(json, Map.class).get(FIELD);
            return isAutomaticSource(source) ? (String) source : null;
        } catch (RuntimeException ignored) { return null; }
    }
    public static boolean isAutomatic(String json) {
        return sourceFromJson(json) != null;
    }
}
