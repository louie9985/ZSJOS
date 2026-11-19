package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.ExamCollectionSubmissionDO;
import java.util.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.UploadLimit;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

/** Exam snapshots use published material definitions, independent of order product rules. */
public final class ExamCollectionFields {
    private ExamCollectionFields() {}
    public static final List<String> FIXED_KEYS = List.of("name", "mobile", "id_card");
    /** Per-task attendance answer; it gates the rest of the form and is never merged into the student archive. */
    public static final String ATTENDANCE_KEY = "exam_attendance", ABSENCE_REASON_KEY = "absence_reason";
    public static final String ATTEND = "是", ABSENT = "否";

    /** Fields a submission must satisfy: identity always; "是" adds the task fields, "否" only the reason. */
    public static List<Field> applicable(List<Field> fields, Object attendance) {
        if (fields.stream().noneMatch(field -> ATTENDANCE_KEY.equals(field.getKey()))) return fields;
        if (ABSENT.equals(attendance)) return fields.stream().filter(field -> FIXED_KEYS.contains(field.getKey())
                || ATTENDANCE_KEY.equals(field.getKey()) || ABSENCE_REASON_KEY.equals(field.getKey())).toList();
        return fields.stream().filter(field -> !ABSENCE_REASON_KEY.equals(field.getKey())).toList();
    }

    /** The reason text when the submission declined the exam, otherwise null. */
    public static String absenceReason(List<StudentInfoFormValueDO> answers) {
        if (answers == null || answers.stream().noneMatch(value -> ATTENDANCE_KEY.equals(value.getFieldKey()) && ABSENT.equals(value.getValueText())))
            return null;
        return answers.stream().filter(value -> ABSENCE_REASON_KEY.equals(value.getFieldKey())).map(StudentInfoFormValueDO::getValueText)
                .filter(Objects::nonNull).findFirst().orElse("");
    }

    /** Submission id to absence reason for declined submissions among the given rows. */
    public static Map<Long, String> absenceReasons(Collection<ExamCollectionSubmissionDO> rows) {
        var result = new HashMap<Long, String>();
        for (var row : rows) {
            String reason = absenceReason(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(row.getAnswersJson(), StudentInfoFormValueDO.class));
            if (reason != null) result.put(row.getId(), reason);
        }
        return result;
    }

    private static Field attendanceField(String key) {
        var field = new Field(); field.setKey(key); field.setGroup("basic");
        field.setEnabled(true); field.setRequired(true); field.setSensitive(false);
        if (ATTENDANCE_KEY.equals(key)) {
            field.setLabel("是否参加本次考试"); field.setType("radio"); field.setOptions(List.of(ATTEND, ABSENT));
        } else { field.setLabel("您不参加本次考试的原因"); field.setType("textarea"); }
        return field;
    }

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
        for (String key : List.of(ATTENDANCE_KEY, ABSENCE_REASON_KEY)) {
            Field field = attendanceField(key); field.setSort((result.size() + 1) * 10);
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

    public static List<Field> customize(List<Field> preset, List<Field> custom, Map<String, String> notes) {
        var result = new ArrayList<>(preset);
        var keys = new HashSet<String>();
        result.forEach(field -> keys.add(field.getKey()));
        if (custom != null) {
            if (custom.size() > 30) throw exception(FIELD_INVALID, "自定义字段最多30项");
            for (Field input : custom) {
                if (input == null || input.getKey() == null || !input.getKey().matches("custom_[A-Za-z0-9_-]{1,56}")
                        || !keys.add(input.getKey()) || input.getLabel() == null || input.getLabel().isBlank()
                        || input.getLabel().length() > 64 || !Set.of("text", "radio", "checkbox").contains(input.getType())
                        || input.getRequired() == null) throw exception(FIELD_INVALID, "自定义字段配置无效");
                Field field = copy(input);
                field.setLabel(field.getLabel().trim());
                field.setEnabled(true); field.setSensitive(false); field.setGroup("custom");
                field.setDictType(null);
                if (!"text".equals(field.getType())) {
                    var options = field.getOptions();
                    if (options == null || options.size() < 2 || options.size() > 50
                            || options.stream().anyMatch(option -> option == null || option.isBlank() || option.length() > 200))
                        throw exception(FIELD_INVALID, "选择题需要2至50个有效选项");
                    options = options.stream().map(String::trim).toList();
                    if (new HashSet<>(options).size() != options.size()) throw exception(FIELD_INVALID, "选项不能重复");
                    field.setOptions(options);
                } else field.setOptions(null);
                result.add(field);
            }
        }
        // Public forms show non-upload fields under basic info before uploads; freeze the same order in the snapshot.
        result.sort(Comparator.comparing(field -> "images".equals(field.getType())));
        for (int i = 0; i < result.size(); i++) result.get(i).setSort((i + 1) * 10);
        if (notes != null) {
            if (!keys.containsAll(notes.keySet())) throw exception(FIELD_INVALID, "填写说明包含未选择字段");
            result.forEach(field -> { if (notes.containsKey(field.getKey())) field.setNote(notes.get(field.getKey())); });
        }
        if (result.stream().anyMatch(field -> field.getNote() != null && field.getNote().length() > 500))
            throw exception(FIELD_INVALID, "填写说明最多500字");
        return List.copyOf(result);
    }

    private static Field copy(Field input) {
        var result = BeanUtils.toBean(input, Field.class);
        if (input.getAllowedExtensions() != null) result.setAllowedExtensions(List.copyOf(input.getAllowedExtensions()));
        return result;
    }
}
