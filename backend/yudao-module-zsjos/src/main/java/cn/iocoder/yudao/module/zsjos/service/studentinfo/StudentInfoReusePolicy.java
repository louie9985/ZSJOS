package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import java.util.*;

/** Matches stable field contracts, never project names or display labels. */
public final class StudentInfoReusePolicy {
    private StudentInfoReusePolicy() { }

    public static List<String> missing(List<Field> target, List<Field> source,
                                       List<StudentInfoFormValueDO> answers, List<Image> images) {
        List<String> missing = new ArrayList<>();
        for (Field field : target) {
            if (!Boolean.TRUE.equals(field.getEnabled()) || !Boolean.TRUE.equals(field.getRequired())) continue;
            Field previous = source.stream().filter(f -> compatible(field, f)).findFirst().orElse(null);
            if (previous == null) { missing.add(field.getLabel()); continue; }
            if ("images".equals(field.getType())) {
                var selected = images.stream().filter(i -> field.getKey().equals(i.getFieldKey())).toList();
                boolean identity = "identity_images".equals(field.getKey());
                if (selected.isEmpty() || identity && !selected.stream().map(Image::getSide).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of("front", "back"))
                        || selected.size() > (identity ? 2 : field.getMaxImages())
                        || selected.stream().anyMatch(i -> !attachmentCompatible(field, i))) missing.add(field.getLabel());
            } else {
                var answer = answers.stream().filter(a -> field.getKey().equals(a.getFieldKey())).findFirst().orElse(null);
                String value = answer == null ? null : "dict".equals(field.getType()) ? answer.getValueCode()
                        : "area".equals(field.getType()) ? answer.getIdentityAreaSnapshot() != null ? answer.getAreaLabelSnapshot() : answer.getAreaCodePath() : answer.getValueText();
                if (value == null || value.isBlank()) missing.add(field.getLabel());
            }
        }
        return missing;
    }

    public static boolean compatible(Field target, Field source) {
        return Boolean.TRUE.equals(source.getEnabled()) && Objects.equals(target.getKey(), source.getKey())
                && Objects.equals(target.getType(), source.getType()) && Objects.equals(target.getDictType(), source.getDictType());
    }

    public static boolean attachmentCompatible(Field field, Image image) {
        String extension = StudentInfoAttachmentValidator.extension(image.getContentType()).replaceFirst("^\\.", "");
        return image.getSize() != null && image.getSize() <= (long) field.getMaxSizeMb() * 1024 * 1024
                && field.getAllowedExtensions() != null && field.getAllowedExtensions().contains(extension);
    }

    public static Draft prefill(List<Field> target, List<Field> source, List<StudentInfoFormValueDO> answers) {
        Draft draft = new Draft();
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, DraftSelection> selections = new LinkedHashMap<>();
        for (Field field : target) {
            if (!StudentInfoDraftPolicy.editable(field) || source.stream().noneMatch(f -> compatible(field, f))) continue;
            var answer = answers.stream().filter(a -> field.getKey().equals(a.getFieldKey())).findFirst().orElse(null);
            if (answer == null) continue;
            String value = "dict".equals(field.getType()) ? answer.getValueCode() : answer.getValueText();
            if (value == null) continue;
            values.put(field.getKey(), value);
            if ("dict".equals(field.getType())) {
                DraftSelection selection = new DraftSelection(); selection.setCode(value);
                selection.setDictType(answer.getDictType()); selection.setLabel(answer.getValueLabelSnapshot());
                selections.put(field.getKey(), selection);
            }
        }
        if (target.stream().anyMatch(f -> "household_area".equals(f.getKey()) && Boolean.TRUE.equals(f.getEnabled()))) {
            var area = answers.stream().filter(a -> "household_area".equals(a.getFieldKey()) && a.getIdentityAreaSnapshot()!=null).findFirst();
            if (area.isPresent()) {
                var snapshot = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(area.get().getIdentityAreaSnapshot(), Map.class);
                Object savedInput = snapshot.get("input");
                if (savedInput != null) {
                    var restored = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                            cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(savedInput), IdentityAreaInput.class);
                    String card = values.getOrDefault("id_card", "");
                    if (card.startsWith(restored.getCode())) draft.setIdentityArea(restored);
                }
            }
        }
        draft.setValues(values); draft.setSelections(selections); return draft;
    }
}
