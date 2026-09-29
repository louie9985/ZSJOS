package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.util.Map;

/** Server-owned provenance; neither dictionary labels nor user remarks identify automatic work. */
public final class LeadAutomaticGeneration {
    public static final String SOURCE = "sales_self_sourced_auto";
    public static final String FIELD = "generationSource";
    // Explicit business decision: reuse enabled dictionary values; labels remain dictionary snapshots.
    public static final String METHOD = "other";
    public static final String RESULT = "interested";
    private LeadAutomaticGeneration() {}
    public static String followUpKey(Long leadId) { return SOURCE + ":follow:" + leadId; }
    public static String qualificationKey(Long leadId) { return SOURCE + ":qualify:" + leadId; }
    public static String qualificationEventKey(Long leadId) { return "lead-qualification:" + qualificationKey(leadId); }
    public static boolean isAutomatic(String json) {
        if (json == null || json.isBlank()) return false;
        try { return SOURCE.equals(JsonUtils.parseObject(json, Map.class).get(FIELD)); }
        catch (RuntimeException ignored) { return false; }
    }
}
