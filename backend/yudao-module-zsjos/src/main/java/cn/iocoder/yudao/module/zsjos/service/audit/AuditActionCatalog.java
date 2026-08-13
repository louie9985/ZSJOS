package cn.iocoder.yudao.module.zsjos.service.audit;

import java.util.Map;
import java.util.Set;

/** Stable business audit catalog. New explicit audit actions must be registered here first. */
public final class AuditActionCatalog {
    public static final String CATEGORY_EXPORT = "export";
    public static final String CATEGORY_IMPERSONATION = "impersonation";
    public static final String EXPORT_CREATE = "export.create";
    public static final String EXPORT_GENERATE = "export.generate";
    public static final String EXPORT_DOWNLOAD = "export.download";
    public static final String EXPORT_CANCEL = "export.cancel";
    public static final String IMPERSONATION_START = "impersonation.start";
    public static final String IMPERSONATION_END = "impersonation.end";
    public static final String IMPERSONATION_READ = "impersonation.read";

    public static final Map<String, Set<String>> ACTIONS = Map.of(
            CATEGORY_EXPORT, Set.of(EXPORT_CREATE, EXPORT_GENERATE, EXPORT_DOWNLOAD, EXPORT_CANCEL),
            CATEGORY_IMPERSONATION, Set.of(IMPERSONATION_START, IMPERSONATION_END, IMPERSONATION_READ));

    private AuditActionCatalog() {
    }

    public static boolean contains(String category, String action) {
        return ACTIONS.getOrDefault(category, Set.of()).contains(action);
    }
}
