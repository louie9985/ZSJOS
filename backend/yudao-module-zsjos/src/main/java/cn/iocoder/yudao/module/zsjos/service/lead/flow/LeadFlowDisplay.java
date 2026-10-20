package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import java.util.regex.Pattern;

/** Translate only verified import markers; user-authored reasons and stored evidence remain intact. */
public final class LeadFlowDisplay {
    private static final String ASSIGNMENT_BASELINE = "legacy assignment history baseline";
    private static final Pattern ASSIGNMENT_MARKER = Pattern.compile("^legacy-assignment-log-[0-9]+(?:\\s+|$)");
    private static final Pattern CLAIM_MARKER = Pattern.compile("^legacy-claim-[0-9]+(?:\\s+|$)");

    private LeadFlowDisplay() {}

    public static boolean isAssignmentBaseline(String reason) {
        return ASSIGNMENT_BASELINE.equals(reason);
    }

    public static String reason(String reason) {
        if (reason == null) return null;
        if (isAssignmentBaseline(reason)) return "历史分配初始记录";
        var assignment = ASSIGNMENT_MARKER.matcher(reason);
        if (assignment.find()) return importedReason("历史分配记录", reason.substring(assignment.end()));
        var claim = CLAIM_MARKER.matcher(reason);
        if (claim.find()) return importedReason("历史抢单记录", reason.substring(claim.end()));
        return reason;
    }

    private static String importedReason(String label, String detail) {
        return detail.isBlank() ? label : label + "：" + detail;
    }
}
