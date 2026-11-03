package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import java.util.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;

/** Registration staff may omit these materials, but cannot enable unpublished fields. */
final class StudentInfoMaterialSelection {
    private StudentInfoMaterialSelection() { }
    static final Set<String> KEYS = Set.of("portrait", "identity_images", "diploma_images",
            "education_report", "application_images", "work_proof", "social_security",
            "labor_contract", "prior_certificate", "certificate_lookup", "commitment", "gift_shipping_address", "certificate_shipping_address");
    static List<String> selectable(List<Field> fields) {
        return fields.stream().filter(f -> Boolean.TRUE.equals(f.getEnabled()) && KEYS.contains(f.getKey()))
                .map(Field::getKey).toList();
    }
    static List<Field> select(List<Field> fields, List<String> requested) {
        var selected = requested == null ? List.<String>of() : requested;
        if (new HashSet<>(selected).size() != selected.size() || !selectable(fields).containsAll(selected))
            throw exception(FIELD_INVALID, "所选材料不在本次可收集范围内，请刷新配置");
        // Omit from the snapshot entirely so prefill and attachment copying cannot retain hidden materials.
        return fields.stream().filter(f -> !KEYS.contains(f.getKey()) || selected.contains(f.getKey())).toList();
    }
}
