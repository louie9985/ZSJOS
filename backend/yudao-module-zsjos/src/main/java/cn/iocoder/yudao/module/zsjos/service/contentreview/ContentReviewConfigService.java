package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewProcessDefinitionRespVO;
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
        if (contentReviewConfigMapper.selectCurrent() != null) {
            return;
        }
        ContentReviewConfigDO config = new ContentReviewConfigDO();
        config.setProductionMaterialTypeCode(MATERIAL_TYPE_PRODUCTION_CONTENT);
        config.setMaterialFieldMappingJson("{}");
        config.setMaterialDefaultValuesJson("{}");
        config.setVersion(0);
        try {
            contentReviewConfigMapper.insert(config);
        } catch (DuplicateKeyException ignored) {
            // Another request initialized this tenant first.
        }
    }

    public ContentReviewConfigRespVO getConfig() {
        ContentReviewConfigDO config = contentReviewConfigMapper.selectCurrent();
        if (config == null) throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        return toResponse(config);
    }

    public List<ContentReviewProcessDefinitionRespVO> getProcessDefinitions() {
        return definitionReadApi.getPublishedProcessDefinitions(BPM_CATEGORY).stream()
                .map(item -> {
                    ContentReviewProcessDefinitionRespVO response = BeanUtils.toBean(
                            item, ContentReviewProcessDefinitionRespVO.class);
                    response.setUserTasks(item.getUserTasks() == null ? List.of() : item.getUserTasks().stream()
                            .map(task -> BeanUtils.toBean(task,
                                    ContentReviewProcessDefinitionRespVO.UserTask.class)).toList());
                    return response;
                }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(ContentReviewConfigSaveReqVO request, Long userId) {
        ContentReviewConfigDO config = contentReviewConfigMapper.selectCurrentForUpdate(
                TenantContextHolder.getRequiredTenantId());
        if (config == null || !Objects.equals(config.getVersion(), request.getVersion())) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
        String processDefinitionKey = request.getProcessDefinitionKey().trim();
        String directorTaskKey = request.getDirectorTaskKey().trim();
        String finalTaskKey = request.getFinalTaskKey().trim();
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                processDefinitionKey);
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())
                || !BPM_CATEGORY.equals(definition.getCategory())
                || directorTaskKey.equals(finalTaskKey) || !validTaskSequence(definition, directorTaskKey, finalTaskKey)) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        validateMaterialMapping(request.getProductionMaterialTypeCode(), request.getMaterialFieldMapping(),
                request.getMaterialDefaultValues(), userId);
        config.setProcessDefinitionKey(processDefinitionKey);
        config.setDirectorTaskKey(directorTaskKey);
        config.setFinalTaskKey(finalTaskKey);
        config.setProductionMaterialTypeCode(request.getProductionMaterialTypeCode().trim());
        config.setMaterialFieldMappingJson(JsonUtils.toJsonString(request.getMaterialFieldMapping()));
        config.setMaterialDefaultValuesJson(JsonUtils.toJsonString(request.getMaterialDefaultValues()));
        if (contentReviewConfigMapper.updateConfig(config, request.getVersion()) != 1) {
            throw exception(CONTENT_REVIEW_VERSION_CONFLICT);
        }
    }

    public ContentReviewConfigDO requireReadyConfig(Long userId) {
        ContentReviewConfigDO config = contentReviewConfigMapper.selectCurrent();
        if (config == null || blank(config.getProcessDefinitionKey()) || blank(config.getDirectorTaskKey())
                || blank(config.getFinalTaskKey()) || blank(config.getProductionMaterialTypeCode())) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                config.getProcessDefinitionKey());
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())
                || !BPM_CATEGORY.equals(definition.getCategory())
                || !validTaskSequence(definition, config.getDirectorTaskKey(), config.getFinalTaskKey())) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        validateMaterialMapping(config.getProductionMaterialTypeCode(), mapping(config), defaults(config), userId);
        return config;
    }

    public BpmProcessDefinitionMetadataRespDTO requireCurrentDefinition(ContentReviewConfigDO config) {
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                config.getProcessDefinitionKey());
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())
                || !BPM_CATEGORY.equals(definition.getCategory())
                || !validTaskSequence(definition, config.getDirectorTaskKey(), config.getFinalTaskKey())) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        return definition;
    }

    public MaterialSchemaVersionDO requireProductionMaterialSchema(ContentReviewConfigDO config) {
        MaterialTypeDO type = materialTypeMapper.selectByCode(config.getProductionMaterialTypeCode());
        if (type == null || !CommonStatusEnum.ENABLE.getStatus().equals(type.getStatus())
                || !Boolean.TRUE.equals(type.getAllowAutoCollect())) {
            throw exception(CONTENT_REVIEW_CONFIG_INVALID);
        }
        return materialTypeService.requirePublishedSchema(type);
    }

    private boolean validTaskSequence(BpmProcessDefinitionMetadataRespDTO definition,
                                      String directorTaskKey, String finalTaskKey) {
        if (!Boolean.TRUE.equals(definition.getSimpleSequentialApproval())
                || definition.getUserTasks() == null || definition.getUserTasks().size() != 2) return false;
        var directorTask = definition.getUserTasks().stream()
                .filter(task -> directorTaskKey.equals(task.getKey())).findFirst().orElse(null);
        var finalTask = definition.getUserTasks().stream()
                .filter(task -> finalTaskKey.equals(task.getKey())).findFirst().orElse(null);
        return directorTask != null && finalTask != null
                && "SINGLE".equals(directorTask.getExecutionMode())
                && "SINGLE".equals(finalTask.getExecutionMode())
                && Objects.equals(directorTask.getNextUserTaskKeys(), List.of(finalTaskKey))
                && (finalTask.getNextUserTaskKeys() == null || finalTask.getNextUserTaskKeys().isEmpty());
    }

    @SuppressWarnings("unchecked")
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

    private ContentReviewConfigRespVO toResponse(ContentReviewConfigDO config) {
        ContentReviewConfigRespVO response = BeanUtils.toBean(config, ContentReviewConfigRespVO.class);
        response.setMaterialFieldMapping(mapping(config));
        response.setMaterialDefaultValues(defaults(config));
        return response;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
