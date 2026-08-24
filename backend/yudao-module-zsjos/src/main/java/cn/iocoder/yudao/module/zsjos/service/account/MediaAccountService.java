package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.AccountDiagnosisSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.AccountStageLogDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountStudentLinkDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.AccountStageLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.AccountWeeklyDiagnosisMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.AccountWeeklyDiagnosisDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.config.MediaConfigVersionMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi; import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO; import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountStudentCandidateRespVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
@Slf4j
public class MediaAccountService {
    @Resource private MediaAccountMapper mapper;
    @Resource private AccountStageLogMapper stageLogMapper;
    @Resource private MediaAccountStudentLinkMapper linkMapper;
    @Resource private MediaAccountNumberService numberService;
    @Resource private PermissionApi permissionApi;
    @Resource private MediaAccountObjectPermissionProvider objectPermissionProvider;
    @Resource private MediaDataScopeService dataScopeService;
    @Resource private PersonMapper personMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private AccountWeeklyDiagnosisMapper diagnosisMapper;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private MediaConfigVersionMapper configVersionMapper;
    @Resource private MediaWorkflowEventService workflowEventService;
    @Resource private MediaAccountFieldConfigService fieldConfigService;

    @Transactional(rollbackFor = Exception.class)
    public Long create(MediaAccountSaveReqVO req, Long userId) {
        if (req.getStudentPersonId() == null) throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);
        validateStudent(req.getStudentPersonId());
        Long directorUserId = permissionApi.hasAnyPermissions(userId, "zsjos:media-account:query-all")
                && req.getDirectorUserId() != null ? req.getDirectorUserId() : userId;
        adminUserApi.validateUser(directorUserId);
        Map<String, Object> detailValues = mergeCompatibilityValues(
                req.getDetailValues(), req.getPlatformAccountId(), req.getNickname());
        MediaAccountFieldConfigService.DetailSnapshot details = fieldConfigService.validateAndSnapshot(detailValues);
        String uid = stringValue(details.values().get("uid"), req.getPlatformAccountId());
        String nickname = stringValue(details.values().get("nickname"), req.getNickname());
        MediaAccountDO account = new MediaAccountDO();
        account.setAccountNo(numberService.next()).setStudentPersonId(req.getStudentPersonId())
                .setOwnershipType(req.getStudentPersonId() == null ? OWNERSHIP_COMPANY : OWNERSHIP_STUDENT)
                .setOwnerOperatorUserId(userId).setDirectorUserId(directorUserId)
                .setPlatformValue(req.getPlatformValue()).setPlatformLabelSnapshot(req.getPlatformLabelSnapshot())
                .setPlatformAccountId(uid).setNickname(nickname)
                .setDetailConfigVersionId(details.configVersionId())
                .setDetailValuesJson(JsonUtils.toJsonString(details.values()))
                .setDetailSnapshotJson(JsonUtils.toJsonString(details.snapshots()))
                .setLeadDirection(req.getLeadDirection()).setSStage("s0")
                .setSStageEnteredAt(LocalDateTime.now()).setIsSilent(false).setRunStatus(RUN_STATUS_ACTIVE)
                .setRescueStatus("none").setWhitelistStatus("none").setVersion(0);
        mapper.insert(account);
        return account.getId();
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "stage-advance")
    @Transactional(rollbackFor = Exception.class)
    public void advanceStage(Long accountId, String toStage, Integer version, String criteriaSnapshotJson,
                             String basis, String idempotencyKey, Long userId) {
        stageTransition(accountId, toStage, version, criteriaSnapshotJson, basis, idempotencyKey, userId, true);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "stage-rollback")
    @Transactional(rollbackFor = Exception.class)
    public void rollbackStage(Long accountId, String toStage, Integer version, String criteriaSnapshotJson,
                              String basis, String idempotencyKey, Long userId) {
        stageTransition(accountId, toStage, version, criteriaSnapshotJson, basis, idempotencyKey, userId, false);
    }

    private void stageTransition(Long accountId, String toStage, Integer version, String criteriaSnapshotJson,
                                 String basis, String idempotencyKey, Long userId, boolean advancing) {
        if (!ACCOUNT_STAGES.contains(toStage)) throw exception(MEDIA_ACCOUNT_STAGE_INVALID);
        if (criteriaSnapshotJson == null || criteriaSnapshotJson.isBlank() || basis == null || basis.isBlank()) {
            throw exception(MEDIA_ACCOUNT_STAGE_BASIS_REQUIRED);
        }
        MediaAccountDO account = require(accountId);
        if (stageLogMapper.selectByIdempotencyKey(idempotencyKey) != null) return;
        if (account.getSStage().equals(toStage)) throw exception(MEDIA_ACCOUNT_STAGE_INVALID);
        int indexFrom = ACCOUNT_STAGES.indexOf(account.getSStage());
        int indexTo = ACCOUNT_STAGES.indexOf(toStage);
        if (advancing != (indexTo > indexFrom)) throw exception(MEDIA_ACCOUNT_STAGE_INVALID);
        String direction = indexTo > indexFrom ? "advance" : "rollback";
        String stageVersion = indexTo > indexFrom ? toStage : account.getSStageVersion();
        LocalDateTime now = LocalDateTime.now();
        if (mapper.updateStage(accountId, version, account.getSStage(), toStage, stageVersion, userId, now) == 0) {
            throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
        }
        AccountStageLogDO log = new AccountStageLogDO().setAccountId(accountId).setFromStage(account.getSStage())
                .setToStage(toStage).setStageVersion(stageVersion).setDirection(direction)
                .setCriteriaSnapshotJson(criteriaSnapshotJson).setJudgmentBasis(basis)
                .setJudgedByUserId(userId).setJudgedAt(now).setIdempotencyKey(idempotencyKey);
        stageLogMapper.insert(log);
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
        MediaAccountDO account = require(id);
        Long directorUserId = account.getDirectorUserId();
        if (req.getDirectorUserId() != null && !req.getDirectorUserId().equals(directorUserId)) {
            if (!permissionApi.hasAnyPermissions(userId, "zsjos:media-account:query-all")) {
                throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
            }
            adminUserApi.validateUser(req.getDirectorUserId());
            directorUserId = req.getDirectorUserId();
        }
        if (req.getDetailValues() != null) {
            MediaAccountFieldConfigService.DetailSnapshot details = fieldConfigService.validateAndSnapshot(
                    mergeCompatibilityValues(req.getDetailValues(), req.getPlatformAccountId(), req.getNickname()));
            account.setDetailConfigVersionId(details.configVersionId())
                    .setDetailValuesJson(JsonUtils.toJsonString(details.values()))
                    .setDetailSnapshotJson(JsonUtils.toJsonString(details.snapshots()))
                    .setPlatformAccountId(stringValue(details.values().get("uid"), req.getPlatformAccountId()))
                    .setNickname(stringValue(details.values().get("nickname"), req.getNickname()));
        }
        account.setLeadDirection(req.getLeadDirection()).setDirectorUserId(directorUserId)
                .setAccountGradeValue(req.getAccountGradeValue()).setAccountGradeLabelSnapshot(req.getAccountGradeLabelSnapshot())
                .setHealthStatusValue(req.getHealthStatusValue()).setHealthStatusLabelSnapshot(req.getHealthStatusLabelSnapshot())
                .setRiskLevelValue(req.getRiskLevelValue()).setRiskLevelLabelSnapshot(req.getRiskLevelLabelSnapshot())
                .setHealthJson(req.getHealthJson());
        if (mapper.updateProfile(account, req.getVersion()) == 0) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "diagnose")
    @Transactional(rollbackFor = Exception.class)
    public Long diagnose(Long accountId, AccountDiagnosisSaveReqVO req, Long userId) {
        require(accountId);
        AccountWeeklyDiagnosisDO row = BeanUtils.toBean(req, AccountWeeklyDiagnosisDO.class);
        row.setAccountId(accountId); row.setOwnerOperatorUserId(userId); row.setVersion(0);
        diagnosisMapper.insert(row); return row.getId();
    }

    public List<AccountWeeklyDiagnosisDO> diagnoses(Long accountId, Long userId) {
        get(accountId, userId); return diagnosisMapper.selectByAccount(accountId);
    }
    public Long getPublishedDiagnosisConfigId() {
        var config = configVersionMapper.selectPublished();
        if (config == null) throw exception(MEDIA_CONFIG_INVALID);
        return config.getId();
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
        if (objectPermissionProvider.hasPermission(account.getId(), "stage-advance", userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:media-account:stage-advance")) {
            actions.add(ACTION_ADVANCE_STAGE);
        }
        if (!"s0".equals(account.getSStage()) && objectPermissionProvider.hasPermission(account.getId(), "stage-rollback", userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:media-account:stage-rollback")) {
            actions.add(ACTION_ROLLBACK_STAGE);
        }
        if (objectPermissionProvider.hasPermission(account.getId(), "bind-student", userId)
                && permissionApi.hasAnyPermissions(userId, "zsjos:media-account:bind-student")) {
            actions.add(account.getStudentPersonId() == null ? ACTION_BIND_STUDENT : ACTION_UNBIND_STUDENT);
        }
        if (permissionApi.hasAnyPermissions(userId, "zsjos:media-account:edit")) actions.add(ACTION_EDIT_ACCOUNT);
        if (permissionApi.hasAnyPermissions(userId, "zsjos:media-account:diagnose")) actions.add(ACTION_DIAGNOSE_ACCOUNT);
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
