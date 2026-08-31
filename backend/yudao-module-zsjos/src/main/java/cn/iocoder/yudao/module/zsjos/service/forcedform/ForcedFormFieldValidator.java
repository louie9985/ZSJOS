package cn.iocoder.yudao.module.zsjos.service.forcedform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Component
public class ForcedFormFieldValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "text", "textarea", "radio", "multi-select", "checkbox", "attachment");

    @Resource
    private DictDataApi dictDataApi;

    public List<ForcedFormFieldDefinition> parseAndValidate(String json) {
        try {
            List<ForcedFormFieldDefinition> fields = JsonUtils.parseArray(json, ForcedFormFieldDefinition.class);
            validateDefinitions(fields);
            return fields;
        } catch (RuntimeException exception) {
            if (exception.getClass().getName().contains("ServiceException")) {
                throw exception;
            }
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
        }
    }

    public Map<String, Object> normalizeAnswers(List<ForcedFormFieldDefinition> fields, Map<String, Object> answers) {
        if (answers == null) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
        }
        Set<String> allowedKeys = new HashSet<>();
        for (ForcedFormFieldDefinition field : fields) {
            allowedKeys.add(field.getKey());
        }
        if (!allowedKeys.containsAll(answers.keySet())) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (ForcedFormFieldDefinition field : fields) {
            Object value = answers.get(field.getKey());
            if (value == null || (value instanceof String str && str.isBlank())) {
                if (Boolean.TRUE.equals(field.getRequired())) {
                    throw exception(ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED);
                }
                continue;
            }
            normalized.put(field.getKey(), normalizeAnswer(field, value));
        }
        return normalized;
    }

    public Map<String, Object> buildDictSnapshot(List<ForcedFormFieldDefinition> fields, Map<String, Object> answers) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (ForcedFormFieldDefinition field : fields) {
            if (!Set.of("radio", "multi-select").contains(field.getType())) {
                continue;
            }
            Object value = answers.get(field.getKey());
            if (value == null) {
                continue;
            }
            List<Map<String, String>> dictOptions = dictDataApi.getDictDataList(field.getDictType()).stream()
                    .map(item -> Map.of("value", item.getValue(), "label", item.getLabel()))
                    .toList();
            if ("radio".equals(field.getType())) {
                snapshot.put(field.getKey(), Map.of(
                        "dictType", field.getDictType(),
                        "value", value,
                        "label", labelOf(dictOptions, String.valueOf(value))));
            } else {
                @SuppressWarnings("unchecked")
                Collection<Object> values = (Collection<Object>) value;
                snapshot.put(field.getKey(), Map.of(
                        "dictType", field.getDictType(),
                        "values", values.stream().map(String::valueOf).toList(),
                        "labels", values.stream().map(v -> labelOf(dictOptions, String.valueOf(v))).toList()));
            }
        }
        return snapshot;
    }

    public void validateAttachmentField(ForcedFormFieldDefinition field, Collection<?> attachments) {
        if (field == null || !"attachment".equals(field.getType())) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
        }
        if (attachments == null || attachments.isEmpty()) {
            if (Boolean.TRUE.equals(field.getRequired())) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED);
            }
            return;
        }
        if (field.getMaxCount() != null && attachments.size() > field.getMaxCount()) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
        }
    }

    private void validateDefinitions(List<ForcedFormFieldDefinition> fields) {
        if (fields == null || fields.isEmpty() || fields.size() > 100) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
        }
        Set<String> keys = new HashSet<>();
        for (ForcedFormFieldDefinition field : fields) {
            if (field == null || field.getKey() == null || !field.getKey().matches("[a-z][a-z0-9_]{0,63}")
                    || !keys.add(field.getKey()) || field.getLabel() == null || field.getLabel().isBlank()
                    || field.getType() == null || !ALLOWED_TYPES.contains(field.getType())) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
            }
            if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
            }
            if (Set.of("radio", "multi-select").contains(field.getType())) {
                if (field.getDictType() == null || field.getDictType().isBlank()) {
                    throw exception(ZsjosErrorCodeConstants.FORCED_FORM_DICT_INVALID);
                }
                if (dictDataApi.getDictDataList(field.getDictType()).isEmpty()) {
                    throw exception(ZsjosErrorCodeConstants.FORCED_FORM_DICT_INVALID);
                }
            }
            if (field.getMaxLength() != null && (field.getMaxLength() < 1 || field.getMaxLength() > 10_000)) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
            }
            if (field.getMaxCount() != null && (field.getMaxCount() < 1 || field.getMaxCount() > 20)) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
            }
            if (field.getMaxSizeMb() != null && (field.getMaxSizeMb() < 1 || field.getMaxSizeMb() > 500)) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
            }
            if (field.getAllowedExtensions() != null && field.getAllowedExtensions().stream().anyMatch(ext ->
                    ext == null || ext.isBlank() || ext.startsWith(".") || ext.contains("/") || ext.contains("\\"))) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
            }
        }
    }

    private Object normalizeAnswer(ForcedFormFieldDefinition field, Object value) {
        if (Set.of("text", "textarea").contains(field.getType())) {
            if (!(value instanceof String str)) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
            }
            if (field.getMaxLength() != null && str.length() > field.getMaxLength()) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
            }
            return str;
        }
        if ("checkbox".equals(field.getType())) {
            if (!(value instanceof Boolean bool)) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
            }
            if (!bool && Boolean.TRUE.equals(field.getRequired())) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED);
            }
            return bool;
        }
        if ("radio".equals(field.getType())) {
            String str = String.valueOf(value);
            validateDictValues(field, List.of(str));
            return str;
        }
        if ("multi-select".equals(field.getType())) {
            List<String> values = toStringList(value);
            validateDictValues(field, values);
            return values;
        }
        if ("attachment".equals(field.getType())) {
            List<String> tokens = toStringList(value);
            validateAttachmentField(field, tokens);
            return tokens;
        }
        throw exception(ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
    }

    private void validateDictValues(ForcedFormFieldDefinition field, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            if (Boolean.TRUE.equals(field.getRequired())) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_REQUIRED);
            }
            return;
        }
        try {
            dictDataApi.validateDictDataList(field.getDictType(), values);
        } catch (RuntimeException ignored) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_DICT_INVALID);
        }
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        if (value instanceof String str && !str.isBlank()) {
            return List.of(str);
        }
        throw exception(ZsjosErrorCodeConstants.FORCED_FORM_SUBMIT_INVALID);
    }

    private static String labelOf(List<Map<String, String>> options, String value) {
        return options.stream().filter(item -> Objects.equals(item.get("value"), value))
                .map(item -> item.get("label")).findFirst().orElse(value);
    }
}
