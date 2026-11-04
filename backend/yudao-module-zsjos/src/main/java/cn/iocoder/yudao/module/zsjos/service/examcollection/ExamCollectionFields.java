package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

/** Exam snapshots use published material definitions, independent of order product rules. */
public final class ExamCollectionFields {
    private ExamCollectionFields() {}
    public static final List<String> FIXED_KEYS = List.of("name", "mobile", "wechat", "id_card");

    public static List<Field> selectable(List<Field> published) {
        return published.stream().filter(f -> Boolean.TRUE.equals(f.getEnabled()))
                .filter(f -> "images".equals(f.getType()) || "certificate_shipping_address".equals(f.getKey()))
                .filter(f -> !"gift_shipping_address".equals(f.getKey()))
                .sorted(Comparator.comparing(Field::getSort)).map(ExamCollectionFields::copy).toList();
    }

    public static List<Field> snapshot(List<Field> published, List<String> selected) {
        if (published == null || published.isEmpty()) throw exception(CONFIG_MISSING);
        if (selected == null || new HashSet<>(selected).size() != selected.size())
            throw exception(FIELD_INVALID, "重复或缺失的材料选择");
        var byKey = new LinkedHashMap<String, Field>();
        published.forEach(field -> byKey.put(field.getKey(), field));
        Set<String> allowed = new HashSet<>();
        selectable(published).forEach(field -> allowed.add(field.getKey()));
        if (!allowed.containsAll(selected)) throw exception(FIELD_INVALID, "材料未发布、停用或不允许收集");
        var result = new ArrayList<Field>();
        for (String key : FIXED_KEYS) {
            Field field;
            if ("wechat".equals(key)) {
                // A fixed protocol field required by this collection flow, not a business option.
                field = new Field(); field.setKey(key); field.setLabel("微信号");
                field.setType("text"); field.setGroup("basic"); field.setSensitive(true); field.setNote("");
            } else {
                if (!byKey.containsKey(key)) throw exception(CONFIG_INVALID);
                field = copy(byKey.get(key));
            }
            field.setEnabled(true); field.setRequired(true); field.setSort((result.size() + 1) * 10);
            result.add(field);
        }
        for (Field available : selectable(published)) {
            if (!selected.contains(available.getKey())) continue;
            available.setRequired(true); available.setSort((result.size() + 1) * 10);
            result.add(available);
        }
        return List.copyOf(result);
    }

    private static Field copy(Field input) {
        var result = BeanUtils.toBean(input, Field.class);
        if (input.getAllowedExtensions() != null) result.setAllowedExtensions(List.copyOf(input.getAllowedExtensions()));
        return result;
    }
}
