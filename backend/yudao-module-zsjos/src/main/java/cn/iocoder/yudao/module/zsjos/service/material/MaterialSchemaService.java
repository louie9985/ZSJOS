package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_FIELD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_FILE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_SCHEMA_INVALID;

@Service
public class MaterialSchemaService {

    private static final Pattern FIELD_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_TOP_LEVEL_FIELDS = 100;
    private static final int MAX_GROUP_FIELDS = 50;
    private static final int MAX_GROUP_ROWS = 100;
    private static final int DEFAULT_TEXT_LENGTH = 255;
    private static final int DEFAULT_TEXTAREA_LENGTH = 10_000;
    private static final int DEFAULT_RICH_TEXT_LENGTH = 200_000;
    private static final int DEFAULT_FILE_COUNT = 20;
    private static final int DEFAULT_FILE_SIZE_MB = 100;
    private static final int INDEX_NUMBER_PRECISION = 24;
    private static final int INDEX_NUMBER_SCALE = 6;

    @Resource private DictDataApi dictDataApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private FileApi fileApi;
    @Resource private XssCleaner xssCleaner;

    public List<MaterialFieldDefinition> parseFields(String fieldsJson) {
        try {
            List<MaterialFieldDefinition> fields = JsonUtils.parseArray(fieldsJson, MaterialFieldDefinition.class);
            validateSchema(fields, false);
            return fields;
        } catch (RuntimeException error) {
            if (error instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) {
                throw error;
            }
            throw exception(MATERIAL_SCHEMA_INVALID, "字段定义无法解析");
        }
    }

    public void validateSchemaForPublish(List<MaterialFieldDefinition> fields) {
        validateSchema(fields, true);
    }

    public void validateSchema(List<MaterialFieldDefinition> fields, boolean validateDictionaryOptions) {
        if (fields == null || fields.isEmpty()) {
            throw exception(MATERIAL_SCHEMA_INVALID, "至少配置一个字段");
        }
        if (fields.size() > MAX_TOP_LEVEL_FIELDS) {
            throw exception(MATERIAL_SCHEMA_INVALID, "一级字段不能超过" + MAX_TOP_LEVEL_FIELDS + "个");
        }
        Set<String> keys = new HashSet<>();
        Set<String> dimensions = new HashSet<>();
        for (MaterialFieldDefinition field : fields) {
            validateField(field, false, keys, dimensions, validateDictionaryOptions);
        }
    }

    public NormalizedMaterial normalize(List<MaterialFieldDefinition> fields, Map<String, Object> rawValues,
                                        Long userId) {
        return normalize(fields, rawValues, userId, Set.of(), Map.of(), true);
    }

    /** 草稿允许缺少必填字段，但已填写值仍必须经过完整格式校验。 */
    public NormalizedMaterial normalizeDraft(List<MaterialFieldDefinition> fields, Map<String, Object> rawValues,
                                             Long userId) {
        return normalize(fields, rawValues, userId, Set.of(), Map.of(), false);
    }

    public void validatePartialValues(List<MaterialFieldDefinition> fields, Map<String, Object> rawValues,
                                      Long userId) {
        validateSchema(fields, false);
        Map<String, Object> source = rawValues == null ? Map.of() : rawValues;
        Map<String, MaterialFieldDefinition> fieldsByKey = fields.stream()
                .collect(java.util.stream.Collectors.toMap(MaterialFieldDefinition::getKey,
                        field -> field, (left, right) -> left, LinkedHashMap::new));
        if (source.keySet().stream().anyMatch(key -> !fieldsByKey.containsKey(key))) {
            throw exception(MATERIAL_FIELD_INVALID, "包含模板之外的字段");
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!empty(entry.getValue())) {
                normalizeField(fieldsByKey.get(entry.getKey()), entry.getValue(), userId, Set.of(),
                        Map.of(), -1, entry.getKey(), false);
            }
        }
    }

    NormalizedMaterial normalize(List<MaterialFieldDefinition> fields, Map<String, Object> rawValues,
                                 Long userId, Set<Long> trustedBusinessFileIds) {
        return normalize(fields, rawValues, userId, trustedBusinessFileIds, Map.of());
    }

    NormalizedMaterial normalize(List<MaterialFieldDefinition> fields, Map<String, Object> rawValues,
                                 Long userId, Set<Long> trustedBusinessFileIds,
                                 Map<String, DictionarySnapshotValue> trustedDictionarySnapshots) {
        return normalize(fields, rawValues, userId, trustedBusinessFileIds, trustedDictionarySnapshots, true);
    }

    private NormalizedMaterial normalize(List<MaterialFieldDefinition> fields, Map<String, Object> rawValues,
                                         Long userId, Set<Long> trustedBusinessFileIds,
                                         Map<String, DictionarySnapshotValue> trustedDictionarySnapshots,
                                         boolean requireRequired) {
        validateSchema(fields, false);
        Map<String, Object> source = rawValues == null ? Map.of() : rawValues;
        Set<Long> trustedFileIds = trustedBusinessFileIds == null ? Set.of() : trustedBusinessFileIds;
        Map<String, DictionarySnapshotValue> dictionarySnapshots = trustedDictionarySnapshots == null
                ? Map.of() : trustedDictionarySnapshots;
        Set<String> knownKeys = new HashSet<>();
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> snapshots = new LinkedHashMap<>();
        List<IndexValue> indexes = new ArrayList<>();
        List<FileValue> files = new ArrayList<>();
        List<DimensionValue> dimensions = new ArrayList<>();
        List<String> searchParts = new ArrayList<>();
        for (MaterialFieldDefinition field : fields) {
            knownKeys.add(field.getKey());
            Object raw = source.get(field.getKey());
            if (empty(raw)) {
                if (requireRequired && Boolean.TRUE.equals(field.getRequired())) {
                    throw fieldError(field, "不能为空");
                }
                continue;
            }
            NormalizedValue normalized = normalizeField(field, raw, userId, trustedFileIds,
                    dictionarySnapshots, -1, field.getKey(), false);
            values.put(field.getKey(), normalized.value());
            if (normalized.snapshot() != null) {
                snapshots.put(field.getKey(), normalized.snapshot());
            }
            indexes.addAll(normalized.indexes());
            files.addAll(normalized.files());
            dimensions.addAll(normalized.dimensions());
            searchParts.addAll(normalized.searchParts());
        }
        if (source.keySet().stream().anyMatch(key -> !knownKeys.contains(key))) {
            throw exception(MATERIAL_FIELD_INVALID, "包含模板之外的字段");
        }
        String searchText = String.join(" ", searchParts).trim();
        return new NormalizedMaterial(values, snapshots, indexes, files, dimensions, searchText);
    }

    public FileValue normalizeCover(Long fileId, Long userId) {
        return normalizeCover(fileId, userId, Set.of());
    }

    FileValue normalizeCover(Long fileId, Long userId, Set<Long> trustedBusinessFileIds) {
        if (fileId == null) {
            return null;
        }
        return snapshotFile("__cover__", -1, fileId, userId, FIELD_IMAGE, null, null,
                trustedBusinessFileIds == null ? Set.of() : trustedBusinessFileIds);
    }

    private void validateField(MaterialFieldDefinition field, boolean nested, Set<String> siblingKeys,
                               Set<String> dimensions, boolean validateDictionaryOptions) {
        if (field == null || field.getKey() == null || !FIELD_KEY.matcher(field.getKey()).matches()) {
            throw exception(MATERIAL_SCHEMA_INVALID, "字段编码必须以小写字母开头且只包含小写字母、数字和下划线");
        }
        if (!siblingKeys.add(field.getKey())) {
            throw exception(MATERIAL_SCHEMA_INVALID, "字段编码重复：" + field.getKey());
        }
        if (field.getLabel() == null || field.getLabel().isBlank() || field.getLabel().length() > 100) {
            throw exception(MATERIAL_SCHEMA_INVALID, "字段 " + field.getKey() + " 的名称无效");
        }
        if (field.getSection() != null && !FIELD_SECTIONS.contains(field.getSection())) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的页面分区无效");
        }
        if (!FIELD_TYPES.contains(field.getType())) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的组件类型不受支持");
        }
        if (nested && FIELD_REPEAT_GROUP.equals(field.getType())) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 不能嵌套重复字段组");
        }
        if (FIELD_REPEAT_GROUP.equals(field.getType())) {
            if (field.getChildren() == null || field.getChildren().isEmpty()
                    || field.getChildren().size() > MAX_GROUP_FIELDS) {
                throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的子字段数量无效");
            }
            validateCountRange(field, MAX_GROUP_ROWS);
            Set<String> childKeys = new HashSet<>();
            for (MaterialFieldDefinition child : field.getChildren()) {
                validateField(child, true, childKeys, dimensions, validateDictionaryOptions);
            }
        } else if (field.getChildren() != null && !field.getChildren().isEmpty()) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 不能配置子字段");
        }
        if (FIELD_DICT_SINGLE.equals(field.getType()) || FIELD_DICT_MULTI.equals(field.getType())) {
            if (field.getDictType() == null || field.getDictType().isBlank() || field.getDictType().length() > 100) {
                throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 缺少字典类型");
            }
            if (validateDictionaryOptions && enabledDictionary(field.getDictType()).isEmpty()) {
                throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的字典没有可用选项");
            }
        } else if (field.getDictType() != null && !field.getDictType().isBlank()) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 不是字典字段");
        }
        validateRecommendationDimension(field, nested, dimensions);
        validateLimits(field);
    }

    /**
     * 推荐维度由字段字典自动归属，不再人工配置；每个维度在模板内只能有一个承载字段。
     */
    private void validateRecommendationDimension(MaterialFieldDefinition field, boolean nested,
                                                 Set<String> dimensions) {
        String dimension = recommendationDimensionOf(field.getDictType());
        if (dimension == null) {
            if (Boolean.TRUE.equals(field.getAllowUnlimited())) {
                throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 不是推荐维度字典字段，不能配置不限");
            }
            return;
        }
        if (nested) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 是推荐维度字段，不能放在重复字段组中");
        }
        if (!dimensions.add(dimension)) {
            throw exception(MATERIAL_SCHEMA_INVALID, "推荐维度重复：" + dimension);
        }
    }

    private void validateLimits(MaterialFieldDefinition field) {
        if (field.getMaxLength() != null && (field.getMaxLength() < 1 || field.getMaxLength() > DEFAULT_RICH_TEXT_LENGTH)) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的长度限制无效");
        }
        if (field.getMin() != null && field.getMax() != null && field.getMin().compareTo(field.getMax()) > 0) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的数值范围无效");
        }
        boolean supportsCount = FIELD_DICT_MULTI.equals(field.getType())
                || FILE_FIELD_TYPES.contains(field.getType())
                || (FIELD_EMPLOYEE.equals(field.getType()) || FIELD_DEPARTMENT.equals(field.getType()))
                && Boolean.TRUE.equals(field.getMultiple());
        if (!supportsCount && !FIELD_REPEAT_GROUP.equals(field.getType())
                && (field.getMinCount() != null || field.getMaxCount() != null)) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 不是多值字段，不能配置数量限制");
        }
        if (supportsCount) {
            validateCountRange(field, fieldMaxCount(field));
        }
        if (field.getMaxSizeMb() != null && (field.getMaxSizeMb() < 1 || field.getMaxSizeMb() > 1024)) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的文件大小限制无效");
        }
        if (field.getAllowedExtensions() != null && field.getAllowedExtensions().stream()
                .anyMatch(extension -> extension == null || !extension.matches("[A-Za-z0-9]{1,16}"))) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的扩展名限制无效");
        }
    }

    private int fieldMaxCount(MaterialFieldDefinition field) {
        return FILE_FIELD_TYPES.contains(field.getType()) ? 100 : 500;
    }

    private void validateCountRange(MaterialFieldDefinition field, int maximum) {
        int min = field.getMinCount() == null ? 0 : field.getMinCount();
        int max = field.getMaxCount() == null ? maximum : field.getMaxCount();
        if (min < 0 || max < 1 || max > maximum || min > max) {
            throw exception(MATERIAL_SCHEMA_INVALID, field.getLabel() + " 的数量范围无效");
        }
    }

    @SuppressWarnings("unchecked")
    private NormalizedValue normalizeField(MaterialFieldDefinition field, Object raw, Long userId,
                                           Set<Long> trustedBusinessFileIds,
                                           Map<String, DictionarySnapshotValue> trustedDictionarySnapshots,
                                           int groupIndex,
                                           String fieldPath, boolean nested) {
        try {
            return switch (field.getType()) {
                case FIELD_TEXT, FIELD_TEXTAREA -> textValue(field, raw, fieldPath, groupIndex, false);
                case FIELD_RICH_TEXT -> textValue(field, raw, fieldPath, groupIndex, true);
                case FIELD_NUMBER -> numberValue(field, raw, fieldPath, groupIndex);
                case FIELD_DATE -> dateValue(field, raw, fieldPath, groupIndex);
                case FIELD_DATETIME -> datetimeValue(field, raw, fieldPath, groupIndex);
                case FIELD_DICT_SINGLE -> dictionaryValue(field, List.of(raw), fieldPath, groupIndex, false,
                        trustedDictionarySnapshots);
                case FIELD_DICT_MULTI -> dictionaryValue(field, collection(raw), fieldPath, groupIndex, true,
                        trustedDictionarySnapshots);
                case FIELD_EMPLOYEE -> entityValue(field, raw, fieldPath, groupIndex, true);
                case FIELD_DEPARTMENT -> entityValue(field, raw, fieldPath, groupIndex, false);
                case FIELD_IMAGE, FIELD_VIDEO, FIELD_ATTACHMENT -> fileValue(field, raw, userId,
                        trustedBusinessFileIds, fieldPath, groupIndex);
                case FIELD_HTTPS_LINK -> linkValue(field, raw, fieldPath, groupIndex);
                case FIELD_REPEAT_GROUP -> groupValue(field, raw, userId, trustedBusinessFileIds,
                        trustedDictionarySnapshots, fieldPath);
                default -> throw fieldError(field, "组件类型不受支持");
            };
        } catch (RuntimeException error) {
            if (error instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) {
                throw error;
            }
            throw fieldError(field, "格式不正确");
        }
    }

    private NormalizedValue textValue(MaterialFieldDefinition field, Object raw, String path, int groupIndex,
                                      boolean richText) {
        String text = String.valueOf(raw).trim();
        if (richText) {
            text = xssCleaner.clean(text);
        }
        int defaultLength = richText ? DEFAULT_RICH_TEXT_LENGTH
                : FIELD_TEXT.equals(field.getType()) ? DEFAULT_TEXT_LENGTH : DEFAULT_TEXTAREA_LENGTH;
        if (text.length() > (field.getMaxLength() == null ? defaultLength : field.getMaxLength())) {
            throw fieldError(field, "超过长度限制");
        }
        String plain = richText ? Jsoup.parse(text).text() : text;
        if (Boolean.TRUE.equals(field.getRequired()) && plain.isBlank()) {
            throw fieldError(field, "不能为空");
        }
        IndexValue index = Boolean.TRUE.equals(field.getSearchable())
                ? IndexValue.text(path, groupIndex, plain) : null;
        return value(text, null, index, null, null, plain);
    }

    private NormalizedValue numberValue(MaterialFieldDefinition field, Object raw, String path, int groupIndex) {
        BigDecimal number = raw instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(raw));
        int scale = Math.max(0, number.scale());
        int integerDigits = Math.max(0, number.precision() - number.scale());
        if (integerDigits > INDEX_NUMBER_PRECISION - INDEX_NUMBER_SCALE || scale > INDEX_NUMBER_SCALE) {
            throw fieldError(field, "最多支持 18 位整数和 6 位小数");
        }
        if (field.getMin() != null && number.compareTo(field.getMin()) < 0
                || field.getMax() != null && number.compareTo(field.getMax()) > 0) {
            throw fieldError(field, "超出数值范围");
        }
        return value(number, null, IndexValue.number(path, groupIndex, number), null, null, number.toPlainString());
    }

    private NormalizedValue dateValue(MaterialFieldDefinition field, Object raw, String path, int groupIndex) {
        LocalDate date = LocalDate.parse(String.valueOf(raw));
        return value(date.toString(), null, IndexValue.date(path, groupIndex, date), null, null, date.toString());
    }

    private NormalizedValue datetimeValue(MaterialFieldDefinition field, Object raw, String path, int groupIndex) {
        LocalDateTime time;
        try {
            time = LocalDateTime.parse(String.valueOf(raw));
        } catch (DateTimeParseException first) {
            time = LocalDateTime.parse(String.valueOf(raw).replace(' ', 'T'));
        }
        return value(time.toString(), null, IndexValue.datetime(path, groupIndex, time), null, null, time.toString());
    }

    private NormalizedValue dictionaryValue(MaterialFieldDefinition field, Collection<?> rawValues, String path,
                                             int groupIndex, boolean multiple,
                                             Map<String, DictionarySnapshotValue> trustedDictionarySnapshots) {
        int max = field.getMaxCount() == null ? 100 : field.getMaxCount();
        int min = field.getMinCount() == null ? 0 : field.getMinCount();
        String dimension = recommendationDimensionOf(field.getDictType());
        Map<String, DictDataRespDTO> options = new LinkedHashMap<>();
        enabledDictionary(field.getDictType()).forEach(option -> options.put(option.getValue(), option));
        List<String> values = new ArrayList<>();
        List<Map<String, Object>> snapshots = new ArrayList<>();
        List<IndexValue> indexes = new ArrayList<>();
        List<DimensionValue> dimensions = new ArrayList<>();
        List<String> search = new ArrayList<>();
        for (Object item : rawValues) {
            String code = String.valueOf(item).trim();
            if (values.contains(code)) {
                continue;
            }
            boolean unlimited = VALUE_UNLIMITED.equals(code);
            DictDataRespDTO option = unlimited ? null : options.get(code);
            DictionarySnapshotValue trusted = trustedDictionarySnapshots.get(path);
            boolean trustedMatch = trusted != null
                    && Objects.equals(trusted.dictType(), field.getDictType())
                    && Objects.equals(trusted.code(), code)
                    && trusted.label() != null && !trusted.label().isBlank();
            if (unlimited && !Boolean.TRUE.equals(field.getAllowUnlimited())
                    || !unlimited && option == null && !trustedMatch) {
                throw fieldError(field, "包含已失效的字典选项");
            }
            String label = unlimited ? LABEL_UNLIMITED : trustedMatch ? trusted.label() : option.getLabel();
            values.add(code);
            snapshots.add(snapshot("type", field.getDictType(), "value", code, "label", label));
            indexes.add(IndexValue.code(path, groupIndex, code, label));
            if (dimension != null) {
                dimensions.add(new DimensionValue(dimension, code, label, unlimited));
            }
            search.add(label);
        }
        if (values.size() < min || values.size() > max || !multiple && values.size() != 1) {
            throw fieldError(field, "选择数量不符合要求");
        }
        if (values.contains(VALUE_UNLIMITED) && values.size() > 1) {
            throw fieldError(field, "不限不能与其他选项同时选择");
        }
        Object value = multiple ? values : values.get(0);
        Object snapshot = multiple ? snapshots : snapshots.get(0);
        return new NormalizedValue(value, snapshot, indexes, List.of(), dimensions, search);
    }

    private NormalizedValue entityValue(MaterialFieldDefinition field, Object raw, String path, int groupIndex,
                                        boolean employee) {
        boolean multiple = Boolean.TRUE.equals(field.getMultiple());
        Collection<?> rawIds = multiple ? collection(raw) : List.of(raw);
        int max = field.getMaxCount() == null ? 100 : field.getMaxCount();
        int min = field.getMinCount() == null ? 0 : field.getMinCount();
        List<Long> ids = rawIds.stream().map(value -> Long.valueOf(String.valueOf(value))).distinct().toList();
        if (ids.size() < min || ids.size() > max || !multiple && ids.size() != 1) {
            throw fieldError(field, "选择数量不符合要求");
        }
        Map<Long, String> labels = new LinkedHashMap<>();
        if (employee) {
            adminUserApi.validateUserList(ids);
            adminUserApi.getUserList(ids).forEach(user -> labels.put(user.getId(), user.getNickname()));
        } else {
            deptApi.validateDeptList(ids);
            deptApi.getDeptList(ids).forEach(dept -> labels.put(dept.getId(), dept.getName()));
        }
        if (labels.size() != ids.size()) {
            throw fieldError(field, "包含无效选项");
        }
        List<Map<String, Object>> snapshots = ids.stream()
                .map(id -> snapshot("id", id, "label", labels.get(id))).toList();
        List<IndexValue> indexes = ids.stream()
                .map(id -> IndexValue.code(path, groupIndex, String.valueOf(id), labels.get(id))).toList();
        Object value = multiple ? ids : ids.get(0);
        Object snapshot = multiple ? snapshots : snapshots.get(0);
        return new NormalizedValue(value, snapshot, indexes, List.of(), List.of(), new ArrayList<>(labels.values()));
    }

    private NormalizedValue fileValue(MaterialFieldDefinition field, Object raw, Long userId,
                                      Set<Long> trustedBusinessFileIds, String path, int groupIndex) {
        List<Long> rawIds = collection(raw).stream().map(this::fileId).distinct().toList();
        int max = field.getMaxCount() == null ? DEFAULT_FILE_COUNT : field.getMaxCount();
        int min = field.getMinCount() == null ? 0 : field.getMinCount();
        if (rawIds.size() < min || rawIds.size() > max) {
            throw fieldError(field, "文件数量不符合要求");
        }
        List<FileValue> files = rawIds.stream().map(fileId -> snapshotFile(path, groupIndex,
                fileId, userId, field.getType(), field.getMaxSizeMb(), field.getAllowedExtensions(),
                trustedBusinessFileIds)).toList();
        List<Long> ids = files.stream().map(FileValue::infraFileId).toList();
        List<Map<String, Object>> snapshots = files.stream().map(FileValue::snapshot).toList();
        return new NormalizedValue(ids, snapshots, List.of(), files, List.of(),
                files.stream().map(FileValue::originalName).toList());
    }

    private FileValue snapshotFile(String path, int groupIndex, Long fileId, Long userId, String fieldType,
                                   Integer maxSizeMb, List<String> allowedExtensions,
                                   Set<Long> trustedBusinessFileIds) {
        FileInfoRespDTO file;
        try {
            file = fileApi.getFileInfo(fileId);
        } catch (RuntimeException error) {
            throw exception(MATERIAL_FILE_INVALID, "文件不存在");
        }
        boolean trustedBusinessFile = trustedBusinessFileIds.contains(fileId);
        if (file == null || !trustedBusinessFile && (file.getPath() == null
                || !file.getPath().startsWith("zsjos/material/")
                || !Objects.equals(String.valueOf(userId), file.getCreator()))) {
            throw exception(MATERIAL_FILE_INVALID, "文件不属于当前上传人或素材目录");
        }
        String contentType = Objects.toString(file.getType(), "").toLowerCase(Locale.ROOT);
        if (FIELD_IMAGE.equals(fieldType) && !contentType.startsWith("image/")
                || FIELD_VIDEO.equals(fieldType) && !contentType.startsWith("video/")) {
            throw exception(MATERIAL_FILE_INVALID, "文件类型与字段不匹配");
        }
        long maxBytes = (long) (maxSizeMb == null ? DEFAULT_FILE_SIZE_MB : maxSizeMb) * 1024 * 1024;
        if (file.getSize() == null || file.getSize() < 0 || file.getSize() > maxBytes) {
            throw exception(MATERIAL_FILE_INVALID, "文件大小超过限制");
        }
        if (allowedExtensions != null && !allowedExtensions.isEmpty()) {
            String extension = extension(file.getName());
            if (allowedExtensions.stream().noneMatch(item -> item.equalsIgnoreCase(extension))) {
                throw exception(MATERIAL_FILE_INVALID, "文件扩展名不受支持");
            }
        }
        Map<String, Object> snapshot = snapshot("id", file.getId(), "name", file.getName(),
                "contentType", file.getType(), "size", file.getSize(), "url", file.getUrl());
        return new FileValue(path, groupIndex, file.getId(), file.getUrl(), file.getName(), file.getType(),
                file.getSize(), trustedBusinessFile ? longValue(file.getCreator()) : userId, snapshot);
    }

    private NormalizedValue linkValue(MaterialFieldDefinition field, Object raw, String path, int groupIndex) {
        String link = String.valueOf(raw).trim();
        URI uri = URI.create(link);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || link.length() > 2048) {
            throw fieldError(field, "必须是有效的 HTTPS 链接");
        }
        return value(link, null, IndexValue.text(path, groupIndex, link), null, null, link);
    }

    private NormalizedValue groupValue(MaterialFieldDefinition field, Object raw, Long userId,
                                       Set<Long> trustedBusinessFileIds,
                                       Map<String, DictionarySnapshotValue> trustedDictionarySnapshots,
                                       String path) {
        Collection<?> rows = collection(raw);
        int min = field.getMinCount() == null ? 0 : field.getMinCount();
        int max = field.getMaxCount() == null ? MAX_GROUP_ROWS : field.getMaxCount();
        if (rows.size() < min || rows.size() > max) {
            throw fieldError(field, "重复组条数不符合要求");
        }
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        List<Map<String, Object>> snapshotRows = new ArrayList<>();
        List<IndexValue> indexes = new ArrayList<>();
        List<FileValue> files = new ArrayList<>();
        List<String> search = new ArrayList<>();
        int rowIndex = 0;
        for (Object item : rows) {
            if (!(item instanceof Map<?, ?> rawRow)) {
                throw fieldError(field, "重复组行格式无效");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            rawRow.forEach((key, value) -> row.put(String.valueOf(key), value));
            Map<String, Object> normalizedRow = new LinkedHashMap<>();
            Map<String, Object> snapshotRow = new LinkedHashMap<>();
            Set<String> known = new HashSet<>();
            for (MaterialFieldDefinition child : field.getChildren()) {
                known.add(child.getKey());
                Object childRaw = row.get(child.getKey());
                if (empty(childRaw)) {
                    if (Boolean.TRUE.equals(child.getRequired())) {
                        throw fieldError(child, "不能为空");
                    }
                    continue;
                }
                String childPath = path + "." + child.getKey();
                NormalizedValue childValue = normalizeField(child, childRaw, userId, trustedBusinessFileIds,
                        trustedDictionarySnapshots, rowIndex, childPath, true);
                normalizedRow.put(child.getKey(), childValue.value());
                if (childValue.snapshot() != null) {
                    snapshotRow.put(child.getKey(), childValue.snapshot());
                }
                indexes.addAll(childValue.indexes());
                files.addAll(childValue.files());
                search.addAll(childValue.searchParts());
            }
            if (row.keySet().stream().anyMatch(key -> !known.contains(key))) {
                throw fieldError(field, "重复组包含模板之外的字段");
            }
            normalizedRows.add(normalizedRow);
            snapshotRows.add(snapshotRow);
            rowIndex++;
        }
        return new NormalizedValue(normalizedRows, snapshotRows, indexes, files, List.of(), search);
    }

    private List<DictDataRespDTO> enabledDictionary(String dictType) {
        return dictDataApi.getDictDataList(dictType).stream()
                .filter(option -> CommonStatusEnum.ENABLE.getStatus().equals(option.getStatus()))
                .toList();
    }

    private RuntimeException fieldError(MaterialFieldDefinition field, String detail) {
        return exception(MATERIAL_FIELD_INVALID, field.getLabel() + detail);
    }

    private NormalizedValue value(Object value, Object snapshot, IndexValue index, FileValue file,
                                  DimensionValue dimension, String search) {
        return new NormalizedValue(value, snapshot, index == null ? List.of() : List.of(index),
                file == null ? List.of() : List.of(file), dimension == null ? List.of() : List.of(dimension),
                search == null || search.isBlank() ? List.of() : List.of(search));
    }

    private Collection<?> collection(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return collection;
        }
        return List.of(raw);
    }

    private Long fileId(Object raw) {
        if (raw instanceof Map<?, ?> map && map.get("id") != null) {
            return Long.valueOf(String.valueOf(map.get("id")));
        }
        return Long.valueOf(String.valueOf(raw));
    }

    private Long longValue(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean empty(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> collection && collection.isEmpty();
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1);
    }

    private Map<String, Object> snapshot(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    public record NormalizedMaterial(Map<String, Object> values, Map<String, Object> snapshots,
                                     List<IndexValue> indexes, List<FileValue> files,
                                     List<DimensionValue> dimensions, String searchText) {
    }

    private record NormalizedValue(Object value, Object snapshot, List<IndexValue> indexes,
                                   List<FileValue> files, List<DimensionValue> dimensions,
                                   List<String> searchParts) {
    }

    public record IndexValue(String fieldKey, int groupIndex, String valueCode, String labelSnapshot,
                             String textValue, BigDecimal numberValue, LocalDate dateValue,
                             LocalDateTime datetimeValue) {
        static IndexValue code(String key, int group, String code, String label) {
            return new IndexValue(key, group, code, label, null, null, null, null);
        }
        static IndexValue text(String key, int group, String text) {
            return new IndexValue(key, group, null, null, text, null, null, null);
        }
        static IndexValue number(String key, int group, BigDecimal number) {
            return new IndexValue(key, group, null, null, null, number, null, null);
        }
        static IndexValue date(String key, int group, LocalDate date) {
            return new IndexValue(key, group, null, null, null, null, date, null);
        }
        static IndexValue datetime(String key, int group, LocalDateTime datetime) {
            return new IndexValue(key, group, null, null, null, null, null, datetime);
        }
    }

    public record FileValue(String fieldKey, int groupIndex, Long infraFileId, String url,
                            String originalName, String contentType, Long fileSize,
                            Long uploadedByUserId, Map<String, Object> snapshot) {
    }

    public record DimensionValue(String dimensionKey, String value, String label, boolean unlimited) {
    }

    public record DictionarySnapshotValue(String dictType, String code, String label) {
    }
}
