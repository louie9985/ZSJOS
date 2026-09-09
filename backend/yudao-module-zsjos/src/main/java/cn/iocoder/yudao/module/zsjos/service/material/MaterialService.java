package cn.iocoder.yudao.module.zsjos.service.material;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialFileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialRecommendationAccountRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceTargetPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceTargetRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialVersionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialApprovalRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFileDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialSchemaVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialTypeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFavoriteMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialLikeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialSchemaVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialTypeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.file.BusinessFileDirectUploadService;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class MaterialService {

    private static final String MATERIAL_BPM_CATEGORY = "zsjos_material";
    private static final int FILE_PREVIEW_SECONDS = 600;

    @Resource
    private MaterialMapper materialMapper;
    @Resource
    private MaterialVersionMapper versionMapper;
    @Resource
    private MaterialTypeMapper typeMapper;
    @Resource
    private MaterialSchemaVersionMapper schemaMapper;
    @Resource
    private MaterialFileMapper materialFileMapper;
    @Resource
    private MaterialLikeMapper likeMapper;
    @Resource
    private MaterialFavoriteMapper favoriteMapper;
    @Resource
    private MaterialApprovalRoundMapper approvalRoundMapper;
    @Resource
    private MediaAccountMapper mediaAccountMapper;
    @Resource
    private ContentMapper contentMapper;
    @Resource
    private ContentVersionMapper contentVersionMapper;
    @Resource
    private MaterialTypeService materialTypeService;
    @Resource
    private MaterialSchemaService schemaService;
    @Resource
    private MaterialProjectionService projectionService;
    @Resource
    private BpmDefinitionReadApi definitionReadApi;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private FileApi fileApi;
    @Resource
    private BusinessFileDirectUploadService directUploadService;
    @Resource
    private MediaAccountObjectPermissionProvider mediaAccountPermissionProvider;

    public PageResult<MaterialRespVO> getPage(MaterialPageReqVO request, Long userId) {
        boolean canManage = permissionApi.hasAnyPermissions(userId, "zsjos:material:manage");
        PageResult<MaterialDO> page = Boolean.TRUE.equals(request.getRecommendation())
                ? getRecommendationPage(request, userId)
                : materialMapper.selectPage(request, userId, canManage);
        if (page.getList().isEmpty()) {
            return PageResult.empty(page.getTotal());
        }
        return new PageResult<>(toResponses(page.getList(), userId, canManage), page.getTotal());
    }

    public List<MaterialRecommendationAccountRespVO> getRecommendationAccountCandidates(String keyword,
                                                                                         Long userId) {
        boolean all = permissionApi.hasAnyPermissions(userId, "zsjos:media-account:query-all");
        return mediaAccountMapper.selectMaterialRecommendationCandidates(trimToNull(keyword), userId, tenantId(), all)
                .stream().map(account -> BeanUtils.toBean(account, MaterialRecommendationAccountRespVO.class))
                .toList();
    }

    public PageResult<MaterialReferenceTargetRespVO> getReferenceTargetCandidates(
            MaterialReferenceTargetPageReqVO request, Long userId) {
        boolean all = permissionApi.hasAnyPermissions(userId, "zsjos:content:query-all");
        return contentMapper.selectMaterialReferenceTargetPage(request, userId, tenantId(), all);
    }

    private PageResult<MaterialDO> getRecommendationPage(MaterialPageReqVO request, Long userId) {
        if (request.getAccountId() == null) {
            throw exception(MATERIAL_RECOMMENDATION_ACCOUNT_REQUIRED);
        }
        mediaAccountPermissionProvider.check(request.getAccountId(), "read", userId);
        MediaAccountDO account = mediaAccountMapper.selectById(request.getAccountId());
        if (account == null) {
            throw exception(MEDIA_ACCOUNT_NOT_EXISTS);
        }
        request.setPageSize(Math.min(request.getPageSize(), recommendationMaxResults(request.getMaterialTypeId())));
        return materialMapper.selectRecommendationPage(request, userId, tenantId(),
                trimToNull(account.getAccountTypePrimaryValue()),
                trimToNull(account.getAccountTypeSecondaryValue()),
                trimToNull(account.getTrackPrimaryValue()),
                trimToNull(account.getTrackSecondaryValue()),
                trimToNull(account.getSStage()));
    }

    private int recommendationMaxResults(Long materialTypeId) {
        if (materialTypeId == null) return 20;
        MaterialTypeDO type = typeMapper.selectById(materialTypeId);
        if (type == null || type.getRecommendationConfigJson() == null) return 20;
        Object value = JsonUtils.parseMap(type.getRecommendationConfigJson()).get("maxResults");
        return value instanceof Number number && number.intValue() >= 1 && number.intValue() <= 100
                ? number.intValue() : 20;
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "read")
    public MaterialRespVO get(Long materialId, Long userId) {
        MaterialDO material = requireMaterial(materialId);
        boolean canManage = permissionApi.hasAnyPermissions(userId, "zsjos:material:manage");
        return toResponses(List.of(material), userId, canManage).getFirst();
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "read")
    public List<MaterialVersionRespVO> getVersions(Long materialId, Long userId) {
        MaterialDO material = requireMaterial(materialId);
        boolean seeAll = Objects.equals(material.getOwnerUserId(), userId)
                || permissionApi.hasAnyPermissions(userId, "zsjos:material:manage");
        return versionMapper.selectByMaterialId(materialId).stream()
                .filter(version -> seeAll || VERSION_EFFECTIVE.equals(version.getStatus()))
                .map(this::toVersionResp)
                .toList();
    }

    public MaterialVersionRespVO getVersion(Long versionId, Long userId) {
        MaterialVersionDO version = versionMapper.selectById(versionId);
        if (version == null) {
            throw exception(MATERIAL_VERSION_NOT_EXISTS);
        }
        MaterialDO material = requireMaterial(version.getMaterialId());
        boolean seeAll = Objects.equals(material.getOwnerUserId(), userId)
                || permissionApi.hasAnyPermissions(userId, "zsjos:material:manage");
        if (!seeAll && !VERSION_EFFECTIVE.equals(version.getStatus())) {
            throw exception(MATERIAL_PERMISSION_DENIED);
        }
        if (!seeAll && !permissionApi.hasAnyPermissions(userId, "zsjos:material:query")) {
            throw exception(MATERIAL_PERMISSION_DENIED);
        }
        return toVersionResp(version);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MaterialSaveReqVO request, Long userId) {
        MaterialTypeDO type = requireCreatableType(request.getMaterialTypeId());
        MaterialSchemaVersionDO schema = materialTypeService.requirePublishedSchema(type);
        PreparedVersion prepared = prepareDraftVersion(type, schema, request, userId);
        MaterialDO material = new MaterialDO();
        material.setMaterialNo(nextMaterialNo());
        material.setMaterialTypeId(type.getId());
        material.setTitle(prepared.title());
        material.setCoverSnapshotJson(prepared.coverSnapshotJson());
        material.setSummary(prepared.summary());
        material.setSource(SOURCE_MANUAL);
        material.setStatus(MATERIAL_DRAFT);
        material.setOwnerUserId(userId);
        material.setLikeCount(0L);
        material.setFavoriteCount(0L);
        material.setReferenceCount(0L);
        material.setPinned(canManage(userId) && Boolean.TRUE.equals(request.getPinned()));
        material.setPriority(canManage(userId) && request.getPriority() != null ? request.getPriority() : 0);
        material.setVersion(0);
        materialMapper.insert(material);

        MaterialVersionDO version = newDraftVersion(material.getId(), 1, schema.getId(), prepared);
        versionMapper.insert(version);
        projectionService.insertProjections(version.getId(), prepared.normalized());
        if (materialMapper.updateDraftPointer(material.getId(), 0, version.getId(), MATERIAL_DRAFT) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        return material.getId();
    }

    public AutoCollectionSnapshot prepareAutoCollection(String materialTypeCode, Long schemaVersionId,
                                                        String schemaHash, MaterialSaveReqVO request,
                                                        Set<Long> trustedBusinessFileIds, Long ownerUserId,
                                                        Map<String, MaterialSchemaService.DictionarySnapshotValue>
                                                                trustedDictionarySnapshots) {
        MaterialTypeDO type = requireAutoCollectType(materialTypeCode);
        MaterialSchemaVersionDO schema = schemaVersionId == null ? null : schemaMapper.selectById(schemaVersionId);
        if (schema == null || !Objects.equals(schema.getMaterialTypeId(), type.getId())
                || !Objects.equals(schema.getSchemaHash(), schemaHash)) {
            throw exception(CONTENT_REVIEW_COLLECTION_INVALID, "已冻结的生产内容素材模板无效");
        }
        PreparedVersion prepared = prepareVersion(schema, request, ownerUserId, trustedBusinessFileIds,
                trustedDictionarySnapshots);
        return new AutoCollectionSnapshot(type.getCode(), type.getId(), schema.getId(), schema.getSchemaHash(),
                prepared.title(), prepared.summary(), prepared.coverSnapshotJson(), prepared.valuesJson(),
                prepared.fieldsJson(), prepared.snapshotsJson(), prepared.filesJson(), prepared.contentHash(),
                prepared.normalized());
    }

    @Transactional(rollbackFor = Exception.class)
    public AutoCollectedMaterial createEffectiveFromContentReview(AutoCollectionSnapshot snapshot,
                                                                   Long sourceContentId,
                                                                   Long sourceContentVersionId,
                                                                   Long ownerUserId) {
        validateAutoCollectionSnapshot(snapshot, snapshot == null ? null : snapshot.materialTypeCode(),
                snapshot == null ? null : snapshot.schemaVersionId(),
                snapshot == null ? null : snapshot.schemaHash());
        PreparedVersion prepared = toPreparedVersion(snapshot);
        LocalDateTime now = LocalDateTime.now();

        MaterialDO material = new MaterialDO();
        material.setMaterialNo(nextMaterialNo());
        material.setMaterialTypeId(snapshot.materialTypeId());
        material.setTitle(prepared.title());
        material.setCoverSnapshotJson(prepared.coverSnapshotJson());
        material.setSummary(prepared.summary());
        material.setSource(SOURCE_CONTENT_REVIEW);
        material.setSourceBusinessId(String.valueOf(sourceContentId));
        material.setSourceBusinessVersionId(String.valueOf(sourceContentVersionId));
        material.setStatus(MATERIAL_DRAFT);
        material.setOwnerUserId(ownerUserId);
        material.setLikeCount(0L);
        material.setFavoriteCount(0L);
        material.setReferenceCount(0L);
        material.setPinned(false);
        material.setPriority(0);
        material.setVersion(0);
        materialMapper.insert(material);

        MaterialVersionDO version = newDraftVersion(material.getId(), 1, snapshot.schemaVersionId(), prepared);
        version.setStatus(VERSION_EFFECTIVE);
        version.setEffectiveAt(now);
        versionMapper.insert(version);
        projectionService.insertProjections(version.getId(), prepared.normalized());
        if (materialMapper.activateVersion(material, version.getId(), version.getTitle(),
                version.getCoverSnapshotJson(), version.getSummary()) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        return new AutoCollectedMaterial(material.getId(), version.getId());
    }

    public AutoCollectionSnapshot parseAndValidateAutoCollectionSnapshot(String snapshotJson,
                                                                          String materialTypeCode,
                                                                          Long schemaVersionId,
                                                                          String schemaHash) {
        try {
            AutoCollectionSnapshot snapshot = JsonUtils.parseObject(snapshotJson, AutoCollectionSnapshot.class);
            validateAutoCollectionSnapshot(snapshot, materialTypeCode, schemaVersionId, schemaHash);
            return snapshot;
        } catch (RuntimeException error) {
            if (error instanceof cn.iocoder.yudao.framework.common.exception.ServiceException serviceError
                    && serviceError.getCode() == CONTENT_REVIEW_COLLECTION_INVALID.getCode()) {
                throw error;
            }
            throw exception(CONTENT_REVIEW_COLLECTION_INVALID, "已冻结的素材收录快照无效");
        }
    }

    public void validateImport(Long materialTypeId, Long schemaVersionId, MaterialSaveReqVO request,
                               Long userId) {
        MaterialTypeDO type = requireImportType(materialTypeId);
        MaterialSchemaVersionDO schema = requireImportSchema(type, schemaVersionId);
        prepareVersion(schema, request, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public AutoCollectedMaterial createEffectiveFromImport(Long materialTypeId, Long schemaVersionId,
                                                            MaterialSaveReqVO request, Long importBatchId,
                                                            Integer rowNo, Long ownerUserId) {
        MaterialTypeDO type = requireImportType(materialTypeId);
        MaterialSchemaVersionDO schema = requireImportSchema(type, schemaVersionId);
        PreparedVersion prepared = prepareVersion(schema, request, ownerUserId);
        LocalDateTime now = LocalDateTime.now();

        MaterialDO material = new MaterialDO();
        material.setMaterialNo(nextMaterialNo());
        material.setMaterialTypeId(type.getId());
        material.setTitle(prepared.title());
        material.setCoverSnapshotJson(prepared.coverSnapshotJson());
        material.setSummary(prepared.summary());
        material.setSource(SOURCE_IMPORT);
        material.setSourceBusinessId(String.valueOf(importBatchId));
        material.setSourceBusinessVersionId(String.valueOf(rowNo));
        material.setStatus(MATERIAL_DRAFT);
        material.setOwnerUserId(ownerUserId);
        material.setLikeCount(0L);
        material.setFavoriteCount(0L);
        material.setReferenceCount(0L);
        material.setPinned(false);
        material.setPriority(0);
        material.setVersion(0);
        materialMapper.insert(material);

        MaterialVersionDO version = newDraftVersion(material.getId(), 1, schema.getId(), prepared);
        version.setStatus(VERSION_EFFECTIVE);
        version.setEffectiveAt(now);
        versionMapper.insert(version);
        projectionService.insertProjections(version.getId(), prepared.normalized());
        if (materialMapper.activateVersion(material, version.getId(), version.getTitle(),
                version.getCoverSnapshotJson(), version.getSummary()) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        return new AutoCollectedMaterial(material.getId(), version.getId());
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "edit")
    @Transactional(rollbackFor = Exception.class)
    public Long update(Long materialId, MaterialSaveReqVO request, Long userId) {
        MaterialDO material = lockMaterial(materialId);
        if (MATERIAL_DISABLED.equals(material.getStatus())) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        if (!Objects.equals(material.getMaterialTypeId(), request.getMaterialTypeId())) {
            throw exception(MATERIAL_FIELD_INVALID, "素材类型不可修改");
        }
        if (request.getExpectedMaterialVersion() == null
                || !Objects.equals(material.getVersion(), request.getExpectedMaterialVersion())) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        MaterialTypeDO type = requireCreatableType(material.getMaterialTypeId());
        MaterialVersionDO currentDraft = material.getCurrentDraftVersionId() == null ? null
                : versionMapper.selectByIdForUpdate(material.getCurrentDraftVersionId(), tenantId());
        if (currentDraft != null && VERSION_IN_APPROVAL.equals(currentDraft.getStatus())) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        MaterialSchemaVersionDO schema;
        boolean updateExistingDraft = currentDraft != null && VERSION_DRAFT.equals(currentDraft.getStatus());
        if (updateExistingDraft) {
            schema = schemaMapper.selectById(currentDraft.getSchemaVersionId());
            if (schema == null) {
                throw exception(MATERIAL_SCHEMA_NOT_EXISTS);
            }
        } else {
            schema = materialTypeService.requirePublishedSchema(type);
        }
        PreparedVersion prepared = prepareDraftVersion(type, schema, request, userId);
        Long draftId;
        if (updateExistingDraft) {
            MaterialVersionDO update = newDraftVersion(materialId, currentDraft.getVersionNo(), schema.getId(), prepared);
            update.setId(currentDraft.getId());
            if (versionMapper.updateDraft(update, currentDraft.getVersion()) != 1) {
                throw exception(MATERIAL_VERSION_CONFLICT);
            }
            projectionService.replaceDraftProjections(currentDraft.getId(), prepared.normalized());
            draftId = currentDraft.getId();
        } else {
            MaterialVersionDO latest = versionMapper.selectLatestForUpdate(materialId, tenantId());
            int nextVersionNo = latest == null ? 1 : latest.getVersionNo() + 1;
            MaterialVersionDO draft = newDraftVersion(materialId, nextVersionNo, schema.getId(), prepared);
            versionMapper.insert(draft);
            projectionService.insertProjections(draft.getId(), prepared.normalized());
            draftId = draft.getId();
        }
        material.setPinned(canManage(userId) && request.getPinned() != null
                ? request.getPinned() : material.getPinned());
        material.setPriority(canManage(userId) && request.getPriority() != null
                ? request.getPriority() : material.getPriority());
        if (materialMapper.updateDraftSummary(material, draftId,
                material.getCurrentEffectiveVersionId() == null ? MATERIAL_DRAFT : MATERIAL_EFFECTIVE,
                prepared.title(), prepared.coverSnapshotJson(), prepared.summary()) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        if (canManage(userId) && (request.getPinned() != null || request.getPriority() != null)) {
            MaterialDO preference = new MaterialDO();
            preference.setId(materialId);
            preference.setPinned(material.getPinned());
            preference.setPriority(material.getPriority());
            materialMapper.updateById(preference);
        }
        return draftId;
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "submit")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long materialId, MaterialSubmitReqVO request, Long userId) {
        MaterialDO material = lockMaterial(materialId);
        if (!Objects.equals(material.getVersion(), request.getExpectedVersion())
                || material.getCurrentDraftVersionId() == null) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        MaterialVersionDO version = versionMapper.selectByIdForUpdate(material.getCurrentDraftVersionId(), tenantId());
        if (version == null || !VERSION_DRAFT.equals(version.getStatus())) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        validateSubmissionCompleteness(material, version, userId);
        MaterialTypeDO type = typeMapper.selectById(material.getMaterialTypeId());
        if (type == null || type.getBpmProcessDefinitionKey() == null
                || type.getBpmProcessDefinitionKey().isBlank()) {
            throw exception(MATERIAL_BPM_UNAVAILABLE);
        }
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                type.getBpmProcessDefinitionKey());
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())
                || !MATERIAL_BPM_CATEGORY.equals(definition.getCategory())) {
            throw exception(MATERIAL_BPM_UNAVAILABLE);
        }
        MaterialApprovalRoundDO latestRound = approvalRoundMapper.selectLatestForUpdate(version.getId(), tenantId());
        int roundNo = latestRound == null ? 1 : latestRound.getRoundNo() + 1;
        String businessKey = BUSINESS_KEY_PREFIX + version.getId();
        String processInstanceId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        MaterialApprovalRoundDO round = new MaterialApprovalRoundDO();
        round.setMaterialVersionId(version.getId());
        round.setRoundNo(roundNo);
        round.setStatus(VERSION_IN_APPROVAL);
        round.setProcessDefinitionId(definition.getId());
        round.setProcessDefinitionKey(definition.getKey());
        round.setProcessDefinitionVersion(definition.getVersion());
        round.setProcessInstanceId(processInstanceId);
        round.setBusinessKey(businessKey);
        round.setSubmittedByUserId(userId);
        round.setSubmittedAt(now);
        approvalRoundMapper.insert(round);

        if (versionMapper.submit(version.getId(), version.getVersion(), processInstanceId, definition.getId(),
                definition.getKey(), definition.getVersion(), businessKey, userId, now) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
        String materialStatus = material.getCurrentEffectiveVersionId() == null
                ? MATERIAL_IN_APPROVAL : MATERIAL_EFFECTIVE;
        if (materialMapper.updateDraftPointer(materialId, material.getVersion(), version.getId(), materialStatus) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }

        BpmProcessInstanceCreateReqDTO processRequest = new BpmProcessInstanceCreateReqDTO();
        processRequest.setProcessDefinitionId(definition.getId());
        processRequest.setProcessDefinitionKey(definition.getKey());
        processRequest.setBusinessKey(businessKey);
        processRequest.setPredefinedProcessInstanceId(processInstanceId);
        processRequest.setStartUserSelectAssignees(request.getStartUserSelectAssignees());
        processRequest.setVariables(Map.of(
                "materialId", material.getId(),
                "materialVersionId", version.getId(),
                "materialNo", material.getMaterialNo(),
                "materialVersionNo", version.getVersionNo()));
        try {
            String createdProcessInstanceId = processInstanceApi.createProcessInstance(userId, processRequest);
            if (!Objects.equals(processInstanceId, createdProcessInstanceId)) {
                throw exception(MATERIAL_BPM_UNAVAILABLE);
            }
        } catch (RuntimeException error) {
            throw exception(MATERIAL_BPM_UNAVAILABLE);
        }
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "disable")
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long materialId, Integer expectedVersion, String reason, Long userId) {
        MaterialDO material = lockMaterial(materialId);
        if (!Objects.equals(material.getVersion(), expectedVersion)
                || !MATERIAL_EFFECTIVE.equals(material.getStatus())) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        if (hasApprovalInProgress(material)) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        if (materialMapper.updateDisabled(material, MATERIAL_DISABLED, reason.trim(), LocalDateTime.now(), userId) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "restore")
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long materialId, Integer expectedVersion) {
        MaterialDO material = lockMaterial(materialId);
        if (!Objects.equals(material.getVersion(), expectedVersion)
                || !MATERIAL_DISABLED.equals(material.getStatus())
                || material.getCurrentEffectiveVersionId() == null) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        if (materialMapper.updateDisabled(material, MATERIAL_EFFECTIVE, null, null, null) != 1) {
            throw exception(MATERIAL_VERSION_CONFLICT);
        }
    }

    public ZsjosDirectUploadInitRespVO initUpload(ZsjosDirectUploadInitReqVO request, Long userId) {
        return BeanUtils.toBean(directUploadService.initMaterial(request, userId), ZsjosDirectUploadInitRespVO.class);
    }

    public MaterialUploadRespVO completeUpload(String uploadToken, Long userId) {
        FileInfoRespDTO saved = directUploadService.completeMaterial(uploadToken, userId);
        MaterialUploadRespVO response = new MaterialUploadRespVO();
        response.setFileId(saved.getId());
        response.setName(saved.getName());
        response.setContentType(saved.getType());
        response.setSize(saved.getSize());
        response.setPreviewUrl(previewUrl(saved.getId(), saved.getUrl()));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(BpmProcessInstanceStatusEvent event) {
        if (event.getBusinessKey() == null || !event.getBusinessKey().startsWith(BUSINESS_KEY_PREFIX)
                || !BpmProcessInstanceStatusEnum.isProcessEndStatus(event.getStatus())) {
            return;
        }
        MaterialApprovalRoundDO locatedRound = approvalRoundMapper.selectByProcessInstanceId(event.getId());
        if (locatedRound == null) {
            return;
        }
        MaterialVersionDO locatedVersion = versionMapper.selectById(locatedRound.getMaterialVersionId());
        if (locatedVersion == null) {
            return;
        }
        MaterialDO material = lockMaterial(locatedVersion.getMaterialId());
        MaterialVersionDO version = versionMapper.selectByIdForUpdate(locatedVersion.getId(), tenantId());
        MaterialApprovalRoundDO round = approvalRoundMapper.selectByProcessInstanceIdForUpdate(event.getId(), tenantId());
        if (round == null || !Objects.equals(round.getBusinessKey(), event.getBusinessKey())
                || !Objects.equals(round.getProcessDefinitionId(), event.getProcessDefinitionId())
                || !Objects.equals(round.getProcessDefinitionKey(), event.getProcessDefinitionKey())
                || !Objects.equals(round.getProcessDefinitionVersion(), event.getProcessDefinitionVersion())) {
            return;
        }
        if (version == null || !VERSION_IN_APPROVAL.equals(version.getStatus())
                || !Objects.equals(version.getId(), round.getMaterialVersionId())
                || !Objects.equals(version.getProcessInstanceId(), event.getId())
                || !Objects.equals(version.getProcessDefinitionId(), round.getProcessDefinitionId())
                || !Objects.equals(version.getProcessDefinitionVersion(), round.getProcessDefinitionVersion())) {
            return;
        }
        String eventKey = event.getEventKey();
        if (Objects.equals(round.getLastEventKey(), eventKey)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean approved = BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(event.getStatus());
        if (approved) {
            if (versionMapper.transition(version.getId(), VERSION_IN_APPROVAL, VERSION_EFFECTIVE, now,
                    null, null) != 1) {
                return;
            }
            if (!Objects.equals(material.getCurrentDraftVersionId(), version.getId())
                    || materialMapper.activateVersion(material, version.getId(), version.getTitle(),
                    version.getCoverSnapshotJson(), version.getSummary()) != 1) {
                throw exception(MATERIAL_VERSION_CONFLICT);
            }
            round.setStatus(VERSION_EFFECTIVE);
        } else {
            if (versionMapper.transition(version.getId(), VERSION_IN_APPROVAL, VERSION_REJECTED,
                    null, now, event.getReason()) != 1) {
                return;
            }
            String status = material.getCurrentEffectiveVersionId() == null
                    ? MATERIAL_REJECTED : MATERIAL_EFFECTIVE;
            if (materialMapper.finishRejectedVersion(material, status) != 1) {
                throw exception(MATERIAL_VERSION_CONFLICT);
            }
            round.setStatus(VERSION_REJECTED);
            round.setResultReason(event.getReason());
        }
        round.setLastEventKey(eventKey);
        round.setConcludedAt(now);
        approvalRoundMapper.updateById(round);
    }

    MaterialDO requireMaterial(Long materialId) {
        MaterialDO material = materialMapper.selectById(materialId);
        if (material == null) {
            throw exception(MATERIAL_NOT_EXISTS);
        }
        return material;
    }

    MaterialDO lockMaterial(Long materialId) {
        MaterialDO material = materialMapper.selectByIdForUpdate(materialId, tenantId());
        if (material == null) {
            throw exception(MATERIAL_NOT_EXISTS);
        }
        return material;
    }

    MaterialVersionRespVO toVersionResp(MaterialVersionDO version) {
        MaterialVersionRespVO response = BeanUtils.toBean(version, MaterialVersionRespVO.class);
        response.setValues(parseMap(version.getValuesJson()));
        response.setFields(JsonUtils.parseArray(version.getFieldSnapshotJson(), MaterialFieldDefinition.class));
        response.setDictSnapshot(parseMap(version.getDictSnapshotJson()));
        List<MaterialFileDO> files = materialFileMapper.selectByVersionId(version.getId());
        response.setFiles(files.stream().map(this::toFileResp).toList());
        MaterialFileDO cover = files.stream().filter(file -> "__cover__".equals(file.getFieldKey())).findFirst()
                .orElse(null);
        if (cover != null) {
            response.setCoverFileId(cover.getInfraFileId());
            response.setCoverPreviewUrl(previewUrl(cover.getInfraFileId(), cover.getFileUrlSnapshot()));
        }
        return response;
    }

    private List<MaterialRespVO> toResponses(List<MaterialDO> materials, Long userId, boolean canManage) {
        Set<Long> typeIds = materials.stream().map(MaterialDO::getMaterialTypeId).collect(Collectors.toSet());
        Map<Long, MaterialTypeDO> types = typeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(MaterialTypeDO::getId, Function.identity()));
        Set<Long> ownerIds = materials.stream().map(MaterialDO::getOwnerUserId).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> users = ownerIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(ownerIds);
        Set<Long> versionIds = materials.stream().map(material -> selectedVersionId(material, userId, canManage))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MaterialVersionDO> versions = (versionIds.isEmpty() ? List.<MaterialVersionDO>of()
                : versionMapper.selectBatchIds(versionIds)).stream()
                .collect(Collectors.toMap(MaterialVersionDO::getId, Function.identity()));
        Set<Long> materialIds = materials.stream().map(MaterialDO::getId).collect(Collectors.toSet());
        Set<Long> liked = likeMapper.selectActiveByUserAndMaterials(userId, materialIds).stream()
                .map(item -> item.getMaterialId()).collect(Collectors.toSet());
        Set<Long> favorited = favoriteMapper.selectActiveByUserAndMaterials(userId, materialIds).stream()
                .map(item -> item.getMaterialId()).collect(Collectors.toSet());
        List<MaterialRespVO> responses = new ArrayList<>(materials.size());
        for (MaterialDO material : materials) {
            MaterialRespVO response = BeanUtils.toBean(material, MaterialRespVO.class);
            MaterialTypeDO type = types.get(material.getMaterialTypeId());
            response.setMaterialTypeName(type == null ? null : type.getName());
            AdminUserRespDTO owner = users.get(material.getOwnerUserId());
            response.setOwnerName(owner == null ? null : owner.getNickname());
            response.setLiked(liked.contains(material.getId()));
            response.setFavorited(favorited.contains(material.getId()));
            MaterialVersionDO selected = versions.get(selectedVersionId(material, userId, canManage));
            response.setCurrentVersion(selected == null ? null : toVersionResp(selected));
            if (response.getCurrentVersion() != null) {
                response.setCoverFileId(response.getCurrentVersion().getCoverFileId());
                response.setCoverPreviewUrl(response.getCurrentVersion().getCoverPreviewUrl());
            }
            response.setAvailableActions(availableActions(material, selected, userId, canManage));
            responses.add(response);
        }
        return responses;
    }

    private List<String> availableActions(MaterialDO material, MaterialVersionDO selected, Long userId,
                                          boolean canManage) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        boolean owner = Objects.equals(material.getOwnerUserId(), userId);
        MaterialVersionDO draft = material.getCurrentDraftVersionId() == null ? null
                : versionMapper.selectById(material.getCurrentDraftVersionId());
        if (!MATERIAL_DISABLED.equals(material.getStatus()) && (owner || canManage)
                && (draft == null || !VERSION_IN_APPROVAL.equals(draft.getStatus()))) {
            if (permissionApi.hasAnyPermissions(userId, "zsjos:material:update")) actions.add("UPDATE");
        }
        if ((owner || canManage) && draft != null && VERSION_DRAFT.equals(draft.getStatus())
                && permissionApi.hasAnyPermissions(userId, "zsjos:material:submit")) {
            actions.add("SUBMIT");
        }
        if (MATERIAL_EFFECTIVE.equals(material.getStatus())) {
            if (permissionApi.hasAnyPermissions(userId, "zsjos:material:like")) actions.add("LIKE");
            if (permissionApi.hasAnyPermissions(userId, "zsjos:material:favorite")) actions.add("FAVORITE");
            if (permissionApi.hasAnyPermissions(userId, "zsjos:material:reference")) actions.add("REFERENCE");
            if (canManage && permissionApi.hasAnyPermissions(userId, "zsjos:material:disable")) actions.add("DISABLE");
        }
        if (MATERIAL_DISABLED.equals(material.getStatus()) && canManage
                && permissionApi.hasAnyPermissions(userId, "zsjos:material:restore")) {
            actions.add("RESTORE");
        }
        return new ArrayList<>(actions);
    }

    private MaterialTypeDO requireCreatableType(Long typeId) {
        MaterialTypeDO type = materialTypeService.requireType(typeId);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(type.getStatus())) {
            throw exception(MATERIAL_TYPE_DISABLED);
        }
        if (!Boolean.TRUE.equals(type.getAllowManualCreate())) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        return type;
    }

    private MaterialTypeDO requireAutoCollectType(String typeCode) {
        MaterialTypeDO type = typeCode == null ? null : typeMapper.selectByCode(typeCode);
        if (type == null || !CommonStatusEnum.ENABLE.getStatus().equals(type.getStatus())
                || !Boolean.TRUE.equals(type.getAllowAutoCollect())) {
            throw exception(CONTENT_REVIEW_COLLECTION_INVALID, "生产内容素材类型未启用自动收录");
        }
        return type;
    }

    private MaterialTypeDO requireImportType(Long typeId) {
        MaterialTypeDO type = materialTypeService.requireType(typeId);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(type.getStatus())
                || !Boolean.TRUE.equals(type.getAllowImport())) {
            throw exception(MATERIAL_IMPORT_INVALID, "该素材类型未启用导入");
        }
        return type;
    }

    private MaterialSchemaVersionDO requireImportSchema(MaterialTypeDO type, Long schemaVersionId) {
        MaterialSchemaVersionDO schema = schemaVersionId == null ? null : schemaMapper.selectById(schemaVersionId);
        if (schema == null || !Objects.equals(schema.getMaterialTypeId(), type.getId())
                || !Set.of(SCHEMA_PUBLISHED, SCHEMA_ARCHIVED).contains(schema.getStatus())) {
            throw exception(MATERIAL_IMPORT_INVALID, "导入模板版本不存在或已失效");
        }
        return schema;
    }

    private PreparedVersion prepareVersion(MaterialSchemaVersionDO schema, MaterialSaveReqVO request, Long userId) {
        return prepareVersion(schema, request, userId, Set.of(), Map.of());
    }

    private PreparedVersion prepareDraftVersion(MaterialTypeDO type, MaterialSchemaVersionDO schema,
                                                MaterialSaveReqVO request, Long userId) {
        if (request == null) {
            throw exception(MATERIAL_FIELD_INVALID, "素材请求不能为空");
        }
        List<MaterialFieldDefinition> fields = schemaService.parseFields(schema.getFieldsJson());
        String title = resolveTitle(type, request, fields, false);
        MaterialSchemaService.NormalizedMaterial dynamic = schemaService.normalizeDraft(fields, request.getValues(),
                userId);
        MaterialSchemaService.FileValue cover = schemaService.normalizeCover(request.getCoverFileId(), userId);
        List<MaterialSchemaService.FileValue> files = new ArrayList<>(dynamic.files());
        if (cover != null) files.add(cover);
        String searchText = String.join(" ", List.of(title, Objects.toString(request.getSummary(), ""),
                dynamic.searchText())).trim();
        MaterialSchemaService.NormalizedMaterial normalized = new MaterialSchemaService.NormalizedMaterial(
                dynamic.values(), dynamic.snapshots(), dynamic.indexes(), files, dynamic.dimensions(), searchText);
        String coverJson = cover == null ? null : JsonUtils.toJsonString(cover.snapshot());
        String valuesJson = JsonUtils.toJsonString(normalized.values());
        String fieldsJson = JsonUtils.toJsonString(fields);
        String snapshotsJson = JsonUtils.toJsonString(normalized.snapshots());
        String filesJson = JsonUtils.toJsonString(files.stream().map(MaterialSchemaService.FileValue::snapshot).toList());
        String contentHash = contentHash(title, request.getSummary(), coverJson, normalized.values(), schema.getSchemaHash());
        return new PreparedVersion(title, trimToNull(request.getSummary()), coverJson, valuesJson, fieldsJson,
                snapshotsJson, filesJson, contentHash, normalized);
    }

    private PreparedVersion prepareVersion(MaterialSchemaVersionDO schema, MaterialSaveReqVO request, Long userId,
                                           Set<Long> trustedBusinessFileIds) {
        return prepareVersion(schema, request, userId, trustedBusinessFileIds, Map.of());
    }

    private PreparedVersion prepareVersion(MaterialSchemaVersionDO schema, MaterialSaveReqVO request, Long userId,
                                           Set<Long> trustedBusinessFileIds,
                                           Map<String, MaterialSchemaService.DictionarySnapshotValue>
                                                   trustedDictionarySnapshots) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw exception(MATERIAL_FIELD_INVALID, "标题不能为空");
        }
        List<MaterialFieldDefinition> fields = schemaService.parseFields(schema.getFieldsJson());
        MaterialSchemaService.NormalizedMaterial dynamic = schemaService.normalize(fields, request.getValues(),
                userId, trustedBusinessFileIds, trustedDictionarySnapshots);
        MaterialSchemaService.FileValue cover = schemaService.normalizeCover(request.getCoverFileId(), userId,
                trustedBusinessFileIds);
        List<MaterialSchemaService.FileValue> files = new ArrayList<>(dynamic.files());
        if (cover != null) files.add(cover);
        String searchText = String.join(" ", List.of(request.getTitle(),
                Objects.toString(request.getSummary(), ""), dynamic.searchText())).trim();
        MaterialSchemaService.NormalizedMaterial normalized = new MaterialSchemaService.NormalizedMaterial(
                dynamic.values(), dynamic.snapshots(), dynamic.indexes(), files, dynamic.dimensions(), searchText);
        String coverJson = cover == null ? null : JsonUtils.toJsonString(cover.snapshot());
        String valuesJson = JsonUtils.toJsonString(normalized.values());
        String fieldsJson = JsonUtils.toJsonString(fields);
        String snapshotsJson = JsonUtils.toJsonString(normalized.snapshots());
        String filesJson = JsonUtils.toJsonString(files.stream()
                .map(MaterialSchemaService.FileValue::snapshot).toList());
        String contentHash = contentHash(request.getTitle().trim(), request.getSummary(), coverJson,
                normalized.values(), schema.getSchemaHash());
        return new PreparedVersion(request.getTitle().trim(), trimToNull(request.getSummary()), coverJson,
                valuesJson, fieldsJson, snapshotsJson, filesJson, contentHash, normalized);
    }

    private String resolveTitle(MaterialTypeDO type, MaterialSaveReqVO request,
                                List<MaterialFieldDefinition> fields, boolean strict) {
        if ("viral_account".equals(type.getCode())) {
            Object accountName = request.getValues() == null ? null : request.getValues().get("account_name");
            String resolved = accountName == null ? "" : String.valueOf(accountName).trim();
            if (!resolved.isBlank()) return resolved;
            if (strict) throw exception(MATERIAL_FIELD_INVALID, "账号名称不能为空");
            return "爆款账号拆解草稿";
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw exception(MATERIAL_FIELD_INVALID, "素材标题不能为空");
        }
        return request.getTitle().trim();
    }

    private void validateSubmissionCompleteness(MaterialDO material, MaterialVersionDO version, Long userId) {
        MaterialTypeDO type = typeMapper.selectById(material.getMaterialTypeId());
        if (type == null) throw exception(MATERIAL_TYPE_NOT_EXISTS);
        MaterialSchemaVersionDO schema = schemaMapper.selectById(version.getSchemaVersionId());
        if (schema == null) throw exception(MATERIAL_SCHEMA_NOT_EXISTS);
        List<MaterialFieldDefinition> fields = schemaService.parseFields(schema.getFieldsJson());
        MaterialSchemaService.NormalizedMaterial normalized = schemaService.normalize(fields,
                parseMap(version.getValuesJson()), userId);
        if ("viral_account".equals(type.getCode())
                && (version.getCoverSnapshotJson() == null || version.getCoverSnapshotJson().isBlank())) {
            throw exception(MATERIAL_FIELD_INVALID, "账号主页截图不能为空");
        }
        resolveTitle(type, new MaterialSaveReqVO().setValues(normalized.values()), fields, true);
    }

    private void validateAutoCollectionSnapshot(AutoCollectionSnapshot snapshot, String materialTypeCode,
                                                Long schemaVersionId, String schemaHash) {
        if (snapshot == null || snapshot.normalized() == null || snapshot.title() == null
                || snapshot.title().isBlank() || !Objects.equals(snapshot.materialTypeCode(), materialTypeCode)
                || !Objects.equals(snapshot.schemaVersionId(), schemaVersionId)
                || !Objects.equals(snapshot.schemaHash(), schemaHash)) {
            throw exception(CONTENT_REVIEW_COLLECTION_INVALID, "已冻结的素材收录快照无效");
        }
        MaterialTypeDO type = typeMapper.selectById(snapshot.materialTypeId());
        MaterialSchemaVersionDO schema = schemaMapper.selectById(snapshot.schemaVersionId());
        if (type == null || schema == null || !Objects.equals(type.getCode(), snapshot.materialTypeCode())
                || !Objects.equals(schema.getMaterialTypeId(), type.getId())
                || !Objects.equals(schema.getSchemaHash(), snapshot.schemaHash())
                || !Objects.equals(snapshot.contentHash(), contentHash(snapshot.title(), snapshot.summary(),
                snapshot.coverSnapshotJson(), snapshot.normalized().values(), snapshot.schemaHash()))) {
            throw exception(CONTENT_REVIEW_COLLECTION_INVALID, "已冻结的素材收录快照无效");
        }
    }

    private PreparedVersion toPreparedVersion(AutoCollectionSnapshot snapshot) {
        return new PreparedVersion(snapshot.title(), snapshot.summary(), snapshot.coverSnapshotJson(),
                snapshot.valuesJson(), snapshot.fieldsJson(), snapshot.snapshotsJson(), snapshot.filesJson(),
                snapshot.contentHash(), snapshot.normalized());
    }

    private String contentHash(String title, String summary, String coverSnapshotJson,
                               Map<String, Object> values, String schemaHash) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "title", title.trim(),
                "summary", Objects.toString(summary, ""),
                "cover", Objects.toString(coverSnapshotJson, ""),
                "values", values,
                "schemaHash", schemaHash)));
    }

    private MaterialVersionDO newDraftVersion(Long materialId, int versionNo, Long schemaVersionId,
                                              PreparedVersion prepared) {
        MaterialVersionDO version = new MaterialVersionDO();
        version.setMaterialId(materialId);
        version.setVersionNo(versionNo);
        version.setSchemaVersionId(schemaVersionId);
        version.setStatus(VERSION_DRAFT);
        version.setTitle(prepared.title());
        version.setCoverSnapshotJson(prepared.coverSnapshotJson());
        version.setSummary(prepared.summary());
        version.setValuesJson(prepared.valuesJson());
        version.setFieldSnapshotJson(prepared.fieldsJson());
        version.setDictSnapshotJson(prepared.snapshotsJson());
        version.setFileSnapshotJson(prepared.filesJson());
        version.setSearchText(prepared.normalized().searchText());
        version.setContentHash(prepared.contentHash());
        version.setVersion(0);
        return version;
    }

    private boolean hasApprovalInProgress(MaterialDO material) {
        if (material.getCurrentDraftVersionId() == null) return false;
        MaterialVersionDO version = versionMapper.selectById(material.getCurrentDraftVersionId());
        return version != null && VERSION_IN_APPROVAL.equals(version.getStatus());
    }

    private Long selectedVersionId(MaterialDO material, Long userId, boolean canManage) {
        if ((canManage || Objects.equals(material.getOwnerUserId(), userId))
                && material.getCurrentDraftVersionId() != null) {
            return material.getCurrentDraftVersionId();
        }
        return material.getCurrentEffectiveVersionId();
    }

    private MaterialFileRespVO toFileResp(MaterialFileDO file) {
        MaterialFileRespVO response = new MaterialFileRespVO();
        response.setId(file.getId());
        response.setFieldKey(file.getFieldKey());
        response.setGroupIndex(file.getGroupIndex());
        response.setFileId(file.getInfraFileId());
        response.setName(file.getOriginalName());
        response.setContentType(file.getContentType());
        response.setSize(file.getFileSize());
        response.setPreviewUrl(previewUrl(file.getInfraFileId(), file.getFileUrlSnapshot()));
        return response;
    }

    private String previewUrl(Long fileId, String fallback) {
        try {
            return fileApi.presignGetUrl(fileId, FILE_PREVIEW_SECONDS);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        Map<String, Object> value = json == null ? null : JsonUtils.parseObject(json, Map.class);
        return value == null ? Map.of() : value;
    }

    private boolean canManage(Long userId) {
        return permissionApi.hasAnyPermissions(userId, "zsjos:material:manage");
    }

    private Long tenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private String nextMaterialNo() {
        return "MAT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record PreparedVersion(String title, String summary, String coverSnapshotJson,
                                   String valuesJson, String fieldsJson, String snapshotsJson,
                                   String filesJson, String contentHash,
                                   MaterialSchemaService.NormalizedMaterial normalized) {
    }

    public record AutoCollectedMaterial(Long materialId, Long materialVersionId) {
    }

    public record AutoCollectionSnapshot(String materialTypeCode, Long materialTypeId, Long schemaVersionId,
                                         String schemaHash, String title, String summary,
                                         String coverSnapshotJson, String valuesJson, String fieldsJson,
                                         String snapshotsJson, String filesJson, String contentHash,
                                         MaterialSchemaService.NormalizedMaterial normalized) {
    }
}
