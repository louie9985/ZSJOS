package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmUserTaskMetadataRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialTypeMapper;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialFieldDefinition;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialSchemaService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialTypeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.BPM_CATEGORY;
import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.MATERIAL_TYPE_PRODUCTION_CONTENT;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_REVIEW_CONFIG_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_REVIEW_VERSION_CONFLICT;

@Service
public class ContentReviewConfigService {

    public static final String PROCESS_DEFINITION_KEY = "zsjos_production_content_review";
    public static final String DIRECTOR_TASK_KEY = "directorReview";
    public static final String FINAL_TASK_KEY = "finalReview";
    /** SIMPLE 设计器为发起人生成的提交节点编码，不是业务审批节点。 */
    private static final String SIMPLE_SUBMISSION_TASK_KEY = "StartUserNode";
    public static final String PRODUCTION_MATERIAL_TYPE_CODE = MATERIAL_TYPE_PRODUCTION_CONTENT;

    /**
     * 内置生产内容模板字段到内容/账号快照来源的固定映射。键为模板字段编码，值为
     * {@link ContentReviewMaterialService} 支持的来源名；租户删改模板字段时按已发布模板过滤。
     */
    private static final Map<String, String> DEFAULT_FIELD_MAPPING = Map.ofEntries(
            Map.entry("__cover__", "coverFileId"),
            Map.entry("content_title", "title"),
            Map.entry("content_topic", "topic"),
            Map.entry("script_text", "scriptText"),
            Map.entry("deliverable_url", "deliverableUrl"),
            Map.entry("lead_resource_url", "leadResourceUrl"),
            Map.entry("planned_publish_at", "plannedPublishAt"),
            Map.entry("deliverable_files", "deliverableFileIds"),
            Map.entry("account_type", "accountType"),
            Map.entry("profession", "profession"),
            Map.entry("account_stage", "accountStage"));

    private static final Set<String> TEXT_SOURCES = Set.of("title", "topic", "scriptText", "deliverableUrl",
            "leadResourceUrl");
    private static final Set<String> LINK_SOURCES = Set.of("deliverableUrl", "leadResourceUrl");
    private static final Set<String> IMAGE_SOURCES = Set.of("coverFileIds", "deliverableFileIds");
    private static final Set<String> ATTACHMENT_SOURCES = Set.of("coverFileIds", "deliverableFileIds");
    private static final Map<String, String> DICTIONARY_SOURCE_TYPES = Map.of(
            "accountType", DICT_PERSONA_TYPE,
            "profession", DICT_PROFESSION,
            "accountStage", DICT_ACCOUNT_STAGE);

    @Resource private ContentReviewConfigMapper contentReviewConfigMapper;
    @Resource private BpmDefinitionReadApi definitionReadApi;
    @Resource private MaterialTypeMapper materialTypeMapper;
    @Resource private MaterialTypeService materialTypeService;
    @Resource private MaterialSchemaService materialSchemaService;

    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaultConfig() {
        ContentReviewConfigDO existing = contentReviewConfigMapper.selectCurrent();
        if (existing != null) {
            repairEmptyMapping(existing);
            return;
        }
        ContentReviewConfigDO config = new ContentReviewConfigDO();
        config.setProductionMaterialTypeCode(MATERIAL_TYPE_PRODUCTION_CONTENT);
        config.setMaterialFieldMappingJson(JsonUtils.toJsonString(defaultMapping()));
        config.setMaterialDefaultValuesJson("{}");
        config.setVersion(0);
        try {
            contentReviewConfigMapper.insert(config);
        } catch (DuplicateKeyException ignored) {
            // Another request initialized this tenant first.
        }
    }

    /**
     * V194 与早期启动只写入空映射，收录时每个字段都取不到来源。映射与模板都已是服务端固定契约，
     * 这里补齐仍为空的存量租户；管理员已配置过的映射不覆盖。
     */
    private void repairEmptyMapping(ContentReviewConfigDO config) {
        if (!mapping(config).isEmpty() || !defaults(config).isEmpty()) {
            return;
        }
        Map<String, String> mapping = defaultMapping();
        if (mapping.isEmpty()) {
            return;
        }
        config.setProductionMaterialTypeCode(MATERIAL_TYPE_PRODUCTION_CONTENT);
        config.setMaterialFieldMappingJson(JsonUtils.toJsonString(mapping));
        config.setMaterialDefaultValuesJson("{}");
        contentReviewConfigMapper.updateConfig(config, config.getVersion());
    }

    /**
     * 默认映射按当前已发布模板过滤，租户自行删改过模板字段时只保留仍存在且类型匹配的来源，
     * 避免固定映射反过来把 requireReadyConfig 卡死。
     */
    private Map<String, String> defaultMapping() {
        MaterialTypeDO type = materialTypeMapper.selectByCode(PRODUCTION_MATERIAL_TYPE_CODE);
        if (type == null || type.getCurrentSchemaVersionId() == null) {
            return Map.of();
        }
        List<MaterialFieldDefinition> fields;
        try {
            fields = materialSchemaService.parseFields(
                    materialTypeService.requirePublishedSchema(type).getFieldsJson());
        } catch (RuntimeException ignored) {
            return Map.of();
        }
        Map<String, MaterialFieldDefinition> byKey = new LinkedHashMap<>();
        fields.forEach(field -> byKey.put(field.getKey(), field));
        Map<String, String> mapping = new LinkedHashMap<>();
        DEFAULT_FIELD_MAPPING.forEach((key, source) -> {
            if ("__cover__".equals(key) || supportsSource(byKey.get(key), source)) {
                mapping.put(key, source);
            }
        });
        return mapping;
    }

    public ContentReviewConfigDO requireReadyConfig(Long userId) {
        ContentReviewConfigDO config = contentReviewConfigMapper.selectCurrent();
        if (config == null) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                PROCESS_DEFINITION_KEY);
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())
                || !BPM_CATEGORY.equals(definition.getCategory())
                || !validTaskSequence(definition)) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        validateMaterialMapping(PRODUCTION_MATERIAL_TYPE_CODE, mapping(config), defaults(config), userId);
        return config;
    }

    public BpmProcessDefinitionMetadataRespDTO requireCurrentDefinition(ContentReviewConfigDO config) {
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                PROCESS_DEFINITION_KEY);
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())
                || !BPM_CATEGORY.equals(definition.getCategory())
                || !validTaskSequence(definition)) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        return definition;
    }

    public MaterialSchemaVersionDO requireProductionMaterialSchema(ContentReviewConfigDO config) {
        MaterialTypeDO type = materialTypeMapper.selectByCode(PRODUCTION_MATERIAL_TYPE_CODE);
        if (type == null || !CommonStatusEnum.ENABLE.getStatus().equals(type.getStatus())
                || !Boolean.TRUE.equals(type.getAllowAutoCollect())) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        return materialTypeService.requirePublishedSchema(type);
    }

    /**
     * 校验流程仍是"编导 → 终审"两级单人骨架。批审的逐条结论只有一组编导列和一组终审列
     * （见 zsjos_content_review_batch_item），因此业务审批节点必须恰好两个、都是单人执行、
     * 且按编导 → 终审 → 结束串联（节点编码由已发布定义提供）；多出的节点或多人审批方式都无处落库。
     *
     * <p>SIMPLE 设计器编译时必然额外生成发起人提交节点，它不是业务审批节点，这里按保留
     * 节点编码排除后再计数。不使用 definition.simpleSequentialApproval：那个标志按全部
     * userTask 计数，对任何 SIMPLE 资产都为 false；串联性由 nextUserTaskKeys 自行校验。
     */
    private boolean validTaskSequence(BpmProcessDefinitionMetadataRespDTO definition) {
        return findReviewTaskKeys(definition) != null;
    }

    public record ReviewTaskKeys(String director, String finalReview) { }

    public ReviewTaskKeys requireReviewTaskKeys(BpmProcessDefinitionMetadataRespDTO definition) {
        ReviewTaskKeys keys = findReviewTaskKeys(definition);
        if (keys == null) throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        return keys;
    }

    private ReviewTaskKeys findReviewTaskKeys(BpmProcessDefinitionMetadataRespDTO definition) {
        if (definition == null || definition.getUserTasks() == null) return null;
        var tasks = definition.getUserTasks().stream()
                .filter(task -> !SIMPLE_SUBMISSION_TASK_KEY.equals(task.getKey())).toList();
        if (tasks.size() != 2 || tasks.stream().anyMatch(task -> task.getKey() == null
                || task.getKey().isBlank() || !Boolean.TRUE.equals(task.getRejectEndsProcess())
                || !"SINGLE".equals(task.getExecutionMode()))) return null;
        // Designer node IDs are opaque. The approved two-stage sequence owns the business meaning.
        for (var director : tasks) {
            var last = tasks.getFirst() == director ? tasks.getLast() : tasks.getFirst();
            if (!Objects.equals(director.getKey(), last.getKey())
                    && Objects.equals(director.getNextUserTaskKeys(), List.of(last.getKey()))
                    && (last.getNextUserTaskKeys() == null || last.getNextUserTaskKeys().isEmpty())) {
                var starts = definition.getUserTasks().stream()
                        .filter(task -> SIMPLE_SUBMISSION_TASK_KEY.equals(task.getKey())).toList();
                if (starts.size() > 1 || !starts.isEmpty()
                        && !Objects.equals(starts.getFirst().getNextUserTaskKeys(), List.of(director.getKey()))) return null;
                return new ReviewTaskKeys(director.getKey(), last.getKey());
            }
        }
        return null;
    }

    public Map<String, String> mapping(ContentReviewConfigDO config) {
        Map<String, Object> raw = JsonUtils.parseMap(config.getMaterialFieldMappingJson());
        if (raw == null) return Map.of();
        return raw.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                item -> String.valueOf(item.getValue()), (left, right) -> right, java.util.LinkedHashMap::new));
    }

    public Map<String, Object> defaults(ContentReviewConfigDO config) {
        Map<String, Object> result = JsonUtils.parseMap(config.getMaterialDefaultValuesJson());
        return result == null ? Map.of() : result;
    }

    private void validateMaterialMapping(String typeCode, Map<String, String> mapping,
                                         Map<String, Object> defaults, Long userId) {
        MaterialTypeDO type = materialTypeMapper.selectByCode(typeCode);
        if (type == null || !CommonStatusEnum.ENABLE.getStatus().equals(type.getStatus())
                || !Boolean.TRUE.equals(type.getAllowAutoCollect())) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        MaterialSchemaVersionDO schema = materialTypeService.requirePublishedSchema(type);
        List<MaterialFieldDefinition> fields = materialSchemaService.parseFields(schema.getFieldsJson());
        Map<String, MaterialFieldDefinition> fieldsByKey = new LinkedHashMap<>();
        for (MaterialFieldDefinition field : fields) {
            fieldsByKey.put(field.getKey(), field);
        }
        if (mapping.containsKey("__cover__") && !"coverFileId".equals(mapping.get("__cover__"))
                || defaults.containsKey("__cover__")
                || !java.util.Collections.disjoint(mapping.keySet(), defaults.keySet())
                || mapping.keySet().stream().anyMatch(key -> !"__cover__".equals(key) && !fieldsByKey.containsKey(key))
                || defaults.keySet().stream().anyMatch(key -> !fieldsByKey.containsKey(key))) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if ("__cover__".equals(entry.getKey())) continue;
            if (!supportsSource(fieldsByKey.get(entry.getKey()), entry.getValue())) {
                throw exception(CONTENT_REVIEW_CONFIG_INVALID);
            }
        }
        for (String key : defaults.keySet()) {
            if (FILE_FIELD_TYPES.contains(fieldsByKey.get(key).getType())) {
                throw exception(CONTENT_REVIEW_CONFIG_INVALID);
            }
        }
        for (MaterialFieldDefinition field : fields) {
            if (Boolean.TRUE.equals(field.getRequired()) && !mapping.containsKey(field.getKey())
                    && empty(defaults.get(field.getKey()))) {
                throw exception(CONTENT_REVIEW_CONFIG_INVALID);
            }
        }
        try {
            materialSchemaService.validatePartialValues(fields, defaults, userId);
        } catch (RuntimeException error) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
    }

    private boolean supportsSource(MaterialFieldDefinition field, String source) {
        if (field == null || source == null || source.isBlank()) return false;
        return switch (field.getType()) {
            case FIELD_TEXT, FIELD_TEXTAREA, FIELD_RICH_TEXT -> TEXT_SOURCES.contains(source);
            case FIELD_HTTPS_LINK -> LINK_SOURCES.contains(source);
            case FIELD_DATETIME -> "plannedPublishAt".equals(source);
            case FIELD_DICT_SINGLE, FIELD_DICT_MULTI -> Objects.equals(
                    field.getDictType(), DICTIONARY_SOURCE_TYPES.get(source));
            case FIELD_IMAGE -> IMAGE_SOURCES.contains(source);
            case FIELD_VIDEO -> "deliverableFileIds".equals(source);
            case FIELD_ATTACHMENT -> ATTACHMENT_SOURCES.contains(source);
            default -> false;
        };
    }

    private boolean empty(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof java.util.Collection<?> collection && collection.isEmpty();
    }


    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}



