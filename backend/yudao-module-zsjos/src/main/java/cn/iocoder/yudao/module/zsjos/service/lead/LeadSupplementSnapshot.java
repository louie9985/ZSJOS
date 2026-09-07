package cn.iocoder.yudao.module.zsjos.service.lead;

import java.util.Map;

/** Versioned append-only supplement evidence; before is retained for legacy audit consumers. */
public record LeadSupplementSnapshot(Map<String, Object> before, String remarkMode, String remark,
                                     String submitterType, Long submitterId, String submitterName,
                                     String requestDigest) {
    public static final String MODE = "append_v1";
    public static final String EVENT = "lead_submitter_supplemented";
}
