package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmFormMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackActionVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackFormRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackReplyDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackSurveyDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.WorkOrderHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackNoDailyCounterMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackReplyMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackSurveyMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_ASSIGNEE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_CHAIRMAN_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_CONFIG_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_NOT_OPEN;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_PROCESS_UNAVAILABLE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_SURVEY_ALREADY_REQUESTED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_SURVEY_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_TYPE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_ACCOUNT_DISABLED;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.AUTHOR_ADMIN;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.AUTHOR_EMPLOYEE;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.AUTHOR_PARTNER_ACCOUNT;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.BUSINESS_TYPE_FEEDBACK;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.NOTIFY_SCENE_READY_FOR_HANDLING;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.PROCESS_DEFINITION_KEY;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.ROLE_CHAIRMAN;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.STATUS_APPROVAL_REJECTED;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.STATUS_APPROVING;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.STATUS_COMPLETED;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.STATUS_IN_PROGRESS;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.STATUS_WAITING;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.SUBJECT_ADMIN;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.SUBJECT_PARTNER_ACCOUNT;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.SUBMISSION_TYPES;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.SURVEY_PENDING;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.SURVEY_SUBMITTED;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TASK_CHAIRMAN;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TASK_DEPARTMENT_LEADER;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_BUG;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_LABEL;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_PERMISSION;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_PREFIX;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_REQUIREMENT;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_SUPPORT;
import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.TYPE_SURVEY;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SCENE_EMPLOYEE_REPLIED = "zsjos.feedback.employee_replied";
    private static final String SCENE_ADMIN_REPLIED = "zsjos.feedback.admin_replied";
    private static final String SCENE_COMPLETED = "zsjos.feedback.completed";
    private static final String SCENE_SURVEY_REQUESTED = "zsjos.feedback.survey_requested";

    @Resource
    private FeedbackMapper feedbackMapper;
    @Resource
    private FeedbackRoundMapper roundMapper;
    @Resource
    private FeedbackReplyMapper replyMapper;
    @Resource
    private FeedbackSurveyMapper surveyMapper;
    @Resource
    private FeedbackConfigMapper feedbackConfigMapper;
    @Resource
    private FeedbackNoDailyCounterMapper counterMapper;
    @Resource
    private WorkOrderMapper workOrderMapper;
    @Resource
    private WorkOrderHistoryMapper historyMapper;
    @Resource
    private FeedbackDynamicFormService dynamicFormService;
    @Resource
    private BpmDefinitionReadApi definitionReadApi;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private RoleApi roleApi;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private FileApi fileApi;
    @Resource
    private NotifyBusinessEventApi notifyBusinessEventApi;
    @Resource
    private PartnerMapper partnerMapper;

    @Override
    public FeedbackRespVO.Portal getPortal(Long userId) {
        FeedbackRespVO.Portal portal = new FeedbackRespVO.Portal();
        portal.setEntries(List.of(
                portalEntry(TYPE_REQUIREMENT, "提交需求", "提交软件系统和网站建设需求"),
                portalEntry(TYPE_BUG, "BUG 反馈", "反馈系统使用中遇到的问题"),
                portalEntry(TYPE_SUPPORT, "技术支持", "申请账号、软件、设备或网络支持")));
        portal.setRecent(feedbackMapper.selectRecentBySubmitter(userId, 5).stream()
                .map(row -> toCard(row, userId, false)).toList());
        return portal;
    }

    @Override
    public FeedbackRespVO.Portal getPartnerPortal(Long accountId, Long partnerId) {
        PartnerDO partner = requireEnabledPartner(partnerId);
        FeedbackRespVO.Portal portal = new FeedbackRespVO.Portal();
        portal.setEntries(List.of(
                partnerPortalEntry(TYPE_REQUIREMENT, "提交需求", "提交软件系统和网站建设需求"),
                partnerPortalEntry(TYPE_BUG, "BUG 反馈", "反馈系统使用中遇到的问题"),
                partnerPortalEntry(TYPE_SUPPORT, "技术支持", "申请账号、软件、设备或网络支持")));
        portal.setRecent(feedbackMapper.selectRecentBySubmitter(SUBJECT_PARTNER_ACCOUNT, accountId, 5).stream()
                .filter(row -> Objects.equals(row.getPartnerId(), partner.getId()))
                .map(row -> toCard(row, accountId, false)).toList());
        return portal;
    }

    @Override
    public FeedbackFormRespVO getCurrentForm(String type) {
        requireSubmissionType(type);
        FeedbackConfigDO config = requireConfig(type);
        FeedbackDynamicFormService.ParsedForm parsed = dynamicFormService.requireCompatibleForm(
                config.getFormId(), type, config.getTitleFieldKey());
        List<Long> dispatchers = effectiveDispatchers(config);
        boolean open = !dispatchers.isEmpty();
        return dynamicFormService.toResponse(type, parsed, config.getTitleFieldKey(), config.getVersion(),
                open, open ? null : "暂未配置可用的分派负责人");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(String type, FeedbackCreateReqVO request, Long userId) {
        requireSubmissionType(type);
        FeedbackConfigDO config = requireConfig(type);
        validateConfigVersion(config, request.getConfigVersion());
        if (effectiveDispatchers(config).isEmpty()) throw exception(FEEDBACK_NOT_OPEN);

        FeedbackDynamicFormService.ParsedForm form = dynamicFormService.requireCompatibleForm(
                config.getFormId(), type, config.getTitleFieldKey());
        FeedbackDynamicFormService.NormalizedValues normalized = dynamicFormService.normalizeValues(
                form, request.getValues(), userId);
        String fingerprint = fingerprint("create", type, userId, request.getConfigVersion(), normalized.values());
        WorkOrderDO existing = workOrderMapper.selectAnyByIdempotencyKey(request.getIdempotencyKey());
        if (existing != null) return requireCreateReplay(existing, fingerprint, userId);

        AdminUserRespDTO submitter = requireEnabledUser(userId);
        String feedbackNo = nextFeedbackNo(type);
        String initialStatus = TYPE_REQUIREMENT.equals(type) && Boolean.TRUE.equals(config.getApprovalEnabled())
                ? STATUS_APPROVING : STATUS_WAITING;
        LocalDateTime now = LocalDateTime.now();
        String fieldSnapshotJson = JsonUtils.toJsonString(form.fields());
        String valueSnapshotJson = JsonUtils.toJsonString(normalized.values());

        WorkOrderDO workOrder = new WorkOrderDO();
        workOrder.setBusinessType(BUSINESS_TYPE_FEEDBACK);
        workOrder.setOrderNo(feedbackNo);
        workOrder.setSceneCode(type);
        workOrder.setSceneNameSnapshot(TYPE_LABEL.get(type));
        workOrder.setAssignmentMode("DIRECT");
        workOrder.setSourceSubjectType(SUBJECT_ADMIN);
        workOrder.setSourceUserId(userId);
        workOrder.setSourceNameSnapshot(submitter.getNickname());
        workOrder.setStatus(initialStatus);
        workOrder.setFieldSnapshotJson(fieldSnapshotJson);
        workOrder.setValueJson(valueSnapshotJson);
        workOrder.setAttachmentIdsJson(JsonUtils.toJsonString(normalized.attachmentIds()));
        workOrder.setIdempotencyKey(request.getIdempotencyKey());
        workOrder.setCommandSubjectType(SUBJECT_ADMIN);
        workOrder.setCommandUserId(userId);
        workOrder.setRequestFingerprint(fingerprint);
        workOrder.setVersion(0);
        workOrderMapper.insert(workOrder);

        FeedbackDO feedback = new FeedbackDO();
        feedback.setWorkOrderId(workOrder.getId());
        feedback.setFeedbackType(type);
        feedback.setFeedbackNo(feedbackNo);
        feedback.setTitle(String.valueOf(normalized.values().get(config.getTitleFieldKey())));
        feedback.setTitleFieldKey(config.getTitleFieldKey());
        feedback.setFormId(config.getFormId());
        feedback.setFormSnapshotJson(fieldSnapshotJson);
        feedback.setValueSnapshotJson(valueSnapshotJson);
        applySupportSnapshot(feedback, normalized.values());
        feedback.setStatus(initialStatus);
        feedback.setSubmitterSubjectType(SUBJECT_ADMIN);
        feedback.setSubmitterUserId(userId);
        feedback.setSubmitterNameSnapshot(submitter.getNickname());
        feedback.setLastActivityAt(now);
        feedback.setUnreadForSubmitter(false);
        feedback.setUnreadForAssignee(false);
        feedback.setApprovalEnabled(TYPE_REQUIREMENT.equals(type)
                && Boolean.TRUE.equals(config.getApprovalEnabled()));
        feedback.setApprovalRoundNo(TYPE_REQUIREMENT.equals(type) ? 1 : 0);
        feedback.setConfigVersion(config.getVersion());
        feedback.setVersion(0);
        feedbackMapper.insert(feedback);
        addHistory(workOrder, null, initialStatus, userId, "创建反馈", request.getIdempotencyKey(),
                "CREATE", fingerprint);

        if (TYPE_REQUIREMENT.equals(type)) {
            createRequirementRound(feedback, config, form, normalized.values(), userId, submitter, now);
        }
        if (STATUS_WAITING.equals(initialStatus)) {
            publishReadyForHandling(feedback, userId, config);
        }
        return feedback.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createForPartner(String type, FeedbackCreateReqVO request, Long accountId, Long partnerId) {
        requireSubmissionType(type);
        PartnerDO partner = requireEnabledPartner(partnerId);
        FeedbackConfigDO config = requireConfig(type);
        validateConfigVersion(config, request.getConfigVersion());
        if (effectiveDispatchers(config).isEmpty()) throw exception(FEEDBACK_NOT_OPEN);
        if (TYPE_REQUIREMENT.equals(type) && Boolean.TRUE.equals(config.getApprovalEnabled())) {
            throw exception(FEEDBACK_PROCESS_UNAVAILABLE);
        }

        FeedbackDynamicFormService.ParsedForm form = dynamicFormService.requireCompatibleForm(
                config.getFormId(), type, config.getTitleFieldKey());
        FeedbackDynamicFormService.NormalizedValues normalized = dynamicFormService.normalizeValues(
                form, request.getValues(), accountId);
        String fingerprint = fingerprint("create", SUBJECT_PARTNER_ACCOUNT, type, accountId, partnerId,
                request.getConfigVersion(), normalized.values());
        WorkOrderDO existing = workOrderMapper.selectAnyByIdempotencyKey(request.getIdempotencyKey());
        if (existing != null) {
            return requireCreateReplay(existing, fingerprint, SUBJECT_PARTNER_ACCOUNT, accountId);
        }

        String feedbackNo = nextFeedbackNo(type);
        String initialStatus = STATUS_WAITING;
        LocalDateTime now = LocalDateTime.now();
        String fieldSnapshotJson = JsonUtils.toJsonString(form.fields());
        String valueSnapshotJson = JsonUtils.toJsonString(normalized.values());
        String submitterName = partner.getName();

        WorkOrderDO workOrder = new WorkOrderDO();
        workOrder.setBusinessType(BUSINESS_TYPE_FEEDBACK);
        workOrder.setOrderNo(feedbackNo);
        workOrder.setSceneCode(type);
        workOrder.setSceneNameSnapshot(TYPE_LABEL.get(type));
        workOrder.setAssignmentMode("DIRECT");
        workOrder.setSourceSubjectType(SUBJECT_PARTNER_ACCOUNT);
        workOrder.setSourceUserId(accountId);
        workOrder.setSourceNameSnapshot(submitterName);
        workOrder.setStatus(initialStatus);
        workOrder.setFieldSnapshotJson(fieldSnapshotJson);
        workOrder.setValueJson(valueSnapshotJson);
        workOrder.setAttachmentIdsJson(JsonUtils.toJsonString(normalized.attachmentIds()));
        workOrder.setIdempotencyKey(request.getIdempotencyKey());
        workOrder.setCommandSubjectType(SUBJECT_PARTNER_ACCOUNT);
        workOrder.setCommandUserId(accountId);
        workOrder.setRequestFingerprint(fingerprint);
        workOrder.setVersion(0);
        workOrderMapper.insert(workOrder);

        FeedbackDO feedback = new FeedbackDO();
        feedback.setWorkOrderId(workOrder.getId());
        feedback.setFeedbackType(type);
        feedback.setFeedbackNo(feedbackNo);
        feedback.setTitle(String.valueOf(normalized.values().get(config.getTitleFieldKey())));
        feedback.setTitleFieldKey(config.getTitleFieldKey());
        feedback.setFormId(config.getFormId());
        feedback.setFormSnapshotJson(fieldSnapshotJson);
        feedback.setValueSnapshotJson(valueSnapshotJson);
        applySupportSnapshot(feedback, normalized.values());
        feedback.setStatus(initialStatus);
        feedback.setSubmitterSubjectType(SUBJECT_PARTNER_ACCOUNT);
        feedback.setSubmitterUserId(accountId);
        feedback.setSubmitterNameSnapshot(submitterName);
        feedback.setPartnerId(partnerId);
        feedback.setLastActivityAt(now);
        feedback.setUnreadForSubmitter(false);
        feedback.setUnreadForAssignee(false);
        feedback.setApprovalEnabled(false);
        feedback.setApprovalRoundNo(TYPE_REQUIREMENT.equals(type) ? 1 : 0);
        feedback.setConfigVersion(config.getVersion());
        feedback.setVersion(0);
        feedbackMapper.insert(feedback);
        addHistory(workOrder, null, initialStatus, SUBJECT_PARTNER_ACCOUNT, accountId,
                "创建反馈", request.getIdempotencyKey(), "CREATE", fingerprint);

        if (TYPE_REQUIREMENT.equals(type)) {
            createRequirementRound(feedback, config, form, normalized.values(), accountId, null, now);
        }
        publishReadyForHandling(feedback, accountId, config);
        return feedback.getId();
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "resubmit-own")
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long id, FeedbackActionVO.ResubmitReq request, Long userId) {
        FeedbackDO row = requireLocked(id);
        String fingerprint = fingerprint("resubmit", id, userId, request.getVersion(),
                request.getConfigVersion(), request.getValues());
        if (exactReplay(row, request.getIdempotencyKey(), "RESUBMIT", userId, fingerprint)) return;
        validateVersion(row, request.getVersion());
        if (!TYPE_REQUIREMENT.equals(row.getFeedbackType())
                || !STATUS_APPROVAL_REJECTED.equals(row.getStatus())) {
            throw exception(FEEDBACK_STATE_INVALID);
        }

        FeedbackConfigDO config = requireConfig(TYPE_REQUIREMENT);
        validateConfigVersion(config, request.getConfigVersion());
        if (effectiveDispatchers(config).isEmpty()) throw exception(FEEDBACK_NOT_OPEN);
        FeedbackDynamicFormService.ParsedForm form = dynamicFormService.requireCompatibleForm(
                config.getFormId(), TYPE_REQUIREMENT, config.getTitleFieldKey());
        FeedbackDynamicFormService.NormalizedValues normalized = dynamicFormService.normalizeValues(
                form, request.getValues(), userId);
        AdminUserRespDTO submitter = requireEnabledUser(userId);
        int nextRound = row.getApprovalRoundNo() + 1;
        String nextStatus = Boolean.TRUE.equals(config.getApprovalEnabled()) ? STATUS_APPROVING : STATUS_WAITING;
        String fieldsJson = JsonUtils.toJsonString(form.fields());
        String valuesJson = JsonUtils.toJsonString(normalized.values());

        row.setTitle(String.valueOf(normalized.values().get(config.getTitleFieldKey())));
        row.setTitleFieldKey(config.getTitleFieldKey());
        row.setFormId(config.getFormId());
        row.setFormSnapshotJson(fieldsJson);
        row.setValueSnapshotJson(valuesJson);
        row.setStatus(nextStatus);
        row.setApprovalEnabled(Boolean.TRUE.equals(config.getApprovalEnabled()));
        row.setProcessInstanceId(null);
        row.setApprovalRoundNo(nextRound);
        row.setRejectReason(null);
        row.setConfigVersion(config.getVersion());
        row.setLastActivityAt(LocalDateTime.now());
        if (feedbackMapper.updateById(row) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        syncWorkOrder(row, nextStatus, fieldsJson, valuesJson, normalized.attachmentIds(), null);
        addHistory(requireWorkOrder(row.getWorkOrderId()), STATUS_APPROVAL_REJECTED, nextStatus, userId,
                "驳回后修改重提", request.getIdempotencyKey(), "RESUBMIT", fingerprint);
        createRequirementRound(row, config, form, normalized.values(), userId, submitter, LocalDateTime.now());
        if (STATUS_WAITING.equals(nextStatus)) {
            publishReadyForHandling(row, userId, config);
        }
    }

    @Override
    public PageResult<FeedbackRespVO> getMyPage(FeedbackPageReqVO request, Long userId) {
        validateOptionalSubmissionType(request.getFeedbackType());
        PageResult<FeedbackDO> page = feedbackMapper.selectMyPage(request, userId);
        return new PageResult<>(page.getList().stream().map(row -> toCard(row, userId, false)).toList(),
                page.getTotal());
    }

    @Override
    public PageResult<FeedbackRespVO> getPartnerPage(FeedbackPageReqVO request, Long accountId, Long partnerId) {
        requireEnabledPartner(partnerId);
        validateOptionalSubmissionType(request.getFeedbackType());
        PageResult<FeedbackDO> page = feedbackMapper.selectPartnerPage(request, accountId, partnerId);
        return new PageResult<>(page.getList().stream().map(row -> toCard(row, accountId, false)).toList(),
                page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "read-own")
    public FeedbackRespVO getOwn(Long id, Long userId) {
        return toDetail(require(id), userId, false);
    }

    @Override
    public FeedbackRespVO getPartnerOwn(Long id, Long accountId, Long partnerId) {
        requireEnabledPartner(partnerId);
        FeedbackDO row = require(id);
        requirePartnerFeedback(row, accountId, partnerId);
        return toDetail(row, accountId, false);
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "read-own")
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id, FeedbackActionVO.VersionedCommand request, Long userId) {
        FeedbackDO row = requireLocked(id);
        String fingerprint = fingerprint("read", id, userId, request.getVersion());
        if (exactReplay(row, request.getIdempotencyKey(), "READ", userId, fingerprint)) return;
        validateVersion(row, request.getVersion());
        row.setUnreadForSubmitter(false);
        if (feedbackMapper.updateById(row) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        addHistory(requireWorkOrder(row.getWorkOrderId()), row.getStatus(), row.getStatus(), userId,
                "员工已读", request.getIdempotencyKey(), "READ", fingerprint);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReadForPartner(Long id, FeedbackActionVO.VersionedCommand request,
                                   Long accountId, Long partnerId) {
        requireEnabledPartner(partnerId);
        FeedbackDO row = requireLocked(id);
        requirePartnerFeedback(row, accountId, partnerId);
        String fingerprint = fingerprint("read", SUBJECT_PARTNER_ACCOUNT, id, accountId, request.getVersion());
        if (exactReplay(row, request.getIdempotencyKey(), "READ",
                SUBJECT_PARTNER_ACCOUNT, accountId, fingerprint)) return;
        validateVersion(row, request.getVersion());
        row.setUnreadForSubmitter(false);
        if (feedbackMapper.updateById(row) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        addHistory(requireWorkOrder(row.getWorkOrderId()), row.getStatus(), row.getStatus(),
                SUBJECT_PARTNER_ACCOUNT, accountId, "兼职已读", request.getIdempotencyKey(),
                "READ", fingerprint);
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "reply-own")
    @Transactional(rollbackFor = Exception.class)
    public void replyOwn(Long id, FeedbackActionVO.ReplyReq request, Long userId) {
        reply(id, request, userId, AUTHOR_EMPLOYEE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyForPartner(Long id, FeedbackActionVO.ReplyReq request, Long accountId, Long partnerId) {
        requireEnabledPartner(partnerId);
        reply(id, request, accountId, AUTHOR_PARTNER_ACCOUNT, partnerId);
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "survey-submit-own")
    @Transactional(rollbackFor = Exception.class)
    public void submitSurvey(Long id, FeedbackActionVO.SurveySubmitReq request, Long userId) {
        FeedbackDO row = requireLocked(id);
        String fingerprint = fingerprint("survey-submit", id, userId, request.getVersion(), request.getValues());
        if (exactReplay(row, request.getIdempotencyKey(), "SURVEY_SUBMIT", userId, fingerprint)) return;
        validateVersion(row, request.getVersion());
        FeedbackSurveyDO survey = surveyMapper.selectByFeedbackId(id);
        if (survey == null || !SURVEY_PENDING.equals(survey.getStatus())
                || !Objects.equals(survey.getSubmitterUserId(), userId)) {
            throw exception(FEEDBACK_SURVEY_STATE_INVALID);
        }
        FeedbackConfigDO config = requireConfig(TYPE_SURVEY);
        FeedbackDynamicFormService.ParsedForm form = dynamicFormService.requireCompatibleForm(
                survey.getFormId(), TYPE_SURVEY, config.getTitleFieldKey());
        FeedbackDynamicFormService.NormalizedValues normalized = dynamicFormService.normalizeValues(
                form, request.getValues(), userId);
        survey.setStatus(SURVEY_SUBMITTED);
        survey.setValueSnapshotJson(JsonUtils.toJsonString(normalized.values()));
        survey.setSubmittedAt(LocalDateTime.now());
        surveyMapper.updateById(survey);
        row.setLastActivityAt(LocalDateTime.now());
        row.setUnreadForAssignee(true);
        if (feedbackMapper.updateById(row) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        addHistory(requireWorkOrder(row.getWorkOrderId()), row.getStatus(), row.getStatus(), userId,
                "提交满意度", request.getIdempotencyKey(), "SURVEY_SUBMIT", fingerprint);
    }

    @Override
    public PageResult<FeedbackRespVO> getAdminPage(String type, FeedbackPageReqVO request, Long userId) {
        requireSubmissionType(type);
        PageResult<FeedbackDO> page = feedbackMapper.selectAdminPage(request, type);
        return new PageResult<>(page.getList().stream().map(row -> toCard(row, userId, true)).toList(),
                page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "read-admin")
    public FeedbackRespVO getAdmin(Long id, Long userId) {
        return toDetail(require(id), userId, true);
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "manage")
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, FeedbackActionVO.AssignReq request, Long userId) {
        FeedbackDO row = requireLocked(id);
        String fingerprint = fingerprint("assign", id, userId, request.getVersion(), request.getAssigneeUserId());
        if (exactReplay(row, request.getIdempotencyKey(), "ASSIGN", userId, fingerprint)) return;
        validateVersion(row, request.getVersion());
        if (!Set.of(STATUS_WAITING, STATUS_IN_PROGRESS).contains(row.getStatus())) {
            throw exception(FEEDBACK_STATE_INVALID);
        }
        AdminUserRespDTO assignee = requireEligibleHandler(row.getFeedbackType(), request.getAssigneeUserId());
        String from = row.getStatus();
        row.setAssigneeUserId(assignee.getId());
        row.setAssigneeNameSnapshot(assignee.getNickname());
        row.setStatus(STATUS_IN_PROGRESS);
        row.setLastActivityAt(LocalDateTime.now());
        row.setUnreadForAssignee(false);
        if (feedbackMapper.updateById(row) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        syncWorkOrder(row, STATUS_IN_PROGRESS, null, null, null, assignee);
        addHistory(requireWorkOrder(row.getWorkOrderId()), from, STATUS_IN_PROGRESS, userId,
                "分派给" + assignee.getNickname(), request.getIdempotencyKey(), "ASSIGN", fingerprint);
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "manage")
    @Transactional(rollbackFor = Exception.class)
    public void replyAdmin(Long id, FeedbackActionVO.ReplyReq request, Long userId) {
        reply(id, request, userId, AUTHOR_ADMIN);
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "manage")
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, FeedbackActionVO.CompleteReq request, Long userId) {
        FeedbackDO row = requireLocked(id);
        List<Long> attachmentIds = validateCommandAttachments(request.getAttachmentIds(), userId);
        String fingerprint = fingerprint("complete", id, userId, request.getVersion(),
                request.getResult().trim(), attachmentIds);
        if (exactReplay(row, request.getIdempotencyKey(), "COMPLETE", userId, fingerprint)) return;
        validateVersion(row, request.getVersion());
        if (!STATUS_IN_PROGRESS.equals(row.getStatus()) || row.getAssigneeUserId() == null) {
            throw exception(FEEDBACK_STATE_INVALID);
        }
        row.setStatus(STATUS_COMPLETED);
        row.setCompletedResult(request.getResult().trim());
        row.setResultAttachmentIdsJson(JsonUtils.toJsonString(attachmentIds));
        row.setLastActivityAt(LocalDateTime.now());
        row.setUnreadForSubmitter(true);
        if (feedbackMapper.updateById(row) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        syncWorkOrder(row, STATUS_COMPLETED, null, null, null, null);
        addHistory(requireWorkOrder(row.getWorkOrderId()), STATUS_IN_PROGRESS, STATUS_COMPLETED, userId,
                abbreviate(request.getResult()), request.getIdempotencyKey(), "COMPLETE", fingerprint);
        publish(SCENE_COMPLETED, row, "completed:" + request.getIdempotencyKey(), userId, Map.of());
    }

    @Override
    @ZsjosPermission(bizType = "feedback", bizId = "#id", action = "manage")
    @Transactional(rollbackFor = Exception.class)
    public void requestSurvey(Long id, FeedbackActionVO.VersionedCommand request, Long userId) {
        FeedbackDO row = requireLocked(id);
        String fingerprint = fingerprint("survey-request", id, userId, request.getVersion());
        if (exactReplay(row, request.getIdempotencyKey(), "SURVEY_REQUEST", userId, fingerprint)) return;
        validateVersion(row, request.getVersion());
        if (!STATUS_COMPLETED.equals(row.getStatus())) throw exception(FEEDBACK_STATE_INVALID);
        if (surveyMapper.selectByFeedbackId(id) != null) throw exception(FEEDBACK_SURVEY_ALREADY_REQUESTED);
        FeedbackConfigDO config = requireConfig(TYPE_SURVEY);
        FeedbackDynamicFormService.ParsedForm form = dynamicFormService.requireCompatibleForm(
                config.getFormId(), TYPE_SURVEY, config.getTitleFieldKey());
        AdminUserRespDTO operator = requireEnabledUser(userId);
        FeedbackSurveyDO survey = new FeedbackSurveyDO();
        survey.setFeedbackId(id);
        survey.setStatus(SURVEY_PENDING);
        survey.setFormId(config.getFormId());
        survey.setFormSnapshotJson(JsonUtils.toJsonString(form.fields()));
        survey.setRequestedByUserId(userId);
        survey.setRequestedByNameSnapshot(operator.getNickname());
        survey.setRequestedAt(LocalDateTime.now());
        survey.setSubmitterUserId(row.getSubmitterUserId());
        surveyMapper.insert(survey);
        row.setLastActivityAt(LocalDateTime.now());
        row.setUnreadForSubmitter(true);
        if (feedbackMapper.updateById(row) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        addHistory(requireWorkOrder(row.getWorkOrderId()), row.getStatus(), row.getStatus(), userId,
                "发起满意度调研", request.getIdempotencyKey(), "SURVEY_REQUEST", fingerprint);
        publish(SCENE_SURVEY_REQUESTED, row, "survey:" + request.getIdempotencyKey(), userId, Map.of());
    }

    @Override
    public List<FeedbackConfigVO.Resp> getConfigs() {
        return feedbackConfigMapper.selectAll().stream().map(config -> {
            FeedbackConfigVO.Resp response = new FeedbackConfigVO.Resp();
            response.setFeedbackType(config.getFeedbackType());
            response.setFormId(config.getFormId());
            BpmFormMetadataRespDTO form = definitionReadApi.getForm(config.getFormId());
            response.setFormName(form == null ? null : form.getName());
            response.setTitleFieldKey(config.getTitleFieldKey());
            response.setDispatcherUserIds(parseLongs(config.getDispatcherUserIdsJson()));
            response.setApprovalEnabled(config.getApprovalEnabled());
            response.setBpmProcessDefinitionKey(config.getBpmProcessDefinitionKey());
            response.setVersion(config.getVersion());
            response.setIncompatibleFields(form == null ? List.of("表单不存在")
                    : dynamicFormService.parse(form).incompatibleFields());
            return response;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(FeedbackConfigVO.SaveReq request, Long userId) {
        requireConfigType(request.getFeedbackType());
        FeedbackConfigDO config = requireConfig(request.getFeedbackType());
        FeedbackDynamicFormService.ParsedForm form = dynamicFormService.requireCompatibleForm(
                request.getFormId(), request.getFeedbackType(), request.getTitleFieldKey());
        List<Long> dispatchers = TYPE_SURVEY.equals(request.getFeedbackType())
                ? List.of() : distinct(request.getDispatcherUserIds());
        Set<Long> eligible = eligibleHandlerIds(request.getFeedbackType());
        if (!eligible.containsAll(dispatchers)) throw exception(FEEDBACK_ASSIGNEE_INVALID);
        validateProcessBinding(request);
        String fingerprint = fingerprint("config", request.getFeedbackType(), request.getFormId(),
                request.getTitleFieldKey(), dispatchers, request.getApprovalEnabled(),
                request.getBpmProcessDefinitionKey());
        if (Objects.equals(config.getLastIdempotencyKey(), request.getIdempotencyKey())) {
            if (!Objects.equals(config.getLastRequestFingerprint(), fingerprint)) {
                throw exception(FEEDBACK_IDEMPOTENCY_CONFLICT);
            }
            return;
        }
        if (!Objects.equals(config.getVersion(), request.getVersion())) {
            throw exception(FEEDBACK_CONFIG_VERSION_CONFLICT);
        }
        config.setFormId(form.formId());
        config.setTitleFieldKey(request.getTitleFieldKey());
        config.setDispatcherUserIdsJson(JsonUtils.toJsonString(dispatchers));
        config.setApprovalEnabled(TYPE_REQUIREMENT.equals(request.getFeedbackType())
                && Boolean.TRUE.equals(request.getApprovalEnabled()));
        config.setBpmProcessDefinitionKey(TYPE_REQUIREMENT.equals(request.getFeedbackType())
                ? request.getBpmProcessDefinitionKey() : null);
        config.setLastIdempotencyKey(request.getIdempotencyKey());
        config.setLastRequestFingerprint(fingerprint);
        if (feedbackConfigMapper.updateById(config) != 1) throw exception(FEEDBACK_CONFIG_VERSION_CONFLICT);
    }

    @Override
    public List<FeedbackConfigVO.UserOption> getCandidates(String type) {
        requireSubmissionType(type);
        return adminUserApi.getUserList(eligibleHandlerIds(type)).stream()
                .filter(this::enabled)
                .map(user -> {
                    FeedbackConfigVO.UserOption option = new FeedbackConfigVO.UserOption();
                    option.setId(user.getId());
                    option.setNickname(user.getNickname());
                    return option;
                }).toList();
    }

    @Override
    public List<FeedbackConfigVO.FormOption> getFormOptions() {
        return dynamicFormService.getFormOptions();
    }

    @Override
    public List<FeedbackConfigVO.ProcessOption> getProcessOptions() {
        return definitionReadApi.getPublishedProcessDefinitions().stream()
                .filter(definition -> PROCESS_DEFINITION_KEY.equals(definition.getKey())
                        && !Boolean.TRUE.equals(definition.getSuspended()))
                .map(definition -> {
                    FeedbackConfigVO.ProcessOption option = new FeedbackConfigVO.ProcessOption();
                    option.setId(definition.getId());
                    option.setKey(definition.getKey());
                    option.setName(definition.getName());
                    option.setVersion(definition.getVersion());
                    return option;
                }).toList();
    }

    @Override
    public FileInfoRespDTO upload(byte[] content, String name, String contentType, Long userId) {
        return fileApi.createFileInfo(content, name, "zsjos/feedback/" + userId, contentType);
    }

    @Override
    public FileInfoRespDTO uploadForPartner(byte[] content, String name, String contentType, Long accountId) {
        return fileApi.createFileInfo(content, name, "zsjos/feedback/partner/" + accountId, contentType);
    }

    @Override
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "feedback.process-result", targetType = "feedback")
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(BpmProcessInstanceStatusEvent event) {
        FeedbackDO row = feedbackMapper.selectByProcessInstanceId(event.getId());
        if (row == null || !Objects.equals(row.getProcessInstanceId(), event.getId())) return;
        String nextStatus;
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(event.getStatus())) {
            nextStatus = STATUS_WAITING;
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(event.getStatus())
                || BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(event.getStatus())) {
            nextStatus = STATUS_APPROVAL_REJECTED;
        } else {
            return;
        }
        if (Objects.equals(row.getStatus(), nextStatus)) return;
        row.setStatus(nextStatus);
        row.setRejectReason(STATUS_APPROVAL_REJECTED.equals(nextStatus) ? event.getReason() : null);
        row.setLastActivityAt(LocalDateTime.now());
        row.setUnreadForSubmitter(true);
        feedbackMapper.updateById(row);
        FeedbackRoundDO round = roundMapper.selectByProcessInstanceId(event.getId());
        if (round != null) {
            round.setStatus(nextStatus);
            round.setRejectReason(row.getRejectReason());
            roundMapper.updateById(round);
        }
        syncWorkOrder(row, nextStatus, null, null, null, null);
        if (STATUS_WAITING.equals(nextStatus)) {
            publishReadyForHandling(row, null, feedbackConfigMapper.selectByType(row.getFeedbackType()));
        }
    }

    private void reply(Long id, FeedbackActionVO.ReplyReq request, Long userId, String authorType) {
        reply(id, request, userId, authorType, null);
    }

    private void reply(Long id, FeedbackActionVO.ReplyReq request, Long userId, String authorType,
                       Long partnerId) {
        FeedbackDO row = requireLocked(id);
        boolean partnerAuthor = AUTHOR_PARTNER_ACCOUNT.equals(authorType);
        String subjectType = partnerAuthor ? SUBJECT_PARTNER_ACCOUNT : SUBJECT_ADMIN;
        String authorName;
        if (partnerAuthor) {
            requirePartnerFeedback(row, userId, partnerId);
            PartnerDO partner = requireEnabledPartner(partnerId);
            authorName = partner.getName();
        } else {
            authorName = requireEnabledUser(userId).getNickname();
        }
        List<Long> attachmentIds = validateCommandAttachments(request.getAttachmentIds(), userId);
        String fingerprint = fingerprint("reply", authorType, id, userId, request.getVersion(),
                request.getContent().trim(), attachmentIds);
        FeedbackReplyDO existing = replyMapper.selectByFeedbackAndKey(id, request.getIdempotencyKey());
        if (existing != null) {
            WorkOrderHistoryDO history = historyMapper.selectByOrderAndKey(row.getWorkOrderId(),
                    request.getIdempotencyKey());
            if (history == null || !Objects.equals(history.getRequestFingerprint(), fingerprint)
                    || !Objects.equals(history.getOperatorSubjectType(), subjectType)
                    || !Objects.equals(history.getOperatorUserId(), userId)) {
                throw exception(FEEDBACK_IDEMPOTENCY_CONFLICT);
            }
            return;
        }
        validateVersion(row, request.getVersion());
        if (AUTHOR_ADMIN.equals(authorType)) {
            if (row.getAssigneeUserId() == null
                    || !Set.of(STATUS_IN_PROGRESS, STATUS_COMPLETED).contains(row.getStatus())) {
                throw exception(FEEDBACK_STATE_INVALID);
            }
        } else if (!Set.of(STATUS_WAITING, STATUS_IN_PROGRESS, STATUS_COMPLETED).contains(row.getStatus())) {
            throw exception(FEEDBACK_STATE_INVALID);
        }
        FeedbackReplyDO reply = new FeedbackReplyDO();
        reply.setFeedbackId(id);
        reply.setAuthorUserId(userId);
        reply.setAuthorNameSnapshot(authorName);
        reply.setAuthorType(authorType);
        reply.setContent(request.getContent().trim());
        reply.setAttachmentIdsJson(JsonUtils.toJsonString(attachmentIds));
        reply.setIdempotencyKey(request.getIdempotencyKey());
        replyMapper.insert(reply);
        row.setLastReplySummary(abbreviate(request.getContent()));
        row.setLastActivityAt(LocalDateTime.now());
        if (AUTHOR_ADMIN.equals(authorType)) {
            row.setUnreadForSubmitter(true);
        } else {
            row.setUnreadForAssignee(true);
        }
        if (feedbackMapper.updateById(row) != 1) throw exception(FEEDBACK_VERSION_CONFLICT);
        addHistory(requireWorkOrder(row.getWorkOrderId()), row.getStatus(), row.getStatus(), subjectType, userId,
                abbreviate(request.getContent()), request.getIdempotencyKey(), "REPLY", fingerprint);
        if (AUTHOR_ADMIN.equals(authorType)) {
            publish(SCENE_ADMIN_REPLIED, row, "admin-reply:" + request.getIdempotencyKey(), userId, Map.of());
        } else {
            publish(SCENE_EMPLOYEE_REPLIED, row, "employee-reply:" + request.getIdempotencyKey(), userId,
                    Map.of("dispatcherUserIds", effectiveDispatchers(requireConfig(row.getFeedbackType()))));
        }
    }

    private void createRequirementRound(FeedbackDO row, FeedbackConfigDO config,
                                        FeedbackDynamicFormService.ParsedForm form,
                                        Map<String, Object> values, Long userId,
                                        AdminUserRespDTO submitter, LocalDateTime now) {
        ApprovalContext approval = Boolean.TRUE.equals(config.getApprovalEnabled())
                ? resolveApprovalContext(submitter, config) : ApprovalContext.disabled();
        String businessKey = "feedback:" + row.getWorkOrderId() + ":round:" + row.getApprovalRoundNo();
        FeedbackRoundDO round = new FeedbackRoundDO();
        round.setFeedbackId(row.getId());
        round.setRoundNo(row.getApprovalRoundNo());
        round.setStatus(row.getStatus());
        round.setFormSnapshotJson(JsonUtils.toJsonString(form.fields()));
        round.setValueSnapshotJson(JsonUtils.toJsonString(values));
        round.setApprovalContextJson(JsonUtils.toJsonString(approval.snapshot()));
        round.setBusinessKey(businessKey);
        round.setSubmittedAt(now);
        roundMapper.insert(round);
        if (!Boolean.TRUE.equals(config.getApprovalEnabled())) return;

        BpmProcessInstanceCreateReqDTO processRequest = new BpmProcessInstanceCreateReqDTO();
        processRequest.setProcessDefinitionKey(config.getBpmProcessDefinitionKey());
        processRequest.setBusinessKey(businessKey);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("feedbackId", row.getId());
        variables.put("workOrderId", row.getWorkOrderId());
        variables.put("feedbackNo", row.getFeedbackNo());
        variables.put("roundNo", row.getApprovalRoundNo());
        variables.put("hasDepartmentLeader", approval.departmentLeaderId() != null);
        variables.put("departmentLeaderUsers", approval.departmentLeaderId() == null
                ? List.of() : List.of(approval.departmentLeaderId()));
        variables.put("departmentLeaderAssignee", approval.departmentLeaderId());
        variables.put("chairmanUsers", List.of(approval.chairmanId()));
        variables.put("chairmanAssignee", approval.chairmanId());
        processRequest.setVariables(variables);
        processRequest.setStartUserSelectAssignees(Map.of(
                TASK_DEPARTMENT_LEADER, approval.departmentLeaderId() == null
                        ? List.of() : List.of(approval.departmentLeaderId()),
                TASK_CHAIRMAN, List.of(approval.chairmanId())));
        String processInstanceId;
        try {
            processInstanceId = processInstanceApi.createProcessInstance(userId, processRequest);
        } catch (RuntimeException error) {
            throw exception(FEEDBACK_PROCESS_UNAVAILABLE);
        }
        round.setProcessInstanceId(processInstanceId);
        roundMapper.updateById(round);
        FeedbackDO update = feedbackMapper.selectById(row.getId());
        update.setProcessInstanceId(processInstanceId);
        feedbackMapper.updateById(update);
        row.setProcessInstanceId(processInstanceId);
    }

    private ApprovalContext resolveApprovalContext(AdminUserRespDTO submitter, FeedbackConfigDO config) {
        if (!PROCESS_DEFINITION_KEY.equals(config.getBpmProcessDefinitionKey())) {
            throw exception(FEEDBACK_PROCESS_UNAVAILABLE);
        }
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                config.getBpmProcessDefinitionKey());
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())) {
            throw exception(FEEDBACK_PROCESS_UNAVAILABLE);
        }
        AdminUserRespDTO departmentLeader = null;
        DeptRespDTO department = submitter.getDeptId() == null ? null : deptApi.getDept(submitter.getDeptId());
        if (department != null && department.getLeaderUserId() != null) {
            AdminUserRespDTO candidate = adminUserApi.getUser(department.getLeaderUserId());
            if (enabled(candidate)) departmentLeader = candidate;
        }
        RoleRespDTO chairmanRole = roleApi.getRoleByCode(ROLE_CHAIRMAN);
        if (chairmanRole == null || !CommonStatusEnum.ENABLE.getStatus().equals(chairmanRole.getStatus())) {
            throw exception(FEEDBACK_CHAIRMAN_INVALID);
        }
        List<AdminUserRespDTO> chairmen = adminUserApi.getUserList(
                        permissionApi.getUserRoleIdListByRoleIds(Set.of(chairmanRole.getId()))).stream()
                .filter(this::enabled).toList();
        if (chairmen.size() != 1) throw exception(FEEDBACK_CHAIRMAN_INVALID);
        AdminUserRespDTO chairman = chairmen.getFirst();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("processDefinitionId", definition.getId());
        snapshot.put("processDefinitionKey", definition.getKey());
        snapshot.put("processDefinitionVersion", definition.getVersion());
        snapshot.put("departmentLeaderUserId", departmentLeader == null ? null : departmentLeader.getId());
        snapshot.put("departmentLeaderName", departmentLeader == null ? null : departmentLeader.getNickname());
        snapshot.put("chairmanUserId", chairman.getId());
        snapshot.put("chairmanName", chairman.getNickname());
        return new ApprovalContext(departmentLeader == null ? null : departmentLeader.getId(),
                chairman.getId(), snapshot);
    }

    private FeedbackRespVO.Entry portalEntry(String type, String title, String description) {
        FeedbackRespVO.Entry entry = new FeedbackRespVO.Entry();
        entry.setFeedbackType(type);
        entry.setTitle(title);
        entry.setDescription(description);
        FeedbackConfigDO config = feedbackConfigMapper.selectByType(type);
        boolean open = config != null && !effectiveDispatchers(config).isEmpty();
        entry.setOpen(open);
        entry.setUnavailableReason(open ? null : "暂未开放");
        return entry;
    }

    private FeedbackRespVO.Entry partnerPortalEntry(String type, String title, String description) {
        FeedbackRespVO.Entry entry = portalEntry(type, title, description);
        FeedbackConfigDO config = feedbackConfigMapper.selectByType(type);
        if (TYPE_REQUIREMENT.equals(type) && config != null && Boolean.TRUE.equals(config.getApprovalEnabled())) {
            entry.setOpen(false);
            entry.setUnavailableReason("需求反馈暂不支持兼职端发起审批");
        }
        return entry;
    }

    private FeedbackRespVO toCard(FeedbackDO row, Long userId, boolean admin) {
        FeedbackRespVO result = new FeedbackRespVO();
        result.setId(row.getId());
        result.setFeedbackType(row.getFeedbackType());
        result.setFeedbackNo(row.getFeedbackNo());
        result.setTitle(row.getTitle());
        result.setStatus(row.getStatus());
        result.setSubmitterUserId(row.getSubmitterUserId());
        result.setSubmitterName(row.getSubmitterNameSnapshot());
        result.setAssigneeUserId(row.getAssigneeUserId());
        result.setAssigneeName(row.getAssigneeNameSnapshot());
        result.setLatestReplySummary(row.getLastReplySummary());
        result.setLastActivityAt(row.getLastActivityAt());
        result.setUnread(admin ? Boolean.TRUE.equals(row.getUnreadForAssignee())
                : Boolean.TRUE.equals(row.getUnreadForSubmitter()));
        result.setVersion(row.getVersion());
        result.setCreateTime(row.getCreateTime());
        applyActions(result, row, userId, admin);
        return result;
    }

    private FeedbackRespVO toDetail(FeedbackDO row, Long userId, boolean admin) {
        FeedbackRespVO result = toCard(row, userId, admin);
        result.setFormId(row.getFormId());
        result.setFields(dynamicFormService.parseSnapshot(row.getFormSnapshotJson()));
        result.setValues(dynamicFormService.readDisplayValues(row.getValueSnapshotJson(), result.getFields()));
        result.setSupportTypeValue(row.getSupportTypeValue());
        result.setSupportTypeLabel(row.getSupportTypeLabelSnapshot());
        result.setProcessInstanceId(row.getProcessInstanceId());
        result.setApprovalRoundNo(row.getApprovalRoundNo());
        result.setRejectReason(row.getRejectReason());
        result.setCompletedResult(row.getCompletedResult());
        result.setResultAttachmentIds(parseLongs(row.getResultAttachmentIdsJson()));
        result.setReplies(replyMapper.selectByFeedbackId(row.getId()).stream().map(reply -> {
            FeedbackRespVO.Reply response = new FeedbackRespVO.Reply();
            response.setId(reply.getId());
            response.setAuthorUserId(reply.getAuthorUserId());
            response.setAuthorName(reply.getAuthorNameSnapshot());
            response.setAuthorType(reply.getAuthorType());
            response.setContent(reply.getContent());
            response.setAttachmentIds(parseLongs(reply.getAttachmentIdsJson()));
            response.setCreateTime(reply.getCreateTime());
            return response;
        }).toList());
        List<Long> attachmentIds = new ArrayList<>(result.getResultAttachmentIds());
        result.getReplies().forEach(reply -> attachmentIds.addAll(reply.getAttachmentIds()));
        Map<Long, FeedbackRespVO.Attachment> attachments = toAttachments(attachmentIds);
        result.setResultAttachments(result.getResultAttachmentIds().stream().map(attachments::get).toList());
        result.getReplies().forEach(reply -> reply.setAttachments(
                reply.getAttachmentIds().stream().map(attachments::get).toList()));
        FeedbackSurveyDO survey = surveyMapper.selectByFeedbackId(row.getId());
        if (survey != null) {
            FeedbackRespVO.Survey response = new FeedbackRespVO.Survey();
            response.setStatus(survey.getStatus());
            response.setFormId(survey.getFormId());
            response.setFields(dynamicFormService.parseSnapshot(survey.getFormSnapshotJson()));
            response.setValues(dynamicFormService.readDisplayValues(survey.getValueSnapshotJson(), response.getFields()));
            response.setRequestedAt(survey.getRequestedAt());
            response.setSubmittedAt(survey.getSubmittedAt());
            result.setSurvey(response);
        }
        return result;
    }

    private Map<Long, FeedbackRespVO.Attachment> toAttachments(Collection<Long> ids) {
        Map<Long, FeedbackRespVO.Attachment> attachments = new LinkedHashMap<>();
        List<Long> availableIds = new ArrayList<>();
        for (Long id : ids.stream().distinct().toList()) {
            FeedbackRespVO.Attachment attachment = new FeedbackRespVO.Attachment();
            attachment.setId(id);
            attachments.put(id, attachment);
            FileInfoRespDTO file;
            try {
                file = fileApi.getFileInfo(id);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (file != null) {
                attachment.setName(file.getName());
                attachment.setType(file.getType());
                attachment.setSize(file.getSize());
                availableIds.add(id);
            }
        }
        FeedbackFileUrls.resolve(fileApi, availableIds).forEach((id, url) -> attachments.get(id).setUrl(url));
        return attachments;
    }

    private void applyActions(FeedbackRespVO result, FeedbackDO row, Long userId, boolean admin) {
        boolean completed = STATUS_COMPLETED.equals(row.getStatus());
        result.setCanResubmit(!admin && SUBJECT_ADMIN.equals(row.getSubmitterSubjectType())
                && TYPE_REQUIREMENT.equals(row.getFeedbackType())
                && STATUS_APPROVAL_REJECTED.equals(row.getStatus()));
        result.setCanReply(admin
                ? row.getAssigneeUserId() != null
                    && Set.of(STATUS_IN_PROGRESS, STATUS_COMPLETED).contains(row.getStatus())
                : Set.of(STATUS_WAITING, STATUS_IN_PROGRESS, STATUS_COMPLETED).contains(row.getStatus()));
        result.setCanComplete(admin && row.getAssigneeUserId() != null
                && STATUS_IN_PROGRESS.equals(row.getStatus()));
        FeedbackSurveyDO survey = completed ? surveyMapper.selectByFeedbackId(row.getId()) : null;
        result.setCanSurvey(admin && completed && survey == null);
        result.setCanSubmitSurvey(!admin && survey != null && SURVEY_PENDING.equals(survey.getStatus())
                && Objects.equals(row.getSubmitterUserId(), userId));
    }

    private void applySupportSnapshot(FeedbackDO feedback, Map<String, Object> normalizedValues) {
        if (!TYPE_SUPPORT.equals(feedback.getFeedbackType())) return;
        Object value = normalizedValues.get("supportType");
        if (value instanceof Map<?, ?> snapshot) {
            feedback.setSupportDictType(Objects.toString(snapshot.get("type"), null));
            feedback.setSupportTypeValue(Objects.toString(snapshot.get("value"), null));
            feedback.setSupportTypeLabelSnapshot(Objects.toString(snapshot.get("label"), null));
        }
    }

    private List<Long> effectiveDispatchers(FeedbackConfigDO config) {
        Set<Long> eligible = eligibleHandlerIds(config.getFeedbackType());
        return parseLongs(config.getDispatcherUserIdsJson()).stream().filter(eligible::contains).toList();
    }

    private Set<Long> eligibleHandlerIds(String type) {
        String permission = TYPE_PERMISSION.get(type);
        return permission == null ? Set.of() : permissionApi.getEnabledUserIdsByPermission(permission);
    }

    private AdminUserRespDTO requireEligibleHandler(String type, Long userId) {
        if (!eligibleHandlerIds(type).contains(userId)) throw exception(FEEDBACK_ASSIGNEE_INVALID);
        return requireEnabledUser(userId);
    }

    private void requirePartnerFeedback(FeedbackDO row, Long accountId, Long partnerId) {
        if (!SUBJECT_PARTNER_ACCOUNT.equals(row.getSubmitterSubjectType())
                || !Objects.equals(row.getSubmitterUserId(), accountId)
                || !Objects.equals(row.getPartnerId(), partnerId)) {
            throw exception(FEEDBACK_PERMISSION_DENIED);
        }
    }

    private PartnerDO requireEnabledPartner(Long partnerId) {
        PartnerDO partner = partnerMapper.selectById(partnerId);
        if (partner == null) throw exception(PARTNER_NOT_EXISTS);
        if (!PARTNER_STATUS_ENABLED.equals(partner.getStatus())) throw exception(PARTNER_ACCOUNT_DISABLED);
        return partner;
    }

    private AdminUserRespDTO requireEnabledUser(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (!enabled(user)) throw exception(FEEDBACK_ASSIGNEE_INVALID);
        return user;
    }

    private boolean enabled(AdminUserRespDTO user) {
        return user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus());
    }

    private void validateProcessBinding(FeedbackConfigVO.SaveReq request) {
        if (!TYPE_REQUIREMENT.equals(request.getFeedbackType())
                || !Boolean.TRUE.equals(request.getApprovalEnabled())) {
            return;
        }
        if (!PROCESS_DEFINITION_KEY.equals(request.getBpmProcessDefinitionKey())) {
            throw exception(FEEDBACK_PROCESS_UNAVAILABLE);
        }
        BpmProcessDefinitionMetadataRespDTO definition = definitionReadApi.getPublishedProcessDefinition(
                request.getBpmProcessDefinitionKey());
        if (definition == null || Boolean.TRUE.equals(definition.getSuspended())) {
            throw exception(FEEDBACK_PROCESS_UNAVAILABLE);
        }
    }

    private List<Long> validateCommandAttachments(Collection<Long> ids, Long userId) {
        if (ids == null) return List.of();
        List<Long> normalized = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (normalized.size() > 20) throw exception(FEEDBACK_STATE_INVALID);
        for (Long id : normalized) {
            FileInfoRespDTO file;
            try {
                file = fileApi.getFileInfo(id);
            } catch (RuntimeException error) {
                throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_ATTACHMENT_INVALID);
            }
            if (file == null || file.getPath() == null || !file.getPath().startsWith("zsjos/feedback/")
                    || !String.valueOf(userId).equals(file.getCreator())) {
                throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_ATTACHMENT_INVALID);
            }
        }
        return normalized;
    }

    private void syncWorkOrder(FeedbackDO feedback, String status, String fieldsJson, String valuesJson,
                               List<Long> attachmentIds, AdminUserRespDTO assignee) {
        WorkOrderDO workOrder = requireWorkOrder(feedback.getWorkOrderId());
        if (status != null) workOrder.setStatus(status);
        if (fieldsJson != null) workOrder.setFieldSnapshotJson(fieldsJson);
        if (valuesJson != null) workOrder.setValueJson(valuesJson);
        if (attachmentIds != null) workOrder.setAttachmentIdsJson(JsonUtils.toJsonString(attachmentIds));
        if (assignee != null) {
            workOrder.setTargetUserId(assignee.getId());
            workOrder.setTargetNameSnapshot(assignee.getNickname());
            workOrder.setClaimedAt(LocalDateTime.now());
        }
        if (STATUS_COMPLETED.equals(status)) workOrder.setCompletedAt(LocalDateTime.now());
        workOrderMapper.updateById(workOrder);
    }

    private WorkOrderDO requireWorkOrder(Long id) {
        WorkOrderDO row = workOrderMapper.selectById(id);
        if (row == null || !BUSINESS_TYPE_FEEDBACK.equals(row.getBusinessType())) {
            throw exception(FEEDBACK_NOT_EXISTS);
        }
        return row;
    }

    private void publish(String scene, FeedbackDO row, String eventKey, Long operatorUserId,
                         Map<String, Object> extraPayload) {
        Map<String, Object> payload = new LinkedHashMap<>(extraPayload);
        payload.put("feedbackNo", row.getFeedbackNo());
        payload.put("feedbackTitle", row.getTitle());
        payload.put("submitterSubjectType", row.getSubmitterSubjectType());
        payload.put("submitterUserId", row.getSubmitterUserId());
        payload.put("partnerId", row.getPartnerId());
        payload.put("assigneeUserId", row.getAssigneeUserId());
        payload.put("deepLink", "/zsjos/feedback?feedbackId=" + row.getId());
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId())
                .sceneCode(scene)
                .sourceEventKey("feedback:" + row.getId() + ":" + eventKey)
                .bizType("feedback")
                .bizId(row.getId())
                .operatorUserId(operatorUserId)
                .occurredAt(LocalDateTime.now())
                .payload(payload)
                .build());
    }

    private void publishReadyForHandling(FeedbackDO row, Long operatorUserId, FeedbackConfigDO config) {
        List<Long> dispatchers = config == null ? List.of() : effectiveDispatchers(config);
        publish(NOTIFY_SCENE_READY_FOR_HANDLING, row,
                "ready:round:" + row.getApprovalRoundNo(), operatorUserId,
                Map.of("dispatcherUserIds", dispatchers));
    }

    private boolean exactReplay(FeedbackDO feedback, String key, String operation,
                                Long userId, String fingerprint) {
        return exactReplay(feedback, key, operation, SUBJECT_ADMIN, userId, fingerprint);
    }

    private boolean exactReplay(FeedbackDO feedback, String key, String operation,
                                String subjectType, Long userId, String fingerprint) {
        WorkOrderHistoryDO replay = historyMapper.selectByOrderAndKey(feedback.getWorkOrderId(), key);
        if (replay == null) return false;
        if (!Objects.equals(replay.getOperation(), operation)
                || !Objects.equals(replay.getOperatorSubjectType(), subjectType)
                || !Objects.equals(replay.getOperatorUserId(), userId)
                || !Objects.equals(replay.getRequestFingerprint(), fingerprint)) {
            throw exception(FEEDBACK_IDEMPOTENCY_CONFLICT);
        }
        return true;
    }

    private void addHistory(WorkOrderDO workOrder, String from, String to, Long userId, String reason,
                            String key, String operation, String fingerprint) {
        addHistory(workOrder, from, to, SUBJECT_ADMIN, userId, reason, key, operation, fingerprint);
    }

    private void addHistory(WorkOrderDO workOrder, String from, String to, String subjectType,
                            Long userId, String reason, String key, String operation, String fingerprint) {
        WorkOrderHistoryDO history = new WorkOrderHistoryDO();
        history.setWorkOrderId(workOrder.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperatorSubjectType(subjectType);
        history.setOperatorUserId(userId);
        history.setReason(reason);
        history.setOperatedAt(LocalDateTime.now());
        history.setIdempotencyKey(key);
        history.setOperation(operation);
        history.setRequestFingerprint(fingerprint);
        historyMapper.insert(history);
    }

    private Long requireCreateReplay(WorkOrderDO existing, String fingerprint, Long userId) {
        return requireCreateReplay(existing, fingerprint, SUBJECT_ADMIN, userId);
    }

    private Long requireCreateReplay(WorkOrderDO existing, String fingerprint, String subjectType, Long userId) {
        if (!BUSINESS_TYPE_FEEDBACK.equals(existing.getBusinessType())
                || !Objects.equals(existing.getCommandSubjectType(), subjectType)
                || !Objects.equals(existing.getCommandUserId(), userId)
                || !Objects.equals(existing.getRequestFingerprint(), fingerprint)) {
            throw exception(FEEDBACK_IDEMPOTENCY_CONFLICT);
        }
        FeedbackDO feedback = feedbackMapper.selectByWorkOrderId(existing.getId());
        if (feedback == null) throw exception(FEEDBACK_IDEMPOTENCY_CONFLICT);
        return feedback.getId();
    }

    private String nextFeedbackNo(String type) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDate today = LocalDate.now();
        String prefix = TYPE_PREFIX.get(type) + "-" + NUMBER_DATE.format(today) + "-";
        long minimumValue = workOrderMapper.selectMaxFeedbackNumber(
                tenantId, type, "^" + prefix + "[0-9]+$") + 1;
        counterMapper.reserve(tenantId, today, type, minimumValue);
        long sequence = counterMapper.selectReservedValue(tenantId, today, type);
        return prefix + String.format("%04d", sequence);
    }

    private FeedbackDO require(Long id) {
        FeedbackDO row = feedbackMapper.selectById(id);
        if (row == null) throw exception(FEEDBACK_NOT_EXISTS);
        return row;
    }

    private FeedbackDO requireLocked(Long id) {
        FeedbackDO row = feedbackMapper.selectByIdForUpdate(id);
        if (row == null) throw exception(FEEDBACK_NOT_EXISTS);
        return row;
    }

    private FeedbackConfigDO requireConfig(String type) {
        FeedbackConfigDO config = feedbackConfigMapper.selectByType(type);
        if (config == null) throw exception(FEEDBACK_NOT_OPEN);
        return config;
    }

    private void validateConfigVersion(FeedbackConfigDO config, Integer version) {
        if (!Objects.equals(config.getVersion(), version)) throw exception(FEEDBACK_CONFIG_VERSION_CONFLICT);
    }

    private void validateVersion(FeedbackDO row, Integer version) {
        if (!Objects.equals(row.getVersion(), version)) throw exception(FEEDBACK_VERSION_CONFLICT);
    }

    private void requireSubmissionType(String type) {
        if (!SUBMISSION_TYPES.contains(type)) throw exception(FEEDBACK_TYPE_INVALID);
    }

    private void validateOptionalSubmissionType(String type) {
        if (type != null && !type.isBlank()) requireSubmissionType(type);
    }

    private void requireConfigType(String type) {
        if (!SUBMISSION_TYPES.contains(type) && !TYPE_SURVEY.equals(type)) {
            throw exception(FEEDBACK_TYPE_INVALID);
        }
    }

    private List<Long> parseLongs(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.parseArray(json, Long.class);
    }

    private List<Long> distinct(Collection<Long> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String fingerprint(Object... values) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(values)));
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private record ApprovalContext(Long departmentLeaderId, Long chairmanId,
                                   Map<String, Object> snapshot) {
        private static ApprovalContext disabled() {
            return new ApprovalContext(null, null, Map.of("approvalEnabled", false));
        }
    }
}
