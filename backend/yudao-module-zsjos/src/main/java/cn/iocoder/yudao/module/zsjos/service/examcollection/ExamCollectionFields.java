package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import java.util.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.UploadLimit;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

/** Exam snapshots use published material definitions, independent of order product rules. */
public final class ExamCollectionFields {
    private ExamCollectionFields() {}
    public static final List<String> FIXED_KEYS = List.of("name", "mobile", "id_card");

    /** Apply the current collection contract without rewriting issued or submitted snapshots. */
    public static List<Field> liveFields(List<Field> snapshot) {
        return snapshot.stream().filter(field -> !"wechat".equals(field.getKey())).toList();
    }

    public static Map<String, Object> liveValues(Map<String, Object> input) {
        var values = new LinkedHashMap<>(input);
        values.remove("wechat");
        return values;
    }

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
            if (!byKey.containsKey(key)) throw exception(CONFIG_INVALID);
            Field field = copy(byKey.get(key));
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

    public static List<Field> snapshot(List<Field> published, List<String> selected,
            Map<String, UploadLimit> limits) {
        var result = snapshot(published, selected);
        var uploads = result.stream().filter(field -> "images".equals(field.getType())).toList();
        var configured = limits == null ? Map.<String, UploadLimit>of() : limits;
        if (!uploads.stream().map(Field::getKey).toList().containsAll(configured.keySet()))
            throw exception(FIELD_INVALID, "附件限制包含未选择的材料");
        for (Field field : uploads) {
            var limit = configured.get(field.getKey());
            if (limit != null && (limit.getMaxSizeMb() == null || limit.getMaxImages() == null))
                throw exception(FIELD_INVALID, "附件限制缺少大小或数量");
            int size = limit == null ? 10 : limit.getMaxSizeMb();
            int count = limit == null ? 1 : limit.getMaxImages();
            if (size < 1 || size > 100 || count < 1 || count > 9
                    || "identity_images".equals(field.getKey()) && count != 1)
                throw exception(FIELD_INVALID, "附件大小或数量限制无效");
            field.setMaxSizeMb(size);
            // Identity front and back retain one file per side in the existing upload contract.
            field.setMaxImages("identity_images".equals(field.getKey()) ? 2 : count);
        }
        return result;
    }

    private static Field copy(Field input) {
        var result = BeanUtils.toBean(input, Field.class);
        if (input.getAllowedExtensions() != null) result.setAllowedExtensions(List.copyOf(input.getAllowedExtensions()));
        return result;
    }
}
