package cn.iocoder.yudao.module.zsjos.service.account;

import static cn.iocoder.yudao.module.zsjos.enums.MediaNotificationScenes.*;

import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountStudentLinkDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioninginterview.PositioningInterviewMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountMaintenanceProblemVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.service.delivery.StudentDeliveryPlanService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi; import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO; import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountStudentCandidateRespVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PERSONA_TYPE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PROFESSION;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
@Slf4j
public class MediaAccountService {
    @Resource private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
    @Resource private MediaAccountMapper mapper;
    @Resource private MediaAccountStudentLinkMapper linkMapper;
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private PositioningInterviewMapper positioningInterviewMapper;
    @Resource private MediaAccountNumberService numberService;
    @Resource private PermissionApi permissionApi;
    @Resource private MediaAccountObjectPermissionProvider objectPermissionProvider;
    @Resource private MediaDataScopeService dataScopeService;
    @Resource private PersonMapper personMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private MediaWorkflowEventService workflowEventService;
    @Resource private MediaAccountFieldConfigService fieldConfigService;
    @Resource private DictDataApi dictDataApi;
    @Resource private StudentDeliveryPlanService studentDeliveryPlanService;
    @Resource private PositioningCardMapper positioningCardMapper;

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#req.serviceRelationId", action = "create-account")
    public Long create(MediaAccountSaveReqVO req, Long userId) {
        ServiceRelationDO relation = relationMapper.selectByIdForUpdate(req.getServiceRelationId(),
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
        validateCreateRelation(relation, req, userId);
        String fingerprint = createFingerprint(req, userId);
        MediaAccountDO replay = mapper.selectByCreateIdempotencyKey(req.getIdempotencyKey());
        if (replay != null) return validateCreateReplay(replay, req, userId, fingerprint);
        if (!Objects.equals(relation.getVersion(), req.getVersion())) {
            throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        }
        if (java.util.stream.Stream.of(req.getPlatformValue(), req.getNickname(), req.getPlatformAccountId(),
                req.getLeadDirection(), req.getAccountTypePrimaryValue(), req.getAccountTypeSecondaryValue(),
                req.getTrackPrimaryValue(), req.getTrackSecondaryValue()).anyMatch(value -> value != null && !value.isBlank())
                || req.getDetailValues() != null && !req.getDetailValues().isEmpty()) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
        validateStudent(req.getStudentPersonId());
        Long assignedOperator = relation.getOperatorUserId();
        Long directorUserId = relation.getContentDirectorUserId();
        adminUserApi.validateUser(directorUserId);
        Map<String, Object> detailValues = mergeCompatibilityValues(
                req.getDetailValues(), req.getPlatformAccountId(), req.getNickname());
        MediaAccountFieldConfigService.DetailSnapshot details = fieldConfigService.validateAndSnapshot(detailValues);
        String uid = stringValue(details.values().get("uid"), req.getPlatformAccountId());
        String nickname = stringValue(details.values().get("nickname"), req.getNickname());
        MediaAccountDO account = new MediaAccountDO();
        account.setAccountNo(numberService.next()).setStudentPersonId(req.getStudentPersonId())
                .setOwnershipType(req.getStudentPersonId() == null ? OWNERSHIP_COMPANY : OWNERSHIP_STUDENT)
                .setOwnerOperatorUserId(assignedOperator).setDirectorUserId(directorUserId)
                .setPlatformValue(req.getPlatformValue() == null || req.getPlatformValue().isBlank() ? null : req.getPlatformValue().trim()).setPlatformLabelSnapshot(optionalPlatformLabel(req.getPlatformValue()))
                .setPlatformAccountId(uid).setNickname(nickname)
                .setDetailConfigVersionId(details.configVersionId())
                .setDetailValuesJson(JsonUtils.toJsonString(details.values()))
                .setDetailSnapshotJson(JsonUtils.toJsonString(details.snapshots()))
                .setLeadDirection(req.getLeadDirection()).setSStage(null)
                .setSStageEnteredAt(null).setIsSilent(false).setRunStatus(RUN_STATUS_ACTIVE)
                .setCreateServiceRelationId(relation.getId()).setCreateOperatorUserId(userId)
                .setCreateIdempotencyKey(req.getIdempotencyKey()).setCreateRequestFingerprint(fingerprint)
                .setRescueStatus("none").setWhitelistStatus("none").setVersion(0);
        setRecommendationProfile(account, req.getAccountTypePrimaryValue(), req.getAccountTypeSecondaryValue(),
                req.getTrackPrimaryValue(), req.getTrackSecondaryValue());
        try {
            mapper.insert(account);
            studentDeliveryPlanService.ensurePlan(account.getStudentPersonId(), account.getId(), relation.getId(),
                    directorUserId, java.time.LocalDateTime.now());
            collaborationNotify.account(MEDIA_ACCOUNT_CREATED, account, userId,
                    "media-account-created:" + account.getId(), java.util.Map.of());
            return account.getId();
        } catch (DuplicateKeyException duplicate) {
            MediaAccountDO concurrentReplay = mapper.selectByCreateIdempotencyKey(req.getIdempotencyKey());
            if (concurrentReplay == null) throw duplicate;
            return validateCreateReplay(concurrentReplay, req, userId, fingerprint);
        }
    }

    private void validateCreateRelation(ServiceRelationDO relation, MediaAccountSaveReqVO req, Long userId) {
        if (relation == null || !Objects.equals(relation.getPersonId(), req.getStudentPersonId())
                || !"active".equals(relation.getStatus()) || !"accepted".equals(relation.getAcceptanceStatus())
                || relation.getContentDirectorUserId() == null
                || !(Objects.equals(relation.getContentDirectorUserId(), userId)
                     || Objects.equals(relation.getOperatorUserId(), userId))
                || req.getDirectorUserId() != null && !Objects.equals(req.getDirectorUserId(), relation.getContentDirectorUserId())) {
            throw exception(MEDIA_ACCOUNT_SERVICE_INVALID);
        }
        if (!Set.of("positioning_interview_completed", "positioning_ready").contains(relation.getDirectorStage())
                && !positioningInterviewMapper.existsCompleted(relation.getId(), req.getStudentPersonId())) {
            throw exception(MEDIA_ACCOUNT_POSITIONING_INCOMPLETE);
        }
    }

    private String createFingerprint(MediaAccountSaveReqVO req, Long userId) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(
                req.getServiceRelationId(), req.getStudentPersonId(), req.getVersion(), userId, req.getPlatformValue(),
                req.getPlatformAccountId(), req.getNickname(), req.getLeadDirection(),
                req.getAccountTypePrimaryValue(), req.getAccountTypeSecondaryValue(),
                req.getTrackPrimaryValue(), req.getTrackSecondaryValue(), canonicalize(req.getDetailValues()))));
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::canonicalize).toList();
        }
        return value;
    }

    private Long validateCreateReplay(MediaAccountDO replay, MediaAccountSaveReqVO req, Long userId,
                                      String fingerprint) {
        if (!Objects.equals(replay.getCreateServiceRelationId(), req.getServiceRelationId())
                || !Objects.equals(replay.getStudentPersonId(), req.getStudentPersonId())
                || !Objects.equals(replay.getCreateOperatorUserId(), userId)
                || !Objects.equals(replay.getCreateRequestFingerprint(), fingerprint)) {
            throw exception(MEDIA_ACCOUNT_CREATE_IDEMPOTENCY_CONFLICT);
        }
        return replay.getId();
    }

    public MediaAccountDO require(Long id) {
        MediaAccountDO account = mapper.selectById(id);
        if (account == null) throw exception(MEDIA_ACCOUNT_NOT_EXISTS);
        return account;
    }
    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#id", action = "read")
    public MediaAccountRespVO get(Long id, Long userId) {
        return toResp(require(id), userId);
    }

    /** Builds the account projection after the enclosing student scope has already been authorized. */
    public MediaAccountRespVO projectStudentReadOnly(MediaAccountDO account) {
        MediaAccountRespVO response = BeanUtils.toBean(account, MediaAccountRespVO.class);
        response.setAvailableActions(List.of());
        response.setDetailValues(account.getDetailValuesJson() == null ? Map.of()
                : JsonUtils.parseObject(account.getDetailValuesJson(), Map.class));
        response.setDetailSnapshots(account.getDetailSnapshotJson() == null ? List.of()
                : JsonUtils.parseArray(account.getDetailSnapshotJson(), MediaAccountDetailSnapshotVO.class));
        response.setPrimaryProblems(account.getPrimaryProblemsJson() == null ? List.of()
                : JsonUtils.parseArray(account.getPrimaryProblemsJson(), MediaAccountMaintenanceProblemVO.class));
        return response;
    }

    /** Projects only actions that pass both account relationship and feature-permission checks. */
    public List<String> availableActionsForVisible(MediaAccountDO account, Long userId) {
        return toResp(account, userId).getAvailableActions();
    }

    public PageResult<MediaAccountRespVO> page(MediaAccountPageReqVO req, Long userId) {
        MediaDataScopeService.Scope scope = dataScopeService.resolve(userId, "zsjos:media-account:query-all");
        PageResult<MediaAccountDO> page = mapper.selectPage(req, scope.userIds(), scope.all());
        return new PageResult<>(page.getList().stream().map(row -> toResp(row, userId)).toList(), page.getTotal());
    }

    public List<MediaAccountStudentCandidateRespVO> studentCandidates(String keyword, Long userId) {
        var query = new LambdaQueryWrapperX<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO>()
                .likeIfPresent(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO::getName,
                        keyword == null ? null : keyword.trim())
                .orderByDesc(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO::getId)
                .last("LIMIT 100");
        if (!permissionApi.hasAnyPermissions(userId, "zsjos:media-account:query-all")) {
            List<Long> visibleStudentIds = mapper.selectVisibleStudentIds(userId,
                    keyword == null ? null : keyword.trim(),
                    cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId());
            if (visibleStudentIds.isEmpty()) return List.of();
            query.in(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO::getId, visibleStudentIds);
        }
        return personMapper.selectList(query).stream().map(person -> {
            var response = new MediaAccountStudentCandidateRespVO();
            response.setPersonId(person.getId());
            response.setName(person.getName());
            return response;
        }).toList();
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#id", action = "edit")
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MediaAccountUpdateReqVO req, Long userId) {
        // Old whole-object writes cannot express field responsibility or safe partial updates.
        throw exception(MEDIA_ACCOUNT_PROFILE_UPGRADE_REQUIRED);
    }

    private void patchRecommendationProfile(MediaAccountDO account, MediaAccountUpdateReqVO request) {
        if (request.getAccountTypePrimaryValue() != null) {
            DictSelection selection = resolveDictSelection(DICT_PERSONA_TYPE, request.getAccountTypePrimaryValue());
            account.setAccountTypePrimaryValue(selection.value())
                    .setAccountTypePrimaryLabelSnapshot(selection.label());
        }
        if (request.getAccountTypeSecondaryValue() != null) {
            DictSelection selection = resolveDictSelection(DICT_PERSONA_TYPE, request.getAccountTypeSecondaryValue());
            account.setAccountTypeSecondaryValue(selection.value())
                    .setAccountTypeSecondaryLabelSnapshot(selection.label());
        }
        if (request.getTrackPrimaryValue() != null) {
            DictSelection selection = resolveDictSelection(DICT_PROFESSION, request.getTrackPrimaryValue());
            account.setTrackPrimaryValue(selection.value()).setTrackPrimaryLabelSnapshot(selection.label());
        }
        if (request.getTrackSecondaryValue() != null) {
            DictSelection selection = resolveDictSelection(DICT_PROFESSION, request.getTrackSecondaryValue());
            account.setTrackSecondaryValue(selection.value()).setTrackSecondaryLabelSnapshot(selection.label());
        }
    }

    private void setRecommendationProfile(MediaAccountDO account, String accountTypePrimary,
                                          String accountTypeSecondary, String trackPrimary,
                                          String trackSecondary) {
        DictSelection primaryType = resolveDictSelection(DICT_PERSONA_TYPE, accountTypePrimary);
        DictSelection secondaryType = resolveDictSelection(DICT_PERSONA_TYPE, accountTypeSecondary);
        DictSelection primaryTrack = resolveDictSelection(DICT_PROFESSION, trackPrimary);
        DictSelection secondaryTrack = resolveDictSelection(DICT_PROFESSION, trackSecondary);
        account.setAccountTypePrimaryValue(primaryType.value())
                .setAccountTypePrimaryLabelSnapshot(primaryType.label())
                .setAccountTypeSecondaryValue(secondaryType.value())
                .setAccountTypeSecondaryLabelSnapshot(secondaryType.label())
                .setTrackPrimaryValue(primaryTrack.value())
                .setTrackPrimaryLabelSnapshot(primaryTrack.label())
                .setTrackSecondaryValue(secondaryTrack.value())
                .setTrackSecondaryLabelSnapshot(secondaryTrack.label());
    }

    private DictSelection resolveDictSelection(String dictType, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return new DictSelection(null, null);
        String value = rawValue.trim();
        dictDataApi.validateDictDataList(dictType, List.of(value));
        String label = dictDataApi.getDictDataList(dictType).stream()
                .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .filter(item -> java.util.Objects.equals(item.getValue(), value))
                .map(DictDataRespDTO::getLabel).findFirst()
                .orElseThrow(() -> exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID));
        return new DictSelection(value, label);
    }

    private record DictSelection(String value, String label) {
    }

    private String requirePlatformLabel(String value) {
        dictDataApi.validateDictDataList("zsjos_account_platform", List.of(value));
        return dictDataApi.getDictDataList("zsjos_account_platform").stream()
                .filter(item -> java.util.Objects.equals(item.getValue(), value))
                .map(DictDataRespDTO::getLabel).findFirst()
                .orElseThrow(() -> exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID));
    }

    private void validateDetailFieldOwnership(MediaAccountDO account, Map<String, Object> requested, Long userId) {
        var published = fieldConfigService.getPublished();
        if (published == null || published.getFields() == null) return;
        for (var field : published.getFields()) {
            if (!requested.containsKey(field.getKey())) continue;
            String owner = field.getOwnerType();
            if ("AUTO".equals(owner) || "DIRECTOR".equals(owner) && !Objects.equals(account.getDirectorUserId(), userId)
                    || "OPERATOR".equals(owner) && !Objects.equals(account.getOwnerOperatorUserId(), userId)) {
                throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
            }
        }
    }

    private String optionalPlatformLabel(String value) {
        return value == null || value.isBlank() ? null : requirePlatformLabel(value);
    }

    private void requireSnapshotValueUnchanged(String storedValue, String requestedValue) {
        if (requestedValue != null && !java.util.Objects.equals(storedValue, requestedValue)) {
            // These legacy fields have no configured dictionary type; accepting a label would make the client authoritative.
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#id", action = "rescue")
    @Transactional(rollbackFor = Exception.class)
    public void updateRescue(Long id, Integer version, String status) {
        require(id); if (!List.of("none", "in_progress", "recovered", "failed").contains(status)) throw exception(MEDIA_ACCOUNT_STATE_INVALID);
        if (mapper.updateRescue(id, version, status) == 0) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#id", action = "rebind")
    @Transactional(rollbackFor = Exception.class)
    public String requestRebind(Long id, Long targetStudentId, Integer version, Long userId) {
        MediaAccountDO account = require(id); validateStudent(targetStudentId);
        if ("pending".equals(account.getRebindStatus())) return account.getRebindProcessInstanceId();
        Long reviewer = requireSupervisor(userId);
        if (mapper.claimRebind(id, version, targetStudentId, userId, reviewer) == 0) {
            throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        }
        BpmProcessInstanceCreateReqDTO req = new BpmProcessInstanceCreateReqDTO(); req.setProcessDefinitionKey(PROCESS_KEY_REBIND);
        req.setBusinessKey("media-rebind:"+id+":v"+version); req.setVariables(new java.util.HashMap<>(Map.of("accountId",id,"fromStudentId",account.getStudentPersonId()==null?0L:account.getStudentPersonId(),"toStudentId",targetStudentId,"assignee",reviewer,"coll_userList",List.of(reviewer))));
        req.setStartUserSelectAssignees(Map.of("managerReviewer", List.of(reviewer)));
        String processId;
        try {
            processId = processInstanceApi.createProcessInstance(userId, req);
        } catch (RuntimeException ex) {
            log.warn("Failed to start media account rebind process: {}: {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw exception(MEDIA_REBIND_PROCESS_UNAVAILABLE);
        }
        if (mapper.finishRebind(id, version + 1, processId) == 0) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        workflowEventService.transition(BIZ_TYPE_MEDIA_ACCOUNT, id, userId, "rebind_starting", "rebind_pending",
                null, "media-account-rebind-submitted:" + id + ":" + processId);
        return processId;
    }

    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "media-account.rebind-process-result", targetType = "media-account")
    @Transactional(rollbackFor = Exception.class)
    public void handleRebindProcessResult(String processInstanceId, Integer processStatus, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) return;
        MediaAccountDO account = mapper.selectByRebindProcessInstanceId(processInstanceId);
        if (account == null || !"pending".equals(account.getRebindStatus())) return;
        boolean approved = BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus);
        boolean rejected = BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus);
        if (!approved && !rejected) return;
        String targetStatus = approved ? "approved" : "rejected";
        Long targetStudentId = approved ? account.getRebindTargetStudentPersonId() : null;
        if (approved && targetStudentId == null) throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);

        LocalDateTime now = LocalDateTime.now();
        if (approved) {
            MediaAccountStudentLinkDO active = linkMapper.selectActiveByAccountId(account.getId());
            if (active != null) {
                active.setStatus("ended").setEndedAt(now).setReason("rebind-approved")
                        .setOperatedByUserId(account.getRebindReviewerUserId());
                linkMapper.updateById(active);
            }
            linkMapper.insert(new MediaAccountStudentLinkDO().setAccountId(account.getId())
                    .setStudentPersonId(targetStudentId).setStatus("active").setReason("rebind-approved")
                    .setStartedAt(now).setOperatedByUserId(account.getRebindReviewerUserId()));
        }
        if (mapper.completeRebind(account.getId(), account.getVersion(), processInstanceId, targetStatus, reason,
                targetStudentId) == 0) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        workflowEventService.transition(BIZ_TYPE_MEDIA_ACCOUNT, account.getId(), account.getRebindReviewerUserId(),
                "rebind_pending", "rebind_" + targetStatus, reason,
                "media-account-rebind-result:" + account.getId() + ":" + processInstanceId + ":" + targetStatus);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("bizNo", account.getAccountNo());
        Long deepLinkStudentId = targetStudentId != null ? targetStudentId : account.getStudentPersonId();
        if (deepLinkStudentId != null) payload.put("deepLink", "/zsjos/media-students?personId="
                + deepLinkStudentId + "&tab=accounts&accountId=" + account.getId());
        if (reason != null) payload.put("reason", reason);
        workflowEventService.notify("media.account.rebind_" + targetStatus, BIZ_TYPE_MEDIA_ACCOUNT, account.getId(),
                account.getRebindRequestedByUserId(), account.getRebindReviewerUserId(),
                "media-account-rebind-notify:" + account.getId() + ":" + processInstanceId + ":" + targetStatus,
                payload);
    }

    private Long requireSupervisor(Long userId) {
        var user = adminUserApi.getUser(userId);
        var dept = user == null || user.getDeptId() == null ? null : deptApi.getDept(user.getDeptId());
        Long leader = dept == null ? null : dept.getLeaderUserId();
        var supervisor = leader == null ? null : adminUserApi.getUser(leader);
        if (supervisor == null || userId.equals(leader)
                || !CommonStatusEnum.ENABLE.getStatus().equals(supervisor.getStatus())) {
            throw exception(MEDIA_REBIND_REVIEWER_INVALID);
        }
        return leader;
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "bind-student")
    @Transactional(rollbackFor = Exception.class)
    public void bindStudent(Long accountId, Long studentPersonId, String reason, Long userId) {
        MediaAccountDO account = require(accountId);
        validateStudent(studentPersonId);
        MediaAccountStudentLinkDO active = linkMapper.selectActiveByAccountId(accountId);
        LocalDateTime now = LocalDateTime.now();
        if (active != null) {
            active.setStatus("ended").setEndedAt(now).setReason(reason == null ? "rebind" : reason)
                    .setOperatedByUserId(userId);
            linkMapper.updateById(active);
        }
        linkMapper.insert(new MediaAccountStudentLinkDO().setAccountId(accountId).setStudentPersonId(studentPersonId)
                .setStatus("active").setReason(reason == null ? "bind" : reason).setStartedAt(now)
                .setOperatedByUserId(userId));
        account.setStudentPersonId(studentPersonId).setOwnershipType(OWNERSHIP_STUDENT);
        mapper.updateById(account);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "bind-student")
    @Transactional(rollbackFor = Exception.class)
    public void unbindStudent(Long accountId, String reason, Long userId) {
        MediaAccountDO account = require(accountId);
        MediaAccountStudentLinkDO active = linkMapper.selectActiveByAccountId(accountId);
        if (active != null) {
            active.setStatus("ended").setEndedAt(LocalDateTime.now()).setReason(reason == null ? "unbind" : reason)
                    .setOperatedByUserId(userId);
            linkMapper.updateById(active);
        }
        account.setStudentPersonId(null).setOwnershipType(OWNERSHIP_COMPANY);
        mapper.updateById(account);
    }

    private MediaAccountRespVO toResp(MediaAccountDO account, Long userId) {
        MediaAccountRespVO response = BeanUtils.toBean(account, MediaAccountRespVO.class);
        List<String> actions = new ArrayList<>();
        if (objectPermissionProvider.hasPermission(account.getId(), "read", userId)
                && permissionApi.hasAnyPermissions(userId,
                "zsjos:media-account:query", "zsjos:media-account:maintenance")) {
            actions.add(ACTION_VIEW_ACCOUNT_HISTORY);
        }
        if (objectPermissionProvider.hasPermission(account.getId(), "maintenance", userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:media-account:maintenance")) {
            actions.add("MAINTAIN_ACCOUNT");
        }
        if (objectPermissionProvider.hasPermission(account.getId(), "bind-student", userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:media-account:bind-student")) {
            actions.add(account.getStudentPersonId() == null ? ACTION_BIND_STUDENT : ACTION_UNBIND_STUDENT);
        }
        if (permissionApi.hasAnyPermissions(userId, "zsjos:media-account:edit")) actions.add(ACTION_EDIT_ACCOUNT);
        if (permissionApi.hasAnyPermissions(userId, "zsjos:media-account:rescue")) actions.add(ACTION_RESCUE_ACCOUNT);
        if (account.getStudentPersonId() != null && !"pending".equals(account.getRebindStatus())
                && objectPermissionProvider.hasPermission(account.getId(), "rebind", userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:media-account:rebind")) {
            actions.add(ACTION_REQUEST_ACCOUNT_REBIND);
        }
        response.setAvailableActions(actions);
        response.setDetailValues(account.getDetailValuesJson() == null ? Map.of()
                : JsonUtils.parseObject(account.getDetailValuesJson(), Map.class));
        response.setDetailSnapshots(account.getDetailSnapshotJson() == null ? List.of()
                : JsonUtils.parseArray(account.getDetailSnapshotJson(), MediaAccountDetailSnapshotVO.class));
        response.setPrimaryProblems(account.getPrimaryProblemsJson() == null ? List.of()
                : JsonUtils.parseArray(account.getPrimaryProblemsJson(), MediaAccountMaintenanceProblemVO.class));
        return response;
    }

    private Map<String, Object> mergeCompatibilityValues(Map<String, Object> requested, String uid, String nickname) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        if (requested != null) {
            values.putAll(requested);
        } else {
            if (uid != null && !uid.isBlank()) values.put("uid", uid);
            if (nickname != null && !nickname.isBlank()) values.put("nickname", nickname);
        }
        return values;
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private void validateStudent(Long studentPersonId) {
        if (studentPersonId != null && personMapper.selectById(studentPersonId) == null) {
            throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);
        }
    }
}
