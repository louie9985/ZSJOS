package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import java.util.*;
import java.util.function.Function;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

/** Drafts accept unfinished text, but never client-owned derived fields or attachment IDs. */
public final class StudentInfoDraftPolicy {
    private StudentInfoDraftPolicy() { }
    public static boolean editable(Field field) {
        return Boolean.TRUE.equals(field.getEnabled()) && Set.of("text", "textarea", "dict").contains(field.getType())
                && !Set.of("gender", "age", "household_area").contains(field.getKey());
    }
    public static Draft build(List<Field> fields, Draft previous, Map<String,String> input, Function<Field,List<Option>> options) {
        if (input == null || input.size() > 32) throw exception(FIELD_INVALID, "草稿字段数量");
        Map<String,Field> allowed = new LinkedHashMap<>();
        fields.stream().filter(StudentInfoDraftPolicy::editable).forEach(field -> allowed.put(field.getKey(),field));
        Map<String,String> values = new LinkedHashMap<>();
        Map<String,DraftSelection> selections = new LinkedHashMap<>();
        for (var item : input.entrySet()) {
            var field = allowed.get(item.getKey()); var value = item.getValue();
            if (field == null || value == null || value.length() > ("textarea".equals(field.getType()) ? 2000 : 200))
                throw exception(FIELD_INVALID, "草稿字段或长度");
            values.put(item.getKey(), value);
            if (!"dict".equals(field.getType()) || value.isEmpty()) continue;
            var old = previous.getSelections().get(field.getKey());
            if (old != null && value.equals(old.getCode()) && Objects.equals(field.getDictType(),old.getDictType())) {
                selections.put(field.getKey(), old);
            } else {
                var option = options.apply(field).stream().filter(o -> value.equals(o.getValue())).findFirst()
                        .orElseThrow(() -> exception(REFERENCE_INVALID, field.getLabel()));
                var snapshot = new DraftSelection(); snapshot.setCode(value); snapshot.setLabel(option.getLabel()); snapshot.setDictType(field.getDictType());
                selections.put(field.getKey(), snapshot);
            }
        }
        var result = new Draft(); result.setValues(values); result.setSelections(selections); return result;
    }
}
