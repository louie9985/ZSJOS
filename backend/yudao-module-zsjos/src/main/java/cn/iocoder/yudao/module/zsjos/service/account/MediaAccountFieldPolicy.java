package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigRespVO.FieldVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Object responsibility supplements menu permission; unassigned and system fields fail closed. */
public final class MediaAccountFieldPolicy {
    public static final Set<String> POSITIONING_SYNC_FIELDS = Set.of("account_position", "professional_position",
            "content_format", "diagnosis_7d_requirement", "diagnosis_14d_requirement", "diagnosis_28d_requirement",
            "delivery_goals");
    private MediaAccountFieldPolicy() {}
    public static boolean canWrite(FieldVO field, MediaAccountDO account, Long userId) {
        if (userId == null || !Boolean.TRUE.equals(field.getEnabled())) return false;
        if (POSITIONING_SYNC_FIELDS.contains(field.getKey()) || isRetiredPositioning(field)) return false;
        if (Set.of("student_commitments", "company_commitments").contains(field.getKey()) && !userId.equals(account.getDirectorUserId())) return false;
        return "DIRECTOR".equals(field.getOwnerType()) && userId.equals(account.getDirectorUserId())
                || "OPERATOR".equals(field.getOwnerType()) && userId.equals(account.getOwnerOperatorUserId());
    }
    public static boolean isRetiredPositioning(FieldVO field) {
        return "POSITIONING".equals(field.getGroup()) || field.getKey().startsWith("pc_")
                || Set.of("positioning_history", "positioning_snapshot").contains(field.getKey());
    }
    public static void validate(List<FieldVO> fields, Map<String, Object> changes, MediaAccountDO account, Long userId) {
        for (String key : changes.keySet()) {
            FieldVO field = fields.stream().filter(f -> key.equals(f.getKey())).findFirst()
                    .orElseThrow(() -> exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID));
            if (!canWrite(field, account, userId)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
            if ("record".equals(field.getType())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
    }
    public static boolean empty(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> items && items.isEmpty();
    }
}
