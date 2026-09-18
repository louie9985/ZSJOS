package cn.iocoder.yudao.module.zsjos.service.material;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSchemaSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialProcessDefinitionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTemplatePublishReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTemplateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTypeRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialTypeSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialSchemaVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialTypeMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class MaterialTypeServiceImpl implements MaterialTypeService {

    private static final Map<String, DefaultType> DEFAULT_TYPES = Map.of(
            "viral_account", new DefaultType("爆款账号", "爆款账号拆解内容包", true, true, false, true),
            "viral_content", new DefaultType("爆款内容", "爆款内容拆解内容包", true, true, false, true),
            "sop", new DefaultType("SOP", "可检索、可引用的标准作业参考文档", true, true, false, false),
            "production_content", new DefaultType("生产内容", "审核通过并明确收录的生产内容", false, false, true, true));

    /** 需要内置默认模板的类型；其余类型由管理员自行建模。 */
    private static final Set<String> DEFAULT_SCHEMA_TYPES = Set.of(
            "viral_account", "viral_content", "production_content");

    @Resource private MaterialTypeMapper typeMapper;
    @Resource private MaterialSchemaVersionMapper schemaMapper;
    @Resource private MaterialSchemaService schemaService;
    @Resource private BpmDefinitionReadApi definitionReadApi;

    @Override
    public List<MaterialTypeRespVO> getTypeList() {
        ensureDefaultTypes();
        return typeMapper.selectAll().stream().map(this::toTypeResp).toList();
    }

    @Override
    public List<MaterialProcessDefinitionRespVO> getPublishedProcessDefinitions() {
        return definitionReadApi.getPublishedProcessDefinitions(MATERIAL_BPM_CATEGORY).stream()
                .map(definition -> BeanUtils.toBean(definition, MaterialProcessDefinitionRespVO.class))
                .toList();
    }

    @Override
    public MaterialTypeRespVO getType(Long id) {
        return toTypeResp(requireType(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createType(MaterialTypeSaveReqVO request) {
        if (typeMapper.selectByCode(request.getCode()) != null) {
            throw exception(MATERIAL_TYPE_CODE_DUPLICATE);
        }
        normalizeProcessBinding(request);
        MaterialTypeDO type = BeanUtils.toBean(request, MaterialTypeDO.class);
        type.setRecommendationConfigJson(JsonUtils.toJsonString(normalizeRecommendationConfig(
                request.getRecommendationConfig(), request.getRecommendationEnabled())));
        type.setVersion(0);
        try {
            typeMapper.insert(type);
        } catch (DuplicateKeyException error) {
            throw exception(MATERIAL_TYPE_CODE_DUPLICATE);
        }
        return type.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateType(Long id, MaterialTypeSaveReqVO request) {
        MaterialTypeDO existing = typeMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (existing == null) {
            throw exception(MATERIAL_TYPE_NOT_EXISTS);
        }
        if (!Objects.equals(existing.getCode(), request.getCode())) {
            throw exception(MATERIAL_SCHEMA_INVALID, "类型编码创建后不可修改");
        }
        if (!Objects.equals(existing.getVersion(), request.getVersion())) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        normalizeProcessBinding(request);
        MaterialTypeDO update = BeanUtils.toBean(request, MaterialTypeDO.class);
        update.setId(id);
        update.setRecommendationConfigJson(JsonUtils.toJsonString(normalizeRecommendationConfig(
                request.getRecommendationConfig(), request.getRecommendationEnabled())));
        if (typeMapper.updateType(update, request.getVersion()) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
    }

    @Override
    public List<MaterialTemplateRespVO> getSchemaVersions(Long materialTypeId) {
        requireType(materialTypeId);
        return schemaMapper.selectListByTypeId(materialTypeId).stream().map(this::toSchemaResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSchemaDraft(Long materialTypeId, MaterialSchemaSaveReqVO request) {
        MaterialTypeDO type = typeMapper.selectByIdForUpdate(materialTypeId,
                TenantContextHolder.getRequiredTenantId());
        if (type == null) {
            throw exception(MATERIAL_TYPE_NOT_EXISTS);
        }
        schemaService.validateSchema(request.getFields(), false);
        String fieldsJson = JsonUtils.toJsonString(request.getFields());
        String hash = DigestUtil.sha256Hex(fieldsJson);
        MaterialSchemaVersionDO latest = schemaMapper.selectLatestByTypeIdForUpdate(materialTypeId,
                TenantContextHolder.getRequiredTenantId());
        if (latest != null && SCHEMA_DRAFT.equals(latest.getStatus())) {
            if (request.getId() == null || request.getVersion() == null
                    || !Objects.equals(latest.getId(), request.getId())) {
                throw exception(MATERIAL_SCHEMA_VERSION_CONFLICT);
            }
            if (schemaMapper.updateDraft(latest.getId(), request.getVersion(), fieldsJson, hash) != 1) {
                throw exception(MATERIAL_SCHEMA_VERSION_CONFLICT);
            }
            return latest.getId();
        }
        if (request.getId() != null || request.getVersion() != null) {
            throw exception(MATERIAL_SCHEMA_VERSION_CONFLICT);
        }
        MaterialSchemaVersionDO draft = new MaterialSchemaVersionDO();
        draft.setMaterialTypeId(materialTypeId);
        draft.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        draft.setStatus(SCHEMA_DRAFT);
        draft.setFieldsJson(fieldsJson);
        draft.setSchemaHash(hash);
        draft.setVersion(0);
        schemaMapper.insert(draft);
        return draft.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishSchema(Long materialTypeId, MaterialTemplatePublishReqVO request, Long userId) {
        MaterialTypeDO type = typeMapper.selectByIdForUpdate(materialTypeId,
                TenantContextHolder.getRequiredTenantId());
        if (type == null) {
            throw exception(MATERIAL_TYPE_NOT_EXISTS);
        }
        if (!Objects.equals(type.getVersion(), request.getExpectedTypeVersion())) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        MaterialSchemaVersionDO schema = schemaMapper.selectByIdForUpdate(request.getSchemaVersionId(),
                TenantContextHolder.getRequiredTenantId());
        if (schema == null || !Objects.equals(schema.getMaterialTypeId(), materialTypeId)) {
            throw exception(MATERIAL_SCHEMA_NOT_EXISTS);
        }
        if (!SCHEMA_DRAFT.equals(schema.getStatus())
                || !Objects.equals(schema.getVersion(), request.getExpectedSchemaVersion())) {
            throw exception(MATERIAL_SCHEMA_VERSION_CONFLICT);
        }
        schemaService.validateSchemaForPublish(schemaService.parseFields(schema.getFieldsJson()));
        LocalDateTime now = LocalDateTime.now();
        int changed = schemaMapper.update(null, new LambdaUpdateWrapper<MaterialSchemaVersionDO>()
                .eq(MaterialSchemaVersionDO::getId, schema.getId())
                .eq(MaterialSchemaVersionDO::getStatus, SCHEMA_DRAFT)
                .eq(MaterialSchemaVersionDO::getVersion, request.getExpectedSchemaVersion())
                .set(MaterialSchemaVersionDO::getStatus, SCHEMA_PUBLISHED)
                .set(MaterialSchemaVersionDO::getPublishedByUserId, userId)
                .set(MaterialSchemaVersionDO::getPublishedAt, now)
                .set(MaterialSchemaVersionDO::getVersion, request.getExpectedSchemaVersion() + 1));
        if (changed != 1) {
            throw exception(MATERIAL_SCHEMA_VERSION_CONFLICT);
        }
        if (type.getCurrentSchemaVersionId() != null) {
            schemaMapper.update(null, new LambdaUpdateWrapper<MaterialSchemaVersionDO>()
                    .eq(MaterialSchemaVersionDO::getId, type.getCurrentSchemaVersionId())
                    .eq(MaterialSchemaVersionDO::getStatus, SCHEMA_PUBLISHED)
                    .set(MaterialSchemaVersionDO::getStatus, SCHEMA_ARCHIVED)
                    .setSql("version = version + 1"));
        }
        if (typeMapper.publishSchema(type.getId(), type.getVersion(), schema.getId()) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
    }

    @Override
    public MaterialTypeDO requireType(Long id) {
        MaterialTypeDO type = typeMapper.selectById(id);
        if (type == null) {
            throw exception(MATERIAL_TYPE_NOT_EXISTS);
        }
        return type;
    }

    @Override
    public MaterialSchemaVersionDO requirePublishedSchema(MaterialTypeDO type) {
        if (type.getCurrentSchemaVersionId() == null) {
            throw exception(MATERIAL_SCHEMA_NOT_PUBLISHED);
        }
        MaterialSchemaVersionDO schema = schemaMapper.selectById(type.getCurrentSchemaVersionId());
        if (schema == null || !Objects.equals(schema.getMaterialTypeId(), type.getId())
                || !List.of(SCHEMA_PUBLISHED, SCHEMA_ARCHIVED).contains(schema.getStatus())) {
            throw exception(MATERIAL_SCHEMA_NOT_PUBLISHED);
        }
        return schema;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaultTypes() {
        for (Map.Entry<String, DefaultType> entry : DEFAULT_TYPES.entrySet()) {
            MaterialTypeDO existing = typeMapper.selectByCode(entry.getKey());
            if (existing != null) {
                ensureDefaultSchema(existing);
                continue;
            }
            DefaultType value = entry.getValue();
            MaterialTypeDO type = new MaterialTypeDO();
            type.setCode(entry.getKey());
            type.setName(value.name());
            type.setDescription(value.description());
            type.setStatus(CommonStatusEnum.ENABLE.getStatus());
            type.setAllowManualCreate(value.manual());
            type.setAllowImport(value.allowImport());
            type.setAllowAutoCollect(value.autoCollect());
            type.setRecommendationEnabled(value.recommend());
            type.setRecommendationConfigJson(JsonUtils.toJsonString(defaultRecommendationConfig()));
            type.setVersion(0);
            try {
                typeMapper.insert(type);
                ensureDefaultSchema(type);
            } catch (DuplicateKeyException ignored) {
                // Another request initialized the same tenant defaults first.
            }
        }
    }

    private void ensureDefaultSchema(MaterialTypeDO type) {
        if (!DEFAULT_SCHEMA_TYPES.contains(type.getCode())) {
            return;
        }
        if (type.getCurrentSchemaVersionId() != null
                && schemaMapper.selectById(type.getCurrentSchemaVersionId()) != null) {
            return;
        }
        // A non-null pointer can outlive its template after an incomplete data restore.
        // Recover published definitions only: opening a page must never publish an administrator's draft.
        List<MaterialSchemaVersionDO> versions = schemaMapper.selectListByTypeId(type.getId());
        MaterialSchemaVersionDO existing = versions.stream()
                .filter(schema -> SCHEMA_PUBLISHED.equals(schema.getStatus()))
                .findFirst().orElse(null);
        if (existing != null) {
            if (typeMapper.publishSchema(type.getId(), type.getVersion(), existing.getId()) == 1) {
                type.setCurrentSchemaVersionId(existing.getId());
            }
            return;
        }
        if (!versions.isEmpty()) {
            return;
        }
        List<MaterialFieldDefinition> fields = defaultSchemaFields(type.getCode());
        String fieldsJson = JsonUtils.toJsonString(fields);
        MaterialSchemaVersionDO schema = new MaterialSchemaVersionDO();
        schema.setMaterialTypeId(type.getId());
        schema.setVersionNo(1);
        schema.setStatus(SCHEMA_PUBLISHED);
        schema.setFieldsJson(fieldsJson);
        schema.setSchemaHash(DigestUtil.sha256Hex(fieldsJson));
        schema.setPublishedAt(LocalDateTime.now());
        schema.setVersion(0);
        schemaMapper.insert(schema);
        // Only mark the type linked when the guarded update actually matched; a lost race is repaired above.
        if (typeMapper.publishSchema(type.getId(), type.getVersion(), schema.getId()) == 1) {
            type.setCurrentSchemaVersionId(schema.getId());
        }
    }

    private List<MaterialFieldDefinition> defaultSchemaFields(String code) {
        return switch (code) {
            case "viral_content" -> ViralContentMaterialSchema.fields();
            case "production_content" -> ProductionContentMaterialSchema.fields();
            default -> ViralAccountMaterialSchema.fields();
        };
    }

    private void normalizeProcessBinding(MaterialTypeSaveReqVO request) {
        if (MaterialApprovalContract.isViral(request.getCode())) {
            // Ignore stale clients' binding: viral approvals always use their stable BPM key.
            request.setBpmProcessDefinitionKey(null);
            return;
        }
        validateProcessDefinition(request.getBpmProcessDefinitionKey());
    }

    private void validateProcessDefinition(String processDefinitionKey) {
        if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
            return;
        }
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                processDefinitionKey.trim());
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())
                || definition.getCategory() != null && !MATERIAL_BPM_CATEGORY.equals(definition.getCategory())) {
            throw exception(MATERIAL_BPM_UNAVAILABLE);
        }
    }

    private Map<String, Object> normalizeRecommendationConfig(Map<String, Object> raw, Boolean enabled) {
        Map<String, Object> source = raw == null ? defaultRecommendationConfig() : raw;
        if (source.keySet().stream().anyMatch(key -> !Set.of("dimensions", "maxResults").contains(key))) {
            throw exception(MATERIAL_SCHEMA_INVALID, "推荐配置包含不支持的参数");
        }
        Object rawDimensions = source.get("dimensions");
        if (!(rawDimensions instanceof List<?> values)) {
            throw exception(MATERIAL_SCHEMA_INVALID, "推荐维度必须是数组");
        }
        LinkedHashSet<String> dimensions = new LinkedHashSet<>();
        for (Object value : values) {
            String dimension = String.valueOf(value);
            if (!RECOMMENDATION_DIMENSIONS.contains(dimension)) {
                throw exception(MATERIAL_SCHEMA_INVALID, "推荐维度无效：" + dimension);
            }
            dimensions.add(dimension);
        }
        if (Boolean.TRUE.equals(enabled) && dimensions.isEmpty()) {
            throw exception(MATERIAL_SCHEMA_INVALID, "启用推荐时至少选择一个推荐维度");
        }
        int maxResults = source.get("maxResults") instanceof Number number ? number.intValue() : 20;
        if (maxResults < 1 || maxResults > 100) {
            throw exception(MATERIAL_SCHEMA_INVALID, "推荐数量必须在1到100之间");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("dimensions", new ArrayList<>(dimensions));
        normalized.put("maxResults", maxResults);
        return normalized;
    }

    private Map<String, Object> defaultRecommendationConfig() {
        return Map.of("dimensions", List.of(DIMENSION_ACCOUNT_TYPE, DIMENSION_PROFESSION,
                DIMENSION_ACCOUNT_STAGE), "maxResults", 20);
    }

    private MaterialTypeRespVO toTypeResp(MaterialTypeDO type) {
        MaterialTypeRespVO response = BeanUtils.toBean(type, MaterialTypeRespVO.class);
        if (MaterialApprovalContract.isViral(type.getCode())) {
            response.setBpmProcessDefinitionKey(null);
        }
        response.setRecommendationConfig(JsonUtils.parseMap(type.getRecommendationConfigJson()));
        if (type.getCurrentSchemaVersionId() != null) {
            MaterialSchemaVersionDO schema = schemaMapper.selectById(type.getCurrentSchemaVersionId());
            response.setCurrentSchema(schema == null ? null : toSchemaResp(schema));
        }
        return response;
    }

    private MaterialTemplateRespVO toSchemaResp(MaterialSchemaVersionDO schema) {
        MaterialTemplateRespVO response = BeanUtils.toBean(schema, MaterialTemplateRespVO.class);
        response.setFields(schemaService.parseFields(schema.getFieldsJson()));
        return response;
    }

    private record DefaultType(String name, String description, boolean manual, boolean allowImport,
                               boolean autoCollect, boolean recommend) {
    }
}
