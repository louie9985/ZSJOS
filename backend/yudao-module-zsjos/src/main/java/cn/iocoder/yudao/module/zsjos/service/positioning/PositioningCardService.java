package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardDraftRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardImportReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardImportRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardImportSourceRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountProfileService;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import java.util.Map;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;

@Service
public class PositioningCardService {
    @jakarta.annotation.Resource private cn.iocoder.yudao.module.infra.api.file.FileApi positioningFileApi;

    public record CardFile(Long id, String name, String type, Long size, String url) {}

    private String fileDirectory(Long id) {
        return "zsjos/positioning-card/" + cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId() + "/" + id + "/";
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "submit-review")
    public CardFile uploadAttachment(Long id, String fieldKey, byte[] bytes, String name, String mimeType, Long userId) {
        var card = require(id); requireStatus(card, POSITIONING_CO_CREATING);
        if (!Objects.equals(card.getDirectorUserId(), userId)) throw exception(POSITIONING_CARD_PERMISSION_DENIED);
        assignmentService.requireMaster(card);
        var fields = JsonUtils.parseArray(card.getFieldsSnapshotJson(), DirectorFormTemplateVO.Field.class);
        if (fields.stream().noneMatch(f -> f.getKey().equals(fieldKey) && Boolean.TRUE.equals(f.getEnabled()) && "attachment".equals(f.getType()))
                || bytes.length == 0 || bytes.length > 20 * 1024 * 1024) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        var file = positioningFileApi.createFileInfo(bytes, name == null ? "attachment" : name.replaceAll("[\\\\/\\r\\n]", "_"),
                fileDirectory(id) + fieldKey, mimeType);
        return new CardFile(file.getId(), file.getName(), file.getType(), file.getSize(), positioningFileApi.presignGetUrl(file.getId(), 300));
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "read")
    public CardFile attachment(Long id, Long fileId, Long userId) {
        require(id);
        var file = positioningFileApi.getFileInfo(fileId);
        if (file == null || file.getPath() == null || !file.getPath().startsWith(fileDirectory(id))) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        return new CardFile(file.getId(), file.getName(), file.getType(), file.getSize(), positioningFileApi.presignGetUrl(fileId, 300));
    }

    private void freezeAttachments(Long id, DirectorFormTemplateVO.Snapshot snapshot) {
        for (var field : snapshot.getFields()) {
            if (!"attachment".equals(field.getType())) continue;
            Object raw = snapshot.getValues().get(field.getKey());
            if (raw == null) continue;
            if (!(raw instanceof java.util.Collection<?> ids) || ids.size() > 20) throw exception(DIRECTOR_FORM_VALUE_INVALID);
            List<CardFile> files = new java.util.ArrayList<>();
            for (Object value : ids) {
                if (!(value instanceof Number number) || number.longValue() <= 0 || number.doubleValue() != number.longValue()) throw exception(DIRECTOR_FORM_VALUE_INVALID);
                var file = positioningFileApi.getFileInfo(number.longValue());
                if (file == null || file.getPath() == null || !file.getPath().startsWith(fileDirectory(id) + field.getKey() + "/")) throw exception(DIRECTOR_FORM_VALUE_INVALID);
                files.add(new CardFile(file.getId(), file.getName(), file.getType(), file.getSize(), null));
            }
            snapshot.getDictSnapshots().put(field.getKey(), files);
        }
    }
    @Resource private PositioningAssignmentService assignmentService;
    @Resource private PositioningCardMapper mapper;
    @Resource private PositioningCardSubmissionMapper submissionMapper;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PositioningCardObjectPermissionProvider objectPermissionProvider;
    @Resource private MediaDataScopeService dataScopeService;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private PersonMapper personMapper;
    @Resource private MediaWorkflowEventService workflowEventService;
    @Resource private DirectorFormTemplateService directorFormTemplateService;
    @Resource private MediaAccountProfileService mediaAccountProfileService;

    public PageResult<PositioningCardRespVO> page(PositioningCardPageReqVO req, Long userId) {
        MediaDataScopeService.Scope scope = dataScopeService.resolve(userId, "zsjos:positioning-card:query-all");
        List<Long> accountIds = accountMapper.selectVisibleIds(scope.userIds(), scope.all());
        PageResult<PositioningCardDO> page = mapper.selectPage(req, scope.userIds(), accountIds, scope.all());
        return new PageResult<>(page.getList().stream().map(row -> toResp(row, userId)).toList(), page.getTotal());
    }

    public DirectorFormTemplateVO.Snapshot getPublishedTemplate(Long templateId) {
        return directorFormTemplateService.validateAndSnapshot(
                DirectorFormTemplateService.SCENE_POSITIONING, templateId, Map.of(), false);
    }

    public List<PositioningCardImportSourceRespVO> getImportSources(Long studentPersonId, Long accountId, Long serviceRelationId, Long userId) {
        requireDraftTarget(studentPersonId, null, serviceRelationId, userId);
        return submissionMapper.selectByService(serviceRelationId).stream()
                .filter(row -> Objects.equals(row.getStudentPersonId(), studentPersonId) && objectPermissionProvider.hasPermission(row.getCardId(), "read", userId))
                .map(row -> {
                    var response = new PositioningCardImportSourceRespVO();
                    response.setSubmissionId(row.getId()); response.setCardId(row.getCardId());
                    var card = mapper.selectById(row.getCardId());
                    response.setCardNo(card.getCardNo()); response.setAccountLabel(card.getCardNo());
                    response.setSubmissionNo(row.getSubmissionNo()); response.setStatus(row.getStatus());
                    response.setSubmittedAt(row.getSubmittedAt()); response.setSameAccount(false); return response;
                }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PositioningCardImportRespVO importSubmission(PositioningCardImportReqVO req, Long userId) {
        var relation = requireDraftTarget(req.getStudentPersonId(), null, req.getServiceRelationId(), userId);
        var source = submissionMapper.selectById(req.getSourceSubmissionId());
        var sourceCard = source == null ? null : mapper.selectById(source.getCardId());
        if (source == null || sourceCard == null || !Objects.equals(source.getServiceRelationId(), req.getServiceRelationId())
                || !Objects.equals(sourceCard.getServiceRelationId(), req.getServiceRelationId())
                || !Objects.equals(source.getStudentPersonId(), req.getStudentPersonId())
                || !Objects.equals(sourceCard.getStudentPersonId(), req.getStudentPersonId())) throw exception(POSITIONING_IMPORT_SOURCE_INVALID);
        objectPermissionProvider.check(sourceCard.getId(), "read", userId);
        Long masterId = assignmentService.masterId(relation.getId());
        if (masterId == null) throw exception(POSITIONING_MASTER_REQUIRED);
        var draft = mapper.selectById(masterId);
        assignmentService.requireMaster(draft);
        requireStatus(draft, POSITIONING_CO_CREATING);
        if (!Objects.equals(draft.getDirectorUserId(), userId)) throw exception(POSITIONING_CARD_PERMISSION_DENIED);
        if (!Objects.equals(draft.getId(), req.getTargetDraftId()) || !Objects.equals(draft.getVersion(), req.getVersion()))
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        var published = directorFormTemplateService.validateAndSnapshot(DirectorFormTemplateService.SCENE_POSITIONING, draft.getTemplateId(), Map.of(), false);
        ImportValues imported = mapImportValues(source, published.getFields());
        var snapshot = directorFormTemplateService.validateAndSnapshotVersion(DirectorFormTemplateService.SCENE_POSITIONING,
                published.getTemplateVersionId(), imported.values(), false, imported.dictSnapshots());
        Integer expectedVersion = draft.getVersion();
        applyImportedSnapshot(draft, source, snapshot, req.getTrialEndDate(), relation.getOperatorUserId());
        if (mapper.overwriteDraftFromImport(draft, expectedVersion) == 0) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        draft.setVersion(expectedVersion + 1);
        return toImportResp(draft, snapshot, imported.skippedFieldKeys());
    }

    private ImportTarget requireImportTarget(Long studentPersonId, Long accountId, Long serviceRelationId,
                                             Long userId, boolean lockRelation) {
        if(serviceRelationId==null)throw exception(POSITIONING_REFERENCE_INVALID);
        Long tenantId=cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        var account = accountMapper.selectById(accountId);
        if (account == null || studentPersonId == null || !Objects.equals(account.getStudentPersonId(), studentPersonId)
                || !Objects.equals(account.getCreateServiceRelationId(),serviceRelationId)
                || !Objects.equals(account.getTenantId(),tenantId)
                || personMapper.selectById(studentPersonId) == null) {
            throw exception(POSITIONING_REFERENCE_INVALID);
        }
        var relation = lockRelation ? relationMapper.selectByIdForUpdate(serviceRelationId,
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())
                : relationMapper.selectById(serviceRelationId);
        if (relation == null || !Objects.equals(relation.getContentDirectorUserId(), userId)
                || !Objects.equals(relation.getTenantId(),tenantId)
                || !Objects.equals(relation.getPersonId(), studentPersonId)
                || !"active".equals(relation.getStatus()) || !"accepted".equals(relation.getAcceptanceStatus())
                || !isPositioningComplete(relation.getDirectorStage())) {
            throw exception(POSITIONING_REFERENCE_INVALID);
        }
        return new ImportTarget(account, relation);
    }

    private ImportValues mapImportValues(PositioningCardSubmissionDO source,
                                         List<DirectorFormTemplateVO.Field> targetFields) {
        List<DirectorFormTemplateVO.Field> sourceFields = StrUtil.isBlank(source.getFieldsSnapshotJson()) ? List.of()
                : JsonUtils.parseArray(source.getFieldsSnapshotJson(), DirectorFormTemplateVO.Field.class);
        Map<String, DirectorFormTemplateVO.Field> sourceByKey = sourceFields == null ? Map.of()
                : sourceFields.stream().collect(Collectors.toMap(DirectorFormTemplateVO.Field::getKey,
                Function.identity(), (first, ignored) -> first));
        Map<String, Object> sourceValues = StrUtil.isBlank(source.getValuesSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(source.getValuesSnapshotJson(), Map.class);
        Map<String, Object> sourceDictSnapshots = StrUtil.isBlank(source.getDictSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(source.getDictSnapshotJson(), Map.class);
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> dictSnapshots = new HashMap<>();
        Set<String> compatibleKeys = targetFields.stream().filter(DirectorFormTemplateVO.Field::getEnabled)
                .filter(target -> sourceByKey.containsKey(target.getKey())
                        && compatibleImportTypes(sourceByKey.get(target.getKey()), target))
                .map(DirectorFormTemplateVO.Field::getKey).collect(Collectors.toSet());
        compatibleKeys.forEach(key -> {
            if (sourceValues.containsKey(key)) values.put(key, sourceValues.get(key));
            if (sourceDictSnapshots.containsKey(key)) dictSnapshots.put(key, sourceDictSnapshots.get(key));
        });
        List<String> skipped = sourceValues.keySet().stream().filter(key -> !compatibleKeys.contains(key)).toList();
        return new ImportValues(values, dictSnapshots, skipped);
    }

    private boolean compatibleImportTypes(DirectorFormTemplateVO.Field source,
                                          DirectorFormTemplateVO.Field target) {
        if (Objects.equals(source.getType(), target.getType())) {
            return !isDictionaryField(target) || Objects.equals(source.getDictType(), target.getDictType());
        }
        Set<String> singleChoice = Set.of("select", "radio", "dict");
        Set<String> multipleChoice = Set.of("multi_select", "checkbox_group");
        if (singleChoice.contains(source.getType()) && singleChoice.contains(target.getType())
                || multipleChoice.contains(source.getType()) && multipleChoice.contains(target.getType())) {
            return Objects.equals(source.getDictType(), target.getDictType());
        }
        return false;
    }

    private boolean isDictionaryField(DirectorFormTemplateVO.Field field) {
        return field.getDictType() != null || Set.of("select", "radio", "dict", "multi_select",
                "checkbox_group").contains(field.getType());
    }

    private void applyImportedSnapshot(PositioningCardDO draft, PositioningCardSubmissionDO source,
                                       DirectorFormTemplateVO.Snapshot snapshot, java.time.LocalDate trialEndDate,
                                       Long operatorUserId) {
        draft.setOperatorUserId(operatorUserId).setTemplateId(snapshot.getTemplateId())
                .setTemplateVersionId(snapshot.getTemplateVersionId())
                .setFieldsSnapshotJson(JsonUtils.toJsonString(snapshot.getFields()))
                .setValuesSnapshotJson(JsonUtils.toJsonString(snapshot.getValues()))
                .setDictSnapshotJson(JsonUtils.toJsonString(snapshot.getDictSnapshots()))
                .setTrialEndDate(trialEndDate).setLayer1Json(jsonOrEmpty(source.getLayer1Json()))
                .setLayer2Json(jsonOrEmpty(source.getLayer2Json())).setFormulaJson(jsonOrEmpty(source.getFormulaJson()))
                .setFeasibilityJson(jsonOrEmpty(source.getFeasibilityJson()))
                .setContentFormJson(jsonOrEmpty(source.getContentFormJson()))
                .setComplianceJson(jsonOrEmpty(source.getComplianceJson()))
                .setProfessionalRisk(Boolean.TRUE.equals(source.getProfessionalRisk()));
    }

    private PositioningCardImportRespVO toImportResp(PositioningCardDO draft,
                                                     DirectorFormTemplateVO.Snapshot snapshot,
                                                     List<String> skippedFieldKeys) {
        PositioningCardImportRespVO response = new PositioningCardImportRespVO();
        response.setId(draft.getId()); response.setVersion(draft.getVersion());
        response.setTemplateId(snapshot.getTemplateId()); response.setTemplateVersionId(snapshot.getTemplateVersionId());
        response.setTemplateVersionNo(snapshot.getTemplateVersionNo()); response.setFields(snapshot.getFields());
        response.setValues(snapshot.getValues()); response.setDictSnapshots(snapshot.getDictSnapshots());
        response.setTrialEndDate(draft.getTrialEndDate()); response.setProfessionalRisk(draft.getProfessionalRisk());
        response.setSkippedFieldKeys(skippedFieldKeys);
        return response;
    }

    private record ImportTarget(
            cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO account,
            cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO relation) {}
    private record ImportValues(Map<String, Object> values, Map<String, Object> dictSnapshots,
                                List<String> skippedFieldKeys) {}

    @Transactional(rollbackFor = Exception.class)
    public PositioningCardDraftRespVO create(PositioningCardSaveReqVO req, Long userId) {
        req.setAccountId(null);
        var relation = requireDraftTarget(req.getStudentPersonId(), null, req.getServiceRelationId(), userId);
        var snapshot = directorFormTemplateService.validateAndSnapshot(
                DirectorFormTemplateService.SCENE_POSITIONING, req.getTemplateId(), req.getValues(), false);
        var serviceCards = mapper.selectByService(relation.getId());
        if (!serviceCards.isEmpty()) {
            Long masterId = assignmentService.masterId(relation.getId());
            if (masterId == null) throw exception(POSITIONING_MASTER_REQUIRED);
            var master = mapper.selectById(masterId);
            if (!POSITIONING_CO_CREATING.equals(master.getStatus())) throw exception(POSITIONING_CARD_STATE_INVALID);
            if (!sameDraft(master, snapshot, req.getTrialEndDate(), req)) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
            return new PositioningCardDraftRespVO(master.getId(), master.getVersion());
        }
        java.time.LocalDate trialEndDate = req.getTrialEndDate();
        PositioningCardDO existing = mapper.selectLatestCreatingDraft(relation.getId(), req.getAccountId(),
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        if (existing != null) {
            if (!sameDraft(existing, snapshot, trialEndDate, req)) {
                throw exception(POSITIONING_CARD_VERSION_CONFLICT);
            }
            return new PositioningCardDraftRespVO(existing.getId(), existing.getVersion());
        }
        PositioningCardDO card = new PositioningCardDO();
        card.setCardNo("PC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .setAccountId(req.getAccountId()).setStudentPersonId(req.getStudentPersonId()).setDirectorUserId(userId)
                .setServiceRelationId(relation.getId()).setOperatorUserId(relation.getOperatorUserId())
                .setTemplateId(snapshot.getTemplateId()).setTemplateVersionId(snapshot.getTemplateVersionId())
                .setFieldsSnapshotJson(JsonUtils.toJsonString(snapshot.getFields()))
                .setValuesSnapshotJson(JsonUtils.toJsonString(snapshot.getValues()))
                .setDictSnapshotJson(JsonUtils.toJsonString(snapshot.getDictSnapshots())).setTrialEndDate(trialEndDate)
                .setVersionNo(1).setLayer1Json(jsonOrEmpty(req.getLayer1Json())).setLayer2Json(jsonOrEmpty(req.getLayer2Json()))
                .setFormulaJson(jsonOrEmpty(req.getFormulaJson())).setFeasibilityJson(jsonOrEmpty(req.getFeasibilityJson()))
                .setContentFormJson(jsonOrEmpty(req.getContentFormJson())).setComplianceJson(jsonOrEmpty(req.getComplianceJson()))
                .setProfessionalRisk(Boolean.TRUE.equals(req.getProfessionalRisk()))
                .setStatus(POSITIONING_CO_CREATING).setVersion(0);
        mapper.insert(card);
        assignmentService.register(relation.getId(), relation.getPersonId(), card.getId());
        return new PositioningCardDraftRespVO(card.getId(), card.getVersion());
    }

    private cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO requireDraftTarget(
            Long studentPersonId, Long accountId, Long serviceRelationId, Long userId) {
        if (accountId != null) {
            return requireImportTarget(studentPersonId, accountId, serviceRelationId, userId, true).relation();
        }
        if (studentPersonId == null || serviceRelationId == null
                || personMapper.selectById(studentPersonId) == null) throw exception(POSITIONING_REFERENCE_INVALID);
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        var relation = relationMapper.selectByIdForUpdate(serviceRelationId, tenantId);
        if (relation == null || !Objects.equals(relation.getPersonId(), studentPersonId)
                || !Objects.equals(relation.getContentDirectorUserId(), userId)
                || !Objects.equals(relation.getTenantId(), tenantId)
                || !"active".equals(relation.getStatus()) || !"accepted".equals(relation.getAcceptanceStatus())
                || !isPositioningComplete(relation.getDirectorStage())) throw exception(POSITIONING_REFERENCE_INVALID);
        return relation;
    }

    private boolean isPositioningComplete(String stage) {
        return stage != null && Set.of("positioning_interview_completed", "positioning_ready").contains(stage);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "submit-review")
    @Transactional(rollbackFor = Exception.class)
    public PositioningCardDraftRespVO updateDraft(Long id, PositioningCardSaveReqVO req, Long userId) {
        PositioningCardDO card = require(id);
        assignmentService.requireMaster(card);
        requireStatus(card, POSITIONING_CO_CREATING);
        if (!Objects.equals(card.getDirectorUserId(), userId)) {
            throw exception(POSITIONING_CARD_PERMISSION_DENIED);
        }
        Map<String, Object> previousDictSnapshots = StrUtil.isBlank(card.getDictSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getDictSnapshotJson(), Map.class);
        var published = directorFormTemplateService.requirePublished(
                DirectorFormTemplateService.SCENE_POSITIONING, card.getTemplateId());
        var snapshot = directorFormTemplateService.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, published.getId(), req.getValues(), false,
                previousDictSnapshots);
        // Keep retired values recoverable; only current fields participate in new edits and submissions.
        var currentKeys = snapshot.getFields().stream().map(DirectorFormTemplateVO.Field::getKey)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Object> retained = StrUtil.isBlank(card.getValuesSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getValuesSnapshotJson(), Map.class);
        snapshot.setValues(new java.util.LinkedHashMap<>(snapshot.getValues()));
        snapshot.setDictSnapshots(new java.util.LinkedHashMap<>(snapshot.getDictSnapshots()));
        retained.forEach((key, value) -> { if (!currentKeys.contains(key)) snapshot.getValues().put(key, value); });
        previousDictSnapshots.forEach((key, value) -> { if (!currentKeys.contains(key)) snapshot.getDictSnapshots().put(key, value); });
        freezeAttachments(id, snapshot);
        java.time.LocalDate trialEndDate = req.getTrialEndDate();
        String layer1Json = draftJson(req.getLayer1Json(), card.getLayer1Json());
        String layer2Json = draftJson(req.getLayer2Json(), card.getLayer2Json());
        String formulaJson = draftJson(req.getFormulaJson(), card.getFormulaJson());
        String feasibilityJson = draftJson(req.getFeasibilityJson(), card.getFeasibilityJson());
        String contentFormJson = draftJson(req.getContentFormJson(), card.getContentFormJson());
        String complianceJson = draftJson(req.getComplianceJson(), card.getComplianceJson());
        Boolean professionalRisk = req.getProfessionalRisk() == null ? card.getProfessionalRisk() : req.getProfessionalRisk();
        if (!Objects.equals(card.getVersion(), req.getVersion())) {
            if (req.getVersion() != null && Objects.equals(card.getVersion(), req.getVersion() + 1)
                    && sameUpdatedDraft(card, snapshot, trialEndDate, layer1Json, layer2Json, formulaJson,
                    feasibilityJson, contentFormJson, complianceJson, professionalRisk)) {
                return new PositioningCardDraftRespVO(card.getId(), card.getVersion());
            }
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
        card.setTemplateVersionId(published.getId());
        card.setFieldsSnapshotJson(JsonUtils.toJsonString(snapshot.getFields()))
                .setValuesSnapshotJson(JsonUtils.toJsonString(snapshot.getValues()))
                .setDictSnapshotJson(JsonUtils.toJsonString(snapshot.getDictSnapshots()))
                .setTrialEndDate(trialEndDate).setLayer1Json(layer1Json).setLayer2Json(layer2Json)
                .setFormulaJson(formulaJson).setFeasibilityJson(feasibilityJson).setContentFormJson(contentFormJson)
                .setComplianceJson(complianceJson).setProfessionalRisk(professionalRisk)
                .setVersion(card.getVersion() + 1);
        if (mapper.updateDraftSnapshot(card, req.getVersion(), POSITIONING_CO_CREATING) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
        return new PositioningCardDraftRespVO(card.getId(), card.getVersion());
    }

    private boolean sameDraft(PositioningCardDO card, DirectorFormTemplateVO.Snapshot snapshot,
                              java.time.LocalDate trialEndDate, PositioningCardSaveReqVO req) {
        Map<String, Object> existingValues = StrUtil.isBlank(card.getValuesSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getValuesSnapshotJson(), Map.class);
        return Objects.equals(card.getTemplateVersionId(), snapshot.getTemplateVersionId())
                && Objects.equals(existingValues, snapshot.getValues())
                && Objects.equals(card.getTrialEndDate(), trialEndDate)
                && Objects.equals(card.getProfessionalRisk(), Boolean.TRUE.equals(req.getProfessionalRisk()))
                && Objects.equals(card.getLayer1Json(), jsonOrEmpty(req.getLayer1Json()))
                && Objects.equals(card.getLayer2Json(), jsonOrEmpty(req.getLayer2Json()))
                && Objects.equals(card.getFormulaJson(), jsonOrEmpty(req.getFormulaJson()))
                && Objects.equals(card.getFeasibilityJson(), jsonOrEmpty(req.getFeasibilityJson()))
                && Objects.equals(card.getContentFormJson(), jsonOrEmpty(req.getContentFormJson()))
                && Objects.equals(card.getComplianceJson(), jsonOrEmpty(req.getComplianceJson()));
    }

    private boolean sameUpdatedDraft(PositioningCardDO card, DirectorFormTemplateVO.Snapshot snapshot,
                                     java.time.LocalDate trialEndDate, String layer1Json, String layer2Json,
                                     String formulaJson, String feasibilityJson, String contentFormJson,
                                     String complianceJson, Boolean professionalRisk) {
        Map<String, Object> existingValues = StrUtil.isBlank(card.getValuesSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getValuesSnapshotJson(), Map.class);
        return Objects.equals(existingValues, snapshot.getValues())
                && Objects.equals(card.getTrialEndDate(), trialEndDate)
                && Objects.equals(card.getLayer1Json(), layer1Json)
                && Objects.equals(card.getLayer2Json(), layer2Json)
                && Objects.equals(card.getFormulaJson(), formulaJson)
                && Objects.equals(card.getFeasibilityJson(), feasibilityJson)
                && Objects.equals(card.getContentFormJson(), contentFormJson)
                && Objects.equals(card.getComplianceJson(), complianceJson)
                && Objects.equals(card.getProfessionalRisk(), professionalRisk);
    }

    private String draftJson(String requested, String existing) {
        return requested == null ? existing : jsonOrEmpty(requested);
    }

    private String jsonOrEmpty(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "submit-review")
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long id, Integer version, Long userId) {
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        PositioningCardDO card = tenantId == null ? mapper.selectById(id) : mapper.selectByIdForUpdate(id, tenantId);
        if (card == null) throw exception(POSITIONING_CARD_NOT_EXISTS);
        if (POSITIONING_OPERATOR_FEASIBILITY.equals(card.getStatus()) && Objects.equals(card.getVersion(), version + 1)) return;
        if (!POSITIONING_CO_CREATING.equals(card.getStatus())) throw exception(POSITIONING_CARD_STATE_INVALID);
        if (!Objects.equals(card.getVersion(), version)) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        assignmentService.requireMaster(card);
        var relation = tenantId == null ? relationMapper.selectById(card.getServiceRelationId())
                : relationMapper.selectByIdForUpdate(card.getServiceRelationId(), tenantId);
        if (relation == null || !"active".equals(relation.getStatus())
                || !"accepted".equals(relation.getAcceptanceStatus())
                || relation.getOperatorUserId() == null) {
            throw exception(POSITIONING_OPERATOR_REQUIRED);
        }
        AdminUserRespDTO assignedOperator = adminUserApi.getUser(relation.getOperatorUserId());
        if (assignedOperator == null || !CommonStatusEnum.ENABLE.getStatus().equals(assignedOperator.getStatus())) {
            throw exception(POSITIONING_OPERATOR_REQUIRED);
        }
        card.setOperatorUserId(relation.getOperatorUserId());
        if (card.getTemplateId() != null) {
            Map<String, Object> values = StrUtil.isBlank(card.getValuesSnapshotJson()) ? Map.of()
                    : JsonUtils.parseObject(card.getValuesSnapshotJson(), Map.class);
            Map<String, Object> dictSnapshots = StrUtil.isBlank(card.getDictSnapshotJson()) ? Map.of()
                    : JsonUtils.parseObject(card.getDictSnapshotJson(), Map.class);
            List<DirectorFormTemplateVO.Field> templateFields = StrUtil.isBlank(card.getFieldsSnapshotJson())
                    ? List.of() : JsonUtils.parseArray(card.getFieldsSnapshotJson(), DirectorFormTemplateVO.Field.class);
            List<String> missing = templateFields.stream().filter(f -> Boolean.TRUE.equals(f.getEnabled()))
                    .filter(f -> Boolean.TRUE.equals(f.getRequired()) && !"system_history".equals(f.getType()))
                    .filter(f -> emptyPositioningValue(values.get(f.getKey()), f.getType()))
                    .map(DirectorFormTemplateVO.Field::getKey).toList();
            if (!missing.isEmpty()) throw exception(POSITIONING_CARD_FIELD_REQUIRED);
            var activeKeys = templateFields.stream().filter(f -> Boolean.TRUE.equals(f.getEnabled()))
                    .map(DirectorFormTemplateVO.Field::getKey).collect(java.util.stream.Collectors.toSet());
            var activeValues = values.entrySet().stream().filter(entry -> activeKeys.contains(entry.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            directorFormTemplateService.validateAndSnapshotVersion(DirectorFormTemplateService.SCENE_POSITIONING,
                    card.getTemplateVersionId(), activeValues, true, dictSnapshots);
        }
        createSubmission(card, userId, POSITIONING_OPERATOR_FEASIBILITY);
        if (mapper.transitionWithOperator(card.getId(), version, POSITIONING_CO_CREATING,
                POSITIONING_OPERATOR_FEASIBILITY, card.getOperatorUserId()) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, userId, POSITIONING_CO_CREATING,
                POSITIONING_OPERATOR_FEASIBILITY, null, transitionKey(card, version,
                        POSITIONING_OPERATOR_FEASIBILITY));
        notifyOperatorReview(card, userId, version, "submitted");
    }

    private boolean emptyPositioningValue(Object value) {
        return emptyPositioningValue(value, null);
    }

    private boolean emptyPositioningValue(Object value, String fieldType) {
        if (value == null) return true;
        if (value instanceof String text) {
            if (text.isBlank()) return true;
            if (fieldType != null && Set.of("multi_select", "checkbox_group", "attachment", "material_picker", "region").contains(fieldType)) {
                String raw = text.trim();
                if (raw.startsWith("[") || raw.startsWith("{")) {
                    try {
                        Object parsed = raw.startsWith("[") ? JsonUtils.parseArray(raw, Object.class)
                                : JsonUtils.parseObject(raw, Map.class);
                        return emptyPositioningValue(parsed, null);
                    } catch (RuntimeException ignored) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (value instanceof java.util.Collection<?> collection) return collection.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value) == 0;
        // 前端动态表单会把 multi_select、checkbox_group、attachment 的值序列化为字符串。
        // 仅对这些结构化字段尝试解析，普通文本中的 "[]" 仍按文本处理。
        return false;
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "operator-confirm")
    @Transactional(rollbackFor = Exception.class)
    public void operatorApprove(Long id, Integer version) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_OPERATOR_FEASIBILITY);
        Long operator = currentAdminUserId();
        PositioningCardSubmissionDO submission = requireLatestSubmission(card, POSITIONING_OPERATOR_FEASIBILITY);
        requireAssignedOperator(card, submission, operator);
        LocalDateTime now = LocalDateTime.now();
        if (submissionMapper.markOperatorDecision(submission.getId(), submission.getVersion(),
                POSITIONING_OPERATOR_FEASIBILITY, POSITIONING_STUDENT_LINK_PENDING, operator, now, null) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
        transitionOperatorReview(card, version, POSITIONING_STUDENT_LINK_PENDING, operator, null);
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, operator, POSITIONING_OPERATOR_FEASIBILITY,
                POSITIONING_STUDENT_LINK_PENDING, null,
                transitionKey(card, version, POSITIONING_STUDENT_LINK_PENDING));
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "operator-reject")
    @Transactional(rollbackFor = Exception.class)
    public void operatorReject(Long id, Integer version, String reason) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_OPERATOR_FEASIBILITY);
        Long operator = currentAdminUserId();
        PositioningCardSubmissionDO submission = requireLatestSubmission(card, POSITIONING_OPERATOR_FEASIBILITY);
        requireAssignedOperator(card, submission, operator);
        if (submissionMapper.markOperatorDecision(submission.getId(), submission.getVersion(),
                POSITIONING_OPERATOR_FEASIBILITY, "operator_rejected", operator, LocalDateTime.now(), reason) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
        transitionOperatorReview(card, version, POSITIONING_CO_CREATING, operator, reason);
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, operator, POSITIONING_OPERATOR_FEASIBILITY,
                POSITIONING_CO_CREATING, reason, transitionKey(card, version, POSITIONING_CO_CREATING));
        workflowEventService.notify("media.positioning.operator_rejected", BIZ_TYPE_POSITIONING_CARD, id,
                card.getDirectorUserId(), operator, "positioning-operator-rejected:" + id + ":" + version,
                withReason(payload(card), reason));
    }

    /** Compatibility overload for existing internal callers; HTTP callers must supply a reason. */
    public void operatorReject(Long id, Integer version) {
        operatorReject(id, version, "未填写退回原因");
    }

    @Transactional(rollbackFor = Exception.class)
    public void studentConfirmFromLink(Long id, Integer version) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_STUDENT_CONFIRM);
        Map<String, Object> positioningValues = StrUtil.isBlank(card.getValuesSnapshotJson())
                ? Map.of() : JsonUtils.parseObject(card.getValuesSnapshotJson(), Map.class);
        if (positioningValues.isEmpty()) throw exception(POSITIONING_CARD_STATE_INVALID);
        transition(card, version, "student_evidence_pending");
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, null, POSITIONING_STUDENT_CONFIRM,
                "student_evidence_pending", null, transitionKey(card, version, "student_evidence_pending"));
        notifyEmployeeResult(card, "media.positioning.student_confirmed", version, "student_evidence_pending");
    }

    @Transactional(rollbackFor = Exception.class)
    public void studentRejectFromLink(Long id, Integer version, String reason) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_STUDENT_CONFIRM);
        transition(card, version, POSITIONING_CO_CREATING);
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, null, POSITIONING_STUDENT_CONFIRM,
                POSITIONING_CO_CREATING, reason, transitionKey(card, version, POSITIONING_CO_CREATING));
        notifyEmployeeResult(card, "media.positioning.student_rejected", version, POSITIONING_CO_CREATING);
    }

    /** Compatibility for non-HTTP internal callers while the Partner confirmation entry is retired. */
    public void studentConfirm(Long id, Integer version) {
        studentConfirmFromLink(id, version);
    }

    /** Compatibility for non-HTTP internal callers while the Partner confirmation entry is retired. */
    public void studentReject(Long id, Integer version) {
        studentRejectFromLink(id, version, "学员提出修改");
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "edit")
    @Transactional(rollbackFor = Exception.class)
    public PositioningCardDraftRespVO startRevision(Long id, Integer version, Long userId) {
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        PositioningCardDO card = mapper.selectByIdForUpdate(id, tenantId);
        if (card == null) throw exception(POSITIONING_CARD_NOT_EXISTS);
        if ((!POSITIONING_CONFIRMED.equals(card.getStatus()) && !POSITIONING_TRIAL_14D.equals(card.getStatus()) && !"student_evidence_pending".equals(card.getStatus()))
                || !Objects.equals(card.getVersion(), version)
                || !Objects.equals(card.getDirectorUserId(), userId)) {
            throw exception(POSITIONING_CARD_STATE_INVALID);
        }
        assignmentService.requireMaster(card);
        PositioningCardSubmissionDO effective = "student_evidence_pending".equals(card.getStatus())
                ? submissionMapper.selectLatestByCard(card.getId()) : submissionMapper.selectLatestConfirmedByCard(card.getId());
        PositioningCardSubmissionDO latest = submissionMapper.selectLatestByCard(card.getId());
        if (effective == null || latest == null || !Objects.equals(effective.getId(), latest.getId())
                || !Objects.equals(effective.getCardId(), card.getId())) {
            throw exception(POSITIONING_CARD_STATE_INVALID);
        }
        if ("student_evidence_pending".equals(card.getStatus()) && submissionMapper.markStatus(effective.getId(), effective.getVersion(),
                "student_evidence_pending", "evidence_revision_closed") != 1) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        if (mapper.startRevision(card, effective, version) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, userId, card.getStatus(),
                POSITIONING_CO_CREATING, null, transitionKey(card, version, POSITIONING_CO_CREATING));
        return new PositioningCardDraftRespVO(id, version + 1);
    }

    public PositioningCardDO require(Long id) {
        PositioningCardDO card = mapper.selectById(id);
        if (card == null) throw exception(POSITIONING_CARD_NOT_EXISTS);
        return card;
    }

    int advanceVersionNo(Long id, Integer expectedVersion, Integer expectedVersionNo) {
        return mapper.advanceVersionNo(id, expectedVersion, expectedVersionNo, expectedVersionNo + 1);
    }

    int advanceVersionNoWithoutTenant(Long id, Integer expectedVersion, Integer expectedVersionNo) {
        return mapper.advanceVersionNo(id, expectedVersion, expectedVersionNo, expectedVersionNo + 1);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "read")
    public PositioningCardRespVO get(Long id, Long userId) {
        return toResp(require(id), userId);
    }

    /**
     * IP（专业）审核流程的回调。
     *
     * <p><b>该分支已按决策退役，不是待修的缺口。</b>提交时不再因 {@code professionalRisk}
     * 进入 {@code ip_review}，而是直接进入 {@code operator_feasibility}；本方法与
     * {@link PositioningIpProcessStatusListener}、BPMN 资产
     * （{@code script/bpm/zsjos_media_positioning_ip/}）一并保留，只为让**已在途的实例**
     * 能正常跑完。决策记录见 handoff/main.md（"the IP listener and process asset remain
     * only for already-running instances"）。
     *
     * <p>因此 {@code selectByIpProcessId} 查不到 card 而在此提前返回是**预期行为**：
     * 没有新实例会写入 {@code ipProcessInstanceId}。新增提交若需要审批，应走运营审核分支，
     * 不要在这里接线。
     */
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "positioning-card.process-result", targetType = "positioning-card")
    @Transactional(rollbackFor = Exception.class)
    public void handleIpProcessResult(String processId, Integer status, String reason) {
        PositioningCardDO card = mapper.selectByIpProcessId(processId);
        if (card == null || !POSITIONING_IP_REVIEW.equals(card.getStatus())) return;
        String target = BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(status) ? POSITIONING_OPERATOR_FEASIBILITY
                : BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(status) ? POSITIONING_CO_CREATING : null;
        if (target != null) {
            if (mapper.transition(card.getId(), card.getVersion(), POSITIONING_IP_REVIEW, target) == 0) {
                throw exception(POSITIONING_CARD_VERSION_CONFLICT);
            }
            PositioningCardSubmissionDO submission = requireLatestSubmission(card, POSITIONING_IP_REVIEW);
            String submissionTarget = POSITIONING_OPERATOR_FEASIBILITY.equals(target)
                    ? POSITIONING_OPERATOR_FEASIBILITY : "ip_rejected";
            if (submissionMapper.markStatus(submission.getId(), submission.getVersion(), POSITIONING_IP_REVIEW,
                    submissionTarget) == 0) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
            workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, card.getId(), card.getIpReviewerUserId(),
                    POSITIONING_IP_REVIEW, target, reason, transitionKey(card, card.getVersion(), target));
            String scene = POSITIONING_OPERATOR_FEASIBILITY.equals(target)
                    ? "media.positioning.ip_approved" : "media.positioning.ip_rejected";
            workflowEventService.notify(scene, BIZ_TYPE_POSITIONING_CARD, card.getId(), card.getDirectorUserId(),
                    card.getIpReviewerUserId(), "positioning-ip-result:" + card.getId() + ":" + card.getVersion()
                            + ":" + target, withReason(payload(card), reason));
            if (POSITIONING_OPERATOR_FEASIBILITY.equals(target)) {
                notifyOperatorReview(card, card.getIpReviewerUserId(), card.getVersion(), "ip-approved");
            }
        }
    }

    private void notifyOperatorReview(PositioningCardDO card, Long operator, Integer version, String branch) {
        Long operatorUserId = card.getOperatorUserId();
        if (operatorUserId == null) return;
        workflowEventService.notify("media.positioning.operator_review", BIZ_TYPE_POSITIONING_CARD, card.getId(),
                operatorUserId, operator,
                "positioning-operator-review:" + card.getId() + ":" + version + ":" + branch, payload(card));
    }

    private void notifyEmployeeResult(PositioningCardDO card, String scene, Integer version, String target) {
        workflowEventService.notify(scene, BIZ_TYPE_POSITIONING_CARD, card.getId(), card.getDirectorUserId(), null,
                "positioning-student-result:" + card.getId() + ":" + version + ":director:" + target,
                payload(card));
        Long operatorUserId = card.getOperatorUserId();
        if (operatorUserId != null && !operatorUserId.equals(card.getDirectorUserId())) {
            workflowEventService.notify(scene, BIZ_TYPE_POSITIONING_CARD, card.getId(),
                    operatorUserId, null,
                    "positioning-student-result:" + card.getId() + ":" + version + ":operator:" + target,
                    payload(card));
        }
    }

    private Map<String, Object> payload(PositioningCardDO card) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("bizNo", card.getCardNo());
        if (card.getStudentPersonId() != null) {
            values.put("deepLink", "/zsjos/media-students?personId=" + card.getStudentPersonId()
                    + "&serviceRelationId=" + card.getServiceRelationId() + "&tab=positioning&positioningCardId=" + card.getId());
        }
        return values;
    }

    private Map<String, Object> withReason(Map<String, Object> payload, String reason) {
        if (reason == null || reason.isBlank()) return payload;
        Map<String, Object> values = new java.util.LinkedHashMap<>(payload);
        values.put("reason", reason);
        return values;
    }

    private String transitionKey(PositioningCardDO card, Integer version, String target) {
        return "positioning:" + card.getId() + ":" + version + ":" + target;
    }

    private Long currentAdminUserId() {
        return cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
    }

    private void requireStatus(PositioningCardDO card, String status) {
        if (!status.equals(card.getStatus())) throw exception(POSITIONING_CARD_STATE_INVALID);
    }

    private void transition(PositioningCardDO card, Integer version, String target) {
        if (mapper.transition(card.getId(), version, card.getStatus(), target) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
    }

    private void transitionOperatorReview(PositioningCardDO card, Integer version, String target,
                                          Long operatorUserId, String comment) {
        if (mapper.transitionOperatorReview(card.getId(), version, card.getStatus(), target, operatorUserId,
                LocalDateTime.now(), comment) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
    }

    @ZsjosPermission(bizType = "media-account", bizId = "#accountId", action = "read")
    public cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningAccountOverviewRespVO accountOverview(
            Long accountId, Long userId) {
        var account = accountMapper.selectById(accountId);
        if (account == null) throw exception(POSITIONING_REFERENCE_INVALID);
        var confirmed = submissionMapper.selectCurrentConfirmedByAccount(accountId);
        var effective = confirmed == null ? null : submissionResponse(confirmed, userId);
        return new cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningAccountOverviewRespVO(null, effective, List.of());
    }

    @ZsjosPermission(bizType="student-service", bizId="#serviceRelationId", action="read")
    public cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningServiceOverviewRespVO serviceOverview(Long serviceRelationId, Long userId) {
        var relation = relationMapper.selectById(serviceRelationId);
        if (relation == null) throw exception(POSITIONING_REFERENCE_INVALID);
        var cards = mapper.selectByService(serviceRelationId);
        Long masterId = assignmentService.masterId(serviceRelationId);
        var master = cards.stream().filter(row -> Objects.equals(row.getId(), masterId)).findFirst().orElse(null);
        boolean director = Objects.equals(relation.getContentDirectorUserId(), userId);
        boolean createPermission = permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:create");
        var history = submissionMapper.selectByService(serviceRelationId).stream()
                .filter(row -> objectPermissionProvider.hasPermission(row.getCardId(), "read", userId))
                .map(row -> submissionResponse(row, userId)).toList();
        var effective = masterId == null ? null : submissionMapper.selectLatestConfirmedByCard(masterId);
        return new cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningServiceOverviewRespVO(serviceRelationId, masterId,
                masterId == null && !cards.isEmpty() && director && createPermission && "active".equals(relation.getStatus())
                        && "accepted".equals(relation.getAcceptanceStatus()) && isPositioningComplete(relation.getDirectorStage()),
                cards.isEmpty() && director && createPermission && "active".equals(relation.getStatus())
                        && "accepted".equals(relation.getAcceptanceStatus()) && isPositioningComplete(relation.getDirectorStage()),
                director && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:submit-review") && relation.getOperatorUserId() != null,
                cards.stream().filter(row -> objectPermissionProvider.hasPermission(row.getId(), "read", userId)).map(row -> toResp(row, userId)).toList(),
                master == null || !objectPermissionProvider.hasPermission(master.getId(), "read", userId) ? null : toResp(master, userId),
                effective == null || !objectPermissionProvider.hasPermission(effective.getCardId(), "read", userId) ? null : submissionResponse(effective, userId), history);
    }

    @ZsjosPermission(bizType="media-account", bizId="#accountId", action="read")
    public cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningApplicationRespVO applicationOptions(Long accountId, Long userId) {
        var current = assignmentService.application(accountId);
        var candidates = assignmentService.candidates(accountId).stream()
                .filter(row -> objectPermissionProvider.hasPermission(row.getCardId(), "read", userId))
                .map(row -> submissionResponse(row, userId)).toList();
        boolean newer = current != null && !candidates.isEmpty() && !Objects.equals(candidates.getFirst().getSubmissionId(), current.getSubmissionId());
        return new cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningApplicationRespVO(current == null ? 0 : current.getVersion(),
                current == null ? null : current.getSubmissionId(), assignmentService.canApply(accountId, userId), newer, candidates);
    }

    public PositioningCardRespVO submissionResponse(PositioningCardSubmissionDO row, Long userId) {
        objectPermissionProvider.check(row.getCardId(), "read", userId);
        var response = BeanUtils.toBean(row, PositioningCardRespVO.class);
        response.setId(row.getCardId()); response.setSubmissionId(row.getId());
        var sourceCard = mapper.selectById(row.getCardId());
        if (sourceCard != null) response.setCardNo(sourceCard.getCardNo());
        response.setFieldsSnapshot(StrUtil.isBlank(row.getFieldsSnapshotJson()) ? List.of()
                : JsonUtils.parseArray(row.getFieldsSnapshotJson(), Object.class));
        response.setValuesSnapshot(StrUtil.isBlank(row.getValuesSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(row.getValuesSnapshotJson(), Map.class));
        response.setDictSnapshot(StrUtil.isBlank(row.getDictSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(row.getDictSnapshotJson(), Map.class));
        enrichSubmission(response, row, userId);
        response.setAvailableActions(List.of());
        return response;
    }

    private PositioningCardRespVO toResp(PositioningCardDO card, Long userId) {
        objectPermissionProvider.check(card.getId(), "read", userId);
        PositioningCardRespVO response = BeanUtils.toBean(card, PositioningCardRespVO.class);
        response.setFieldsSnapshot(StrUtil.isBlank(card.getFieldsSnapshotJson()) ? List.of()
                : JsonUtils.parseArray(card.getFieldsSnapshotJson(), Object.class));
        response.setValuesSnapshot(StrUtil.isBlank(card.getValuesSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getValuesSnapshotJson(), Map.class));
        response.setDictSnapshot(StrUtil.isBlank(card.getDictSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getDictSnapshotJson(), Map.class));
        PositioningCardSubmissionDO submission = submissionMapper.selectLatestByCard(card.getId());
        if (submission != null) {
            enrichSubmission(response, submission, userId);
            response.setSubmissionNo(submission.getSubmissionNo());
            response.setSubmittedAt(submission.getSubmittedAt());
            response.setStudentDecisionComment(submission.getStudentDecisionComment());
        }
        response.setAvailableActions(availableActionsForVisible(card, userId));
        return response;
    }

    private void enrichSubmission(PositioningCardRespVO response, PositioningCardSubmissionDO row, Long userId) {
        response.setSubmissionId(row.getId()); response.setSubmissionVersion(row.getVersion());
        response.setSubmissionStatus(row.getStatus()); response.setStudentDecidedAt(row.getStudentDecidedAt());
        response.setOperatorReviewedAt(row.getOperatorReviewedAt()); response.setOperatorReviewComment(row.getOperatorReviewComment());
        response.setEvidenceRequired(Boolean.TRUE.equals(row.getEvidenceRequired()));
        response.setEvidence(PositioningEvidenceService.evidence(row));
        response.setCanUploadEvidence(permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:evidence")
                && objectPermissionProvider.hasPermission(row.getCardId(), "evidence", userId)
                && (PositioningEvidenceService.eligible(row) || "student_evidence_pending".equals(row.getStatus())));
        var director = row.getDirectorUserId() == null ? null : adminUserApi.getUser(row.getDirectorUserId());
        var operator = row.getOperatorUserId() == null ? null : adminUserApi.getUser(row.getOperatorUserId());
        response.setDirectorName(director == null ? null : director.getNickname());
        response.setOperatorName(operator == null ? null : operator.getNickname());
    }

    public List<String> availableActionsForVisible(PositioningCardDO card, Long userId) {
        List<String> actions = new java.util.ArrayList<>();
        if (!Objects.equals(assignmentService.masterId(card.getServiceRelationId()), card.getId())) return actions;
        if (POSITIONING_CO_CREATING.equals(card.getStatus())
                && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:create")
                && objectPermissionProvider.hasPermission(card.getId(), "submit-review", userId)) actions.add("EDIT_POSITIONING_DRAFT");
        if (POSITIONING_CO_CREATING.equals(card.getStatus())
                && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:submit-review")
                && objectPermissionProvider.hasPermission(card.getId(), "submit-review", userId)) {
            actions.add(ACTION_SUBMIT_POSITIONING_REVIEW);
        } else if (POSITIONING_OPERATOR_FEASIBILITY.equals(card.getStatus())) {
            if (permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:operator-confirm")
                    && objectPermissionProvider.hasPermission(card.getId(), "operator-confirm", userId)) {
                actions.add(ACTION_APPROVE_POSITIONING_FEASIBILITY);
            }
            if (permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:operator-reject")
                    && objectPermissionProvider.hasPermission(card.getId(), "operator-reject", userId)) {
                actions.add(ACTION_REJECT_POSITIONING_FEASIBILITY);
            }
        } else if ((POSITIONING_STUDENT_LINK_PENDING.equals(card.getStatus())
                || POSITIONING_STUDENT_CONFIRM.equals(card.getStatus()))
                && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:student-link-generate")
                && objectPermissionProvider.hasPermission(card.getId(), "student-link-generate", userId)) {
            actions.add(ACTION_GENERATE_POSITIONING_STUDENT_LINK);
        } else if ((POSITIONING_CONFIRMED.equals(card.getStatus()) || POSITIONING_TRIAL_14D.equals(card.getStatus()) || "student_evidence_pending".equals(card.getStatus()))
                && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:edit")
                && objectPermissionProvider.hasPermission(card.getId(), "edit", userId)) {
            actions.add(ACTION_START_POSITIONING_REVISION);
        }
        return actions;
    }

    private PositioningCardSubmissionDO createSubmission(PositioningCardDO card, Long userId, String status) {
        PositioningCardSubmissionDO latest = submissionMapper.selectLatestByCard(card.getId());
        PositioningCardSubmissionDO submission = new PositioningCardSubmissionDO();
        submission.setEvidenceRequired(true);
        submission.setCardId(card.getId()).setAccountId(card.getAccountId())
                .setStudentPersonId(card.getStudentPersonId()).setServiceRelationId(card.getServiceRelationId())
                .setSubmissionNo(latest == null ? 1 : latest.getSubmissionNo() + 1)
                .setDirectorUserId(userId).setOperatorUserId(card.getOperatorUserId())
                .setTemplateId(card.getTemplateId()).setTemplateVersionId(card.getTemplateVersionId())
                .setFieldsSnapshotJson(card.getFieldsSnapshotJson()).setValuesSnapshotJson(card.getValuesSnapshotJson())
                .setDictSnapshotJson(card.getDictSnapshotJson()).setLayer1Json(card.getLayer1Json())
                .setLayer2Json(card.getLayer2Json()).setFormulaJson(card.getFormulaJson())
                .setFeasibilityJson(card.getFeasibilityJson()).setContentFormJson(card.getContentFormJson())
                .setComplianceJson(card.getComplianceJson()).setTrialEndDate(card.getTrialEndDate())
                .setProfessionalRisk(card.getProfessionalRisk()).setStatus(status)
                .setSubmittedAt(LocalDateTime.now()).setVersion(0);
        submissionMapper.insert(submission);
        return submission;
    }

    public PositioningCardSubmissionDO requireLatestSubmission(PositioningCardDO card, String expectedStatus) {
        PositioningCardSubmissionDO submission = submissionMapper.selectLatestByCard(card.getId());
        if (submission == null) throw exception(POSITIONING_SUBMISSION_NOT_EXISTS);
        if (!expectedStatus.equals(submission.getStatus())) throw exception(POSITIONING_CARD_STATE_INVALID);
        return submission;
    }

    private void requireAssignedOperator(PositioningCardDO card, PositioningCardSubmissionDO submission,
                                         Long operatorUserId) {
        if (!Objects.equals(card.getOperatorUserId(), operatorUserId)
                || !Objects.equals(submission.getOperatorUserId(), operatorUserId)) {
            throw exception(POSITIONING_CARD_PERMISSION_DENIED);
        }
    }

    /**
     * Compatibility overload for older callers that already performed an object
     * visibility check. The boolean is only a read gate; action authorization
     * remains evaluated per action by the two-argument implementation.
     */
    public List<String> availableActionsForVisible(PositioningCardDO card, Long userId,
                                                   boolean objectAuthorized) {
        return objectAuthorized ? availableActionsForVisible(card, userId) : List.of();
    }
}
