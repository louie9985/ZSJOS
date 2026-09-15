package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable business routing; approvers and forms remain owned by BPM. */
final class MaterialApprovalContract {
    private static final Map<String, String> VIRAL_PROCESSES = Map.of(
            "viral_account", "zsjos_viral_account_review",
            "viral_content", "zsjos_viral_content_review");

    private MaterialApprovalContract() { }

    static boolean isViral(String code) {
        return code != null && VIRAL_PROCESSES.containsKey(code);
    }

    static String processKey(MaterialTypeDO type) {
        if (type == null) return null;
        return isViral(type.getCode()) ? VIRAL_PROCESSES.get(type.getCode()) : type.getBpmProcessDefinitionKey();
    }

    static Map<String, Object> snapshotVariables(MaterialVersionDO version) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("materialTitle", version.getTitle());
        values.put("materialSummary", version.getSummary());
        // Frozen display/search text is retained; do not resolve today's dictionary labels.
        values.put("materialContent", version.getSearchText());
        return values;
    }
}
