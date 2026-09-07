package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmFormMetadataRespDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackFormRespVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_ATTACHMENT_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_FORM_INCOMPATIBLE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_FORM_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_TITLE_FIELD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_VALUE_INVALID;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_SURVEY;

@Service
public class FeedbackDynamicFormService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "text", "textarea", "date", "dictionary", "upload", "image", "rating");

    @Resource
    private BpmDefinitionReadApi definitionReadApi;
    @Resource
    private DictDataApi dictDataApi;
    @Resource
    private FileApi fileApi;

    public ParsedForm requireCompatibleForm(Long formId, String feedbackType, String titleFieldKey) {
        BpmFormMetadataRespDTO metadata = definitionReadApi.getForm(formId);
        if (metadata == null || !CommonStatusEnum.ENABLE.getStatus().equals(metadata.getStatus())) {
            throw exception(FEEDBACK_FORM_NOT_EXISTS);
        }
        ParsedForm parsed = parse(metadata);
        if (!parsed.incompatibleFields().isEmpty()) {
            throw exception(FEEDBACK_FORM_INCOMPATIBLE, String.join("、", parsed.incompatibleFields()));
        }
        if (TYPE_SURVEY.equals(feedbackType)) {
            FeedbackFormRespVO.Field rating = parsed.fields().stream()
                    .filter(field -> Objects.equals(field.getKey(), titleFieldKey))
                    .findFirst().orElse(null);
            if (rating == null || !"rating".equals(rating.getType()) || !Boolean.TRUE.equals(rating.getRequired())) {
                throw exception(FEEDBACK_TITLE_FIELD_INVALID);
            }
        } else if (!parsed.requiredTextFieldKeys().contains(titleFieldKey)) {
            throw exception(FEEDBACK_TITLE_FIELD_INVALID);
        }
        return parsed;
    }

    public List<FeedbackConfigVO.FormOption> getFormOptions() {
        return definitionReadApi.getForms().stream().map(this::parse).map(parsed -> {
            FeedbackConfigVO.FormOption option = new FeedbackConfigVO.FormOption();
            option.setId(parsed.formId());
            option.setName(parsed.formName());
            option.setIncompatibleFields(parsed.incompatibleFields());
            option.setRequiredTextFieldKeys(parsed.requiredTextFieldKeys());
            option.setRequiredRatingFieldKeys(parsed.requiredRatingFieldKeys());
            return option;
        }).toList();
    }

    public ParsedForm parse(BpmFormMetadataRespDTO metadata) {
        List<FeedbackFormRespVO.Field> fields = new ArrayList<>();
        List<String> incompatible = new ArrayList<>();
        List<String> requiredTextKeys = new ArrayList<>();
        List<String> requiredRatingKeys = new ArrayList<>();
        for (String fieldJson : metadata.getFields() == null ? List.<String>of() : metadata.getFields()) {
            Map<?, ?> raw;
            try {
                raw = JsonUtils.parseObject(fieldJson, Map.class);
            } catch (RuntimeException error) {
                incompatible.add("无法解析的字段");
                continue;
            }
            String key = string(raw.get("field"));
            if (key == null) key = string(raw.get("vModel"));
            String label = string(raw.get("title"));
            if (label == null) label = string(raw.get("label"));
            if (key == null || label == null) {
                incompatible.add(label == null ? "未命名字段" : label);
                continue;
            }
            String normalizedType = normalizeType(raw);
            if (normalizedType == null || !ALLOWED_TYPES.contains(normalizedType)) {
                incompatible.add(label + "(" + Objects.toString(raw.get("type"), "未知控件") + ")");
                continue;
            }
            FeedbackFormRespVO.Field field = new FeedbackFormRespVO.Field();
            field.setKey(key);
            field.setLabel(label);
            field.setType(normalizedType);
            field.setRequired(required(raw));
            field.setDictionaryType(dictionaryType(raw));
            field.setMaxRating("rating".equals(normalizedType) ? integer(property(raw, "max"), 5) : null);
            field.setMaxLength(integer(property(raw, "maxlength"), defaultMaxLength(normalizedType)));
            if ("dictionary".equals(normalizedType) && field.getDictionaryType() == null) {
                incompatible.add(label + "(缺少字典类型)");
                continue;
            }
            if ("rating".equals(normalizedType) && !Objects.equals(field.getMaxRating(), 5)) {
                incompatible.add(label + "(评分范围必须为1-5)");
                continue;
            }
            if ("text".equals(normalizedType) && Boolean.TRUE.equals(field.getRequired())) {
                requiredTextKeys.add(key);
            }
            if ("rating".equals(normalizedType) && Boolean.TRUE.equals(field.getRequired())) {
                requiredRatingKeys.add(key);
            }
            fields.add(field);
        }
        return new ParsedForm(metadata.getId(), metadata.getName(), fields, incompatible,
                requiredTextKeys, requiredRatingKeys);
    }

    public FeedbackFormRespVO toResponse(String feedbackType, ParsedForm parsed, String titleFieldKey,
                                         Integer configVersion, boolean open, String unavailableReason) {
        FeedbackFormRespVO response = new FeedbackFormRespVO();
        response.setFeedbackType(feedbackType);
        response.setFormId(parsed.formId());
        response.setFormName(parsed.formName());
        response.setTitleFieldKey(titleFieldKey);
        response.setConfigVersion(configVersion);
        response.setOpen(open);
        response.setUnavailableReason(unavailableReason);
        response.setFields(withDictionaryOptions(parsed.fields()));
        return response;
    }

    public NormalizedValues normalizeValues(ParsedForm parsed, Map<String, Object> rawValues, Long userId) {
        Map<String, Object> source = rawValues == null ? Map.of() : rawValues;
        Set<String> knownKeys = new HashSet<>();
        Map<String, Object> normalized = new LinkedHashMap<>();
        List<Long> attachmentIds = new ArrayList<>();
        for (FeedbackFormRespVO.Field field : parsed.fields()) {
            knownKeys.add(field.getKey());
            Object value = source.get(field.getKey());
            if (empty(value)) {
                if (Boolean.TRUE.equals(field.getRequired())) {
                    throw exception(FEEDBACK_VALUE_INVALID, field.getLabel() + "不能为空");
                }
                continue;
            }
            normalized.put(field.getKey(), normalizeValue(field, value, userId, attachmentIds));
        }
        if (source.keySet().stream().anyMatch(key -> !knownKeys.contains(key))) {
            throw exception(FEEDBACK_VALUE_INVALID, "包含表单定义之外的字段");
        }
        return new NormalizedValues(normalized, attachmentIds.stream().distinct().toList());
    }

    public List<FeedbackFormRespVO.Field> parseSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) return List.of();
        return JsonUtils.parseArray(snapshotJson, FeedbackFormRespVO.Field.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseValueSnapshot(String valueJson) {
        if (valueJson == null || valueJson.isBlank()) return Map.of();
        return JsonUtils.parseObject(valueJson, Map.class);
    }

    public Map<String, Object> readDisplayValues(String valueJson, List<FeedbackFormRespVO.Field> fields) {
        Map<String, Object> values = parseValueSnapshot(valueJson);
        Map<Long, List<Map<Object, Object>>> attachmentsById = new LinkedHashMap<>();
        for (FeedbackFormRespVO.Field field : fields) {
            if (!"image".equals(field.getType()) && !"upload".equals(field.getType())) continue;
            if (!(values.get(field.getKey()) instanceof Collection<?> attachments)) continue;
            List<Object> resolved = new ArrayList<>();
            for (Object item : attachments) {
                if (!(item instanceof Map<?, ?> snapshot)) {
                    resolved.add(item);
                    continue;
                }
                Map<Object, Object> copy = new LinkedHashMap<>(snapshot);
                // Historical metadata stays frozen; expired upload URLs are never a read fallback.
                copy.put("url", null);
                if (snapshot.get("id") != null) {
                    try {
                        Long id = Long.valueOf(String.valueOf(snapshot.get("id")));
                        attachmentsById.computeIfAbsent(id, unused -> new ArrayList<>()).add(copy);
                    } catch (NumberFormatException ignored) {
                        // Malformed historical IDs cannot be used to recover an access URL.
                    }
                }
                resolved.add(copy);
            }
            values.put(field.getKey(), resolved);
        }
        Map<Long, String> urls = FeedbackFileUrls.resolve(fileApi, attachmentsById.keySet());
        attachmentsById.forEach((id, attachments) ->
                attachments.forEach(attachment -> attachment.put("url", urls.get(id))));
        return values;
    }

    private Object normalizeValue(FeedbackFormRespVO.Field field, Object value, Long userId,
                                  List<Long> attachmentIds) {
        try {
            return switch (field.getType()) {
                case "text", "textarea" -> normalizeText(field, value);
                case "date" -> LocalDate.parse(String.valueOf(value)).toString();
                case "dictionary" -> dictionarySnapshot(field.getDictionaryType(), String.valueOf(value));
                case "upload", "image" -> fileSnapshots(value, userId, attachmentIds);
                case "rating" -> normalizeRating(value);
                default -> throw exception(FEEDBACK_VALUE_INVALID, field.getLabel());
            };
        } catch (RuntimeException error) {
            if (error instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) throw error;
            throw exception(FEEDBACK_VALUE_INVALID, field.getLabel());
        }
    }

    private String normalizeText(FeedbackFormRespVO.Field field, Object value) {
        String text = String.valueOf(value).trim();
        if (text.isEmpty() && Boolean.TRUE.equals(field.getRequired())) {
            throw exception(FEEDBACK_VALUE_INVALID, field.getLabel() + "不能为空");
        }
        if (field.getMaxLength() != null && text.length() > field.getMaxLength()) {
            throw exception(FEEDBACK_VALUE_INVALID, field.getLabel() + "超过长度限制");
        }
        return text;
    }

    private Map<String, Object> dictionarySnapshot(String dictType, String value) {
        DictDataRespDTO item = dictDataApi.getDictDataList(dictType).stream()
                .filter(data -> Objects.equals(data.getValue(), value)
                        && CommonStatusEnum.ENABLE.getStatus().equals(data.getStatus()))
                .findFirst().orElseThrow(() -> exception(FEEDBACK_VALUE_INVALID, "字典选项已失效"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", dictType);
        snapshot.put("value", value);
        snapshot.put("label", item.getLabel());
        return snapshot;
    }

    private List<Map<String, Object>> fileSnapshots(Object value, Long userId, List<Long> attachmentIds) {
        Collection<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
        if (values.size() > 20) throw exception(FEEDBACK_ATTACHMENT_INVALID);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            Long id;
            if (item instanceof Map<?, ?> map && map.get("id") != null) {
                id = Long.valueOf(String.valueOf(map.get("id")));
            } else {
                id = Long.valueOf(String.valueOf(item));
            }
            FileInfoRespDTO file;
            try {
                file = fileApi.getFileInfo(id);
            } catch (RuntimeException error) {
                throw exception(FEEDBACK_ATTACHMENT_INVALID);
            }
            if (file == null || file.getPath() == null || !file.getPath().startsWith("zsjos/feedback/")
                    || !String.valueOf(userId).equals(file.getCreator())) {
                throw exception(FEEDBACK_ATTACHMENT_INVALID);
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", id);
            snapshot.put("name", file.getName());
            snapshot.put("type", file.getType());
            snapshot.put("size", file.getSize());
            snapshot.put("url", file.getUrl());
            result.add(snapshot);
            attachmentIds.add(id);
        }
        return result;
    }

    private Integer normalizeRating(Object value) {
        double numeric = value instanceof Number number ? number.doubleValue()
                : Double.parseDouble(String.valueOf(value));
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)) {
            throw exception(FEEDBACK_VALUE_INVALID, "总体满意度必须为1-5的整数");
        }
        int rating = (int) numeric;
        if (rating < 1 || rating > 5) throw exception(FEEDBACK_VALUE_INVALID, "总体满意度必须为1-5分");
        return rating;
    }

    private List<FeedbackFormRespVO.Field> withDictionaryOptions(List<FeedbackFormRespVO.Field> fields) {
        return fields.stream().map(field -> {
            FeedbackFormRespVO.Field copy = JsonUtils.parseObject(
                    JsonUtils.toJsonString(field), FeedbackFormRespVO.Field.class);
            if ("dictionary".equals(copy.getType())) {
                copy.setOptions(dictDataApi.getDictDataList(copy.getDictionaryType()).stream()
                        .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                        .map(item -> {
                            FeedbackFormRespVO.Option option = new FeedbackFormRespVO.Option();
                            option.setValue(item.getValue());
                            option.setLabel(item.getLabel());
                            return option;
                        }).toList());
            }
            return copy;
        }).toList();
    }

    private String normalizeType(Map<?, ?> raw) {
        String rawType = string(raw.get("type"));
        if (rawType == null) return null;
        String type = rawType.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        if ("input".equals(type)) {
            return "textarea".equalsIgnoreCase(string(property(raw, "type"))) ? "textarea" : "text";
        }
        return switch (type) {
            case "text" -> "text";
            case "textarea" -> "textarea";
            case "date", "datepicker" -> "date";
            case "select", "radio", "dictselect", "dictionary" -> "dictionary";
            case "upload", "fileupload" -> "upload";
            case "image", "imageupload" -> "image";
            case "rate", "rating" -> "rating";
            default -> null;
        };
    }

    private boolean required(Map<?, ?> raw) {
        if (Boolean.TRUE.equals(raw.get("required"))) return true;
        Object validate = raw.get("validate");
        if (validate instanceof Collection<?> rules) {
            return rules.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .anyMatch(rule -> Boolean.TRUE.equals(rule.get("required")));
        }
        return false;
    }

    private String dictionaryType(Map<?, ?> raw) {
        String type = string(raw.get("dictType"));
        return type != null ? type : string(property(raw, "dictType"));
    }

    private Object property(Map<?, ?> raw, String key) {
        return raw.get("props") instanceof Map<?, ?> properties ? properties.get(key) : null;
    }

    private Integer defaultMaxLength(String type) {
        if ("text".equals(type)) return 255;
        if ("textarea".equals(type)) return 5000;
        return null;
    }

    private Integer integer(Object value, Integer defaultValue) {
        return value == null ? defaultValue : Integer.valueOf(String.valueOf(value));
    }

    private String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean empty(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> collection && collection.isEmpty();
    }

    public record ParsedForm(Long formId, String formName, List<FeedbackFormRespVO.Field> fields,
                             List<String> incompatibleFields, List<String> requiredTextFieldKeys,
                             List<String> requiredRatingFieldKeys) {
    }

    public record NormalizedValues(Map<String, Object> values, List<Long> attachmentIds) {
    }
}
