package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardPageReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerStudentLinkMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorConfigService;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi; import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO; import java.util.Map;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;

@Service
public class PositioningCardService {
    @Resource private PositioningCardMapper mapper;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private PermissionApi permissionApi;
    @Resource private PostApi postApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PositioningCardObjectPermissionProvider objectPermissionProvider;
    @Resource private MediaDataScopeService dataScopeService;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private PersonMapper personMapper;
    @Resource private PartnerStudentLinkMapper partnerStudentLinkMapper;
    @Resource private PartnerAccountMapper partnerAccountMapper;
    @Resource private MediaWorkflowEventService workflowEventService;
    @Resource private DirectorFormTemplateService directorFormTemplateService;
    @Resource private DirectorConfigService directorConfigService;

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

    @Transactional(rollbackFor = Exception.class)
    public Long create(PositioningCardSaveReqVO req, Long userId) {
        var account = accountMapper.selectById(req.getAccountId());
        if (account == null || req.getStudentPersonId() == null
                || personMapper.selectById(req.getStudentPersonId()) == null
                || !req.getStudentPersonId().equals(account.getStudentPersonId())
                || (!userId.equals(account.getDirectorUserId())
                && !relationMapper.existsActiveByDirectorAndPerson(userId, req.getStudentPersonId()))) {
            throw exception(POSITIONING_REFERENCE_INVALID);
        }
        var relations = relationMapper.selectActiveByPersonIds(List.of(req.getStudentPersonId()));
        var relation = req.getServiceRelationId() == null
                ? relations.stream().filter(row -> userId.equals(row.getContentDirectorUserId()))
                    .filter(row -> "accepted".equals(row.getAcceptanceStatus())).findFirst().orElse(null)
                : relations.stream().filter(row -> req.getServiceRelationId().equals(row.getId())).findFirst().orElse(null);
        if (relation == null || !userId.equals(relation.getContentDirectorUserId())
                || !"accepted".equals(relation.getAcceptanceStatus())
                || !"positioning_ready".equals(relation.getDirectorStage())) {
            throw exception(POSITIONING_REFERENCE_INVALID);
        }
        var snapshot = directorFormTemplateService.validateAndSnapshot(
                DirectorFormTemplateService.SCENE_POSITIONING, req.getTemplateId(), req.getValues(), false);
        java.time.LocalDate trialEndDate = req.getTrialEndDate() == null
                ? java.time.LocalDate.now().plusDays(directorConfigService.trialDays()) : req.getTrialEndDate();
        if (trialEndDate.isBefore(java.time.LocalDate.now())) throw exception(POSITIONING_REFERENCE_INVALID);
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
        return card.getId();
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "submit-review")
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long id, PositioningCardSaveReqVO req, Long userId) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_CO_CREATING);
        if (!Objects.equals(card.getDirectorUserId(), userId) || !Objects.equals(card.getAccountId(), req.getAccountId())
                || !Objects.equals(card.getVersion(), req.getVersion())) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        Map<String, Object> previousDictSnapshots = StrUtil.isBlank(card.getDictSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getDictSnapshotJson(), Map.class);
        var snapshot = directorFormTemplateService.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, card.getTemplateVersionId(), req.getValues(), false,
                previousDictSnapshots);
        java.time.LocalDate trialEndDate = req.getTrialEndDate() == null ? card.getTrialEndDate() : req.getTrialEndDate();
        if (trialEndDate == null || trialEndDate.isBefore(java.time.LocalDate.now())) throw exception(POSITIONING_REFERENCE_INVALID);
        card.setFieldsSnapshotJson(JsonUtils.toJsonString(snapshot.getFields()))
                .setValuesSnapshotJson(JsonUtils.toJsonString(snapshot.getValues()))
                .setDictSnapshotJson(JsonUtils.toJsonString(snapshot.getDictSnapshots()))
                .setTrialEndDate(trialEndDate).setVersion(card.getVersion() + 1);
        if (mapper.updateDraftSnapshot(card, req.getVersion(), POSITIONING_CO_CREATING) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
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
        if (!POSITIONING_CO_CREATING.equals(card.getStatus())) throw exception(POSITIONING_CARD_STATE_INVALID);
        if (card.getTemplateId() != null) {
            Map<String, Object> values = StrUtil.isBlank(card.getValuesSnapshotJson()) ? Map.of()
                    : JsonUtils.parseObject(card.getValuesSnapshotJson(), Map.class);
            Map<String, Object> dictSnapshots = StrUtil.isBlank(card.getDictSnapshotJson()) ? Map.of()
                    : JsonUtils.parseObject(card.getDictSnapshotJson(), Map.class);
            directorFormTemplateService.validateAndSnapshotVersion(DirectorFormTemplateService.SCENE_POSITIONING,
                    card.getTemplateVersionId(), values, true, dictSnapshots);
            if (card.getTrialEndDate() == null || card.getTrialEndDate().isBefore(java.time.LocalDate.now())) {
                throw exception(POSITIONING_REFERENCE_INVALID);
            }
        }
        if (!Boolean.TRUE.equals(card.getProfessionalRisk())) {
            transition(card, version, POSITIONING_OPERATOR_FEASIBILITY);
            workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, userId, POSITIONING_CO_CREATING,
                    POSITIONING_OPERATOR_FEASIBILITY, null, transitionKey(card, version,
                            POSITIONING_OPERATOR_FEASIBILITY));
            notifyOperatorReview(card, userId, version, "ordinary");
            return;
        }
        BpmProcessInstanceCreateReqDTO process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey(PROCESS_KEY_POSITIONING_IP);
        process.setBusinessKey("positioning-card:" + card.getId() + ":v" + card.getVersionNo());
        List<Long> reviewers = getEnabledIpReviewers();
        if (reviewers.isEmpty()) throw exception(POSITIONING_IP_PROCESS_UNAVAILABLE);
        Long reviewer = reviewers.get(0);
        process.setStartUserSelectAssignees(Map.of("ipReviewer", reviewers));
        process.setVariables(new java.util.HashMap<>(Map.of("positioningCardId", card.getId(),
                "accountId", card.getAccountId(), "assignee", reviewer,
                "coll_userList", reviewers, "ipReviewer", reviewer)));
        card.setIpReviewerUserId(reviewer);
        try {
            card.setIpProcessInstanceId(processInstanceApi.createProcessInstance(userId, process));
        } catch (RuntimeException ex) {
            throw exception(POSITIONING_IP_PROCESS_UNAVAILABLE);
        }
        card.setStatus(POSITIONING_IP_REVIEW);
        card.setVersion(version + 1);
        if (mapper.updateByVersion(card, version, POSITIONING_CO_CREATING) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, userId, POSITIONING_CO_CREATING,
                POSITIONING_IP_REVIEW, null, transitionKey(card, version, POSITIONING_IP_REVIEW));
    }

    private List<Long> getEnabledIpReviewers() {
        PostRespDTO post = postApi.getPostByCode(POST_CODE_IP_TEACHER);
        if (post == null || !CommonStatusEnum.ENABLE.getStatus().equals(post.getStatus())) return List.of();
        return adminUserApi.getUserListByPostIds(List.of(post.getId())).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .map(AdminUserRespDTO::getId).filter(java.util.Objects::nonNull).distinct().toList();
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "operator-confirm")
    @Transactional(rollbackFor = Exception.class)
    public void operatorApprove(Long id, Integer version) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_OPERATOR_FEASIBILITY);
        Long operator = currentAdminUserId();
        transitionOperatorReview(card, version, POSITIONING_STUDENT_CONFIRM, operator, null);
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, operator, POSITIONING_OPERATOR_FEASIBILITY,
                POSITIONING_STUDENT_CONFIRM, null, transitionKey(card, version, POSITIONING_STUDENT_CONFIRM));
        notifyStudentConfirmation(card, operator, version);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "operator-reject")
    @Transactional(rollbackFor = Exception.class)
    public void operatorReject(Long id, Integer version, String reason) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_OPERATOR_FEASIBILITY);
        Long operator = currentAdminUserId();
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
    public void studentConfirm(Long id, Integer version) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_STUDENT_CONFIRM);
        transition(card, version, POSITIONING_TRIAL_14D);
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, null, POSITIONING_STUDENT_CONFIRM,
                POSITIONING_TRIAL_14D, null, transitionKey(card, version, POSITIONING_TRIAL_14D));
        notifyEmployeeResult(card, "media.positioning.student_confirmed", version, POSITIONING_TRIAL_14D);
    }

    @Transactional(rollbackFor = Exception.class)
    public void studentReject(Long id, Integer version) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_STUDENT_CONFIRM);
        transition(card, version, POSITIONING_CO_CREATING);
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, null, POSITIONING_STUDENT_CONFIRM,
                POSITIONING_CO_CREATING, null, transitionKey(card, version, POSITIONING_CO_CREATING));
        notifyEmployeeResult(card, "media.positioning.student_rejected", version, POSITIONING_CO_CREATING);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "confirm-trial")
    @Transactional(rollbackFor = Exception.class)
    public void confirmTrial(Long id, Integer version) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_TRIAL_14D);
        transition(card, version, POSITIONING_CONFIRMED);
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, currentAdminUserId(), POSITIONING_TRIAL_14D,
                POSITIONING_CONFIRMED, null, transitionKey(card, version, POSITIONING_CONFIRMED));
    }

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#id", action = "archive")
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id, Integer version) {
        PositioningCardDO card = require(id);
        requireStatus(card, POSITIONING_CONFIRMED);
        transition(card, version, POSITIONING_ARCHIVED);
        workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, id, currentAdminUserId(), POSITIONING_CONFIRMED,
                POSITIONING_ARCHIVED, null, transitionKey(card, version, POSITIONING_ARCHIVED));
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
        var account = accountMapper.selectById(card.getAccountId());
        if (account == null) return;
        Long operatorUserId = relationMapper.selectActiveByPersonIds(List.of(card.getStudentPersonId())).stream()
                .map(cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO::getOperatorUserId)
                .filter(java.util.Objects::nonNull).findFirst().orElse(account.getOwnerOperatorUserId());
        if (operatorUserId == null) return;
        workflowEventService.notify("media.positioning.operator_review", BIZ_TYPE_POSITIONING_CARD, card.getId(),
                operatorUserId, operator,
                "positioning-operator-review:" + card.getId() + ":" + version + ":" + branch, payload(card));
    }

    private void notifyStudentConfirmation(PositioningCardDO card, Long operator, Integer version) {
        var link = partnerStudentLinkMapper.selectActiveByStudent(card.getStudentPersonId());
        if (link == null) return;
        var partnerAccount = partnerAccountMapper.selectByPartnerId(link.getPartnerId());
        if (partnerAccount == null) return;
        Map<String, Object> values = new java.util.LinkedHashMap<>(payload(card));
        values.put("partnerAccountId", partnerAccount.getId());
        values.put("deepLink", "/positioning/confirm/" + card.getId());
        workflowEventService.notify("media.positioning.student_confirmation", BIZ_TYPE_POSITIONING_CARD,
                card.getId(), null, operator, "positioning-student-confirmation:" + card.getId() + ":" + version,
                values);
    }

    private void notifyEmployeeResult(PositioningCardDO card, String scene, Integer version, String target) {
        workflowEventService.notify(scene, BIZ_TYPE_POSITIONING_CARD, card.getId(), card.getDirectorUserId(), null,
                "positioning-student-result:" + card.getId() + ":" + version + ":director:" + target,
                payload(card));
        var account = accountMapper.selectById(card.getAccountId());
        if (account != null && account.getOwnerOperatorUserId() != null
                && !account.getOwnerOperatorUserId().equals(card.getDirectorUserId())) {
            workflowEventService.notify(scene, BIZ_TYPE_POSITIONING_CARD, card.getId(),
                    account.getOwnerOperatorUserId(), null,
                    "positioning-student-result:" + card.getId() + ":" + version + ":operator:" + target,
                    payload(card));
        }
    }

    private Map<String, Object> payload(PositioningCardDO card) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("bizNo", card.getCardNo());
        if (card.getStudentPersonId() != null) {
            values.put("deepLink", "/zsjos/media-students?personId=" + card.getStudentPersonId()
                    + "&tab=positioning&positioningCardId=" + card.getId());
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

    private PositioningCardRespVO toResp(PositioningCardDO card, Long userId) {
        PositioningCardRespVO response = BeanUtils.toBean(card, PositioningCardRespVO.class);
        response.setFieldsSnapshot(StrUtil.isBlank(card.getFieldsSnapshotJson()) ? List.of()
                : JsonUtils.parseArray(card.getFieldsSnapshotJson(), Object.class));
        response.setValuesSnapshot(StrUtil.isBlank(card.getValuesSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getValuesSnapshotJson(), Map.class));
        response.setDictSnapshot(StrUtil.isBlank(card.getDictSnapshotJson()) ? Map.of()
                : JsonUtils.parseObject(card.getDictSnapshotJson(), Map.class));
        if (!objectPermissionProvider.hasPermission(card.getId(), "read", userId)) {
            response.setAvailableActions(List.of()); return response;
        }
        response.setAvailableActions(availableActionsForVisible(card, userId));
        return response;
    }

    public List<String> availableActionsForVisible(PositioningCardDO card, Long userId) {
        List<String> actions = new java.util.ArrayList<>();
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
        } else if (POSITIONING_TRIAL_14D.equals(card.getStatus())
                && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:confirm-trial")
                && objectPermissionProvider.hasPermission(card.getId(), "confirm-trial", userId)) {
            actions.add(ACTION_CONFIRM_POSITIONING_TRIAL);
        } else if (POSITIONING_CONFIRMED.equals(card.getStatus())
                && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:archive")
                && objectPermissionProvider.hasPermission(card.getId(), "archive", userId)) {
            actions.add(ACTION_ARCHIVE_POSITIONING);
        }
        return actions;
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
