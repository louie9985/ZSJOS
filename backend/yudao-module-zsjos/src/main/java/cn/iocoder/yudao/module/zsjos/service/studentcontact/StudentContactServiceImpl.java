package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorConfigService;
import cn.iocoder.yudao.module.zsjos.service.director.DirectorFormTemplateService;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.io.IOException;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.*;

@Service
@Slf4j
public class StudentContactServiceImpl implements StudentContactService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private StudentContactRecordMapper recordMapper;
    @Resource private StudentContactExtensionMapper extensionMapper;
    @Resource private StudentCollaboratorAssignmentLogMapper assignmentLogMapper;
    @Resource private StudentContactConfigVersionMapper studentContactConfigVersionMapper;
    @Resource private StudentContactConfigService configService;
    @Resource private BusinessTaskMapper taskMapper;
    @Resource private BusinessTaskCommandService taskCommandService;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadAssignmentRelationMapper userRelationMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PermissionApi permissionApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private FileApi fileApi;
    @Resource private PersonMapper personMapper;
    @Resource private PersonIdentityWriteService personIdentityWriteService;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private StudentContactNotifyPublisher studentContactNotifyPublisher;
    @Resource private DirectorFormTemplateService directorFormTemplateService;
    @Resource private DirectorConfigService directorConfigService;
    @Resource private PositioningCardMapper positioningCardMapper;
    @Resource private MediaAccountMapper mediaAccountMapper;

    @Override
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "read")
    public StudentContactContextRespVO getContext(Long relationId, Long userId) {
        ServiceRelationDO relation = requireReadable(relationId, userId);
        StudentContactContextRespVO result = new StudentContactContextRespVO();
        result.setServiceRelationId(relationId); result.setAcceptanceStatus(relation.getAcceptanceStatus());
        result.setAcceptedAt(relation.getAcceptedAt()); result.setVersion(relation.getVersion());
        BusinessTaskDO task = currentTask(relationId);
        StudentContactConfigVersionDO config = task == null ? configService.requirePublished() : configFromTask(task);
        result.setFirstContactChecklist(checklist(config));
        result.setQuickNotes(JsonUtils.parseArray(config.getQuickNotesJson(), String.class));
        result.setFirstContactTimeoutMinutes(config.getFirstContactTimeoutMinutes());
        result.setStudyPlanTimeoutMinutes(config.getStudyPlanTimeoutMinutes());
        boolean owner = Objects.equals(relation.getOwnerUserId(), userId);
        boolean operational = "active".equals(relation.getStatus());
        if (owner) result.setVisibleTabs(List.of("overview", "first-contact", "study-plan", "contacts"));
        else {
            String type = Objects.equals(relation.getContentDirectorUserId(), userId) ? COLLABORATOR_DIRECTOR
                    : Objects.equals(relation.getCareerPlannerUserId(), userId) ? COLLABORATOR_CAREER
                    : COLLABORATOR_OPERATOR;
            Map<?, ?> tabs = JsonUtils.parseObject(config.getCollaboratorTabsJson(), Map.class);
            List<String> visible = new ArrayList<>(List.of("overview"));
            Object configured = tabs == null ? null : tabs.get(type);
            if (configured instanceof Collection<?> values) values.stream().map(String::valueOf).forEach(visible::add);
            result.setVisibleTabs(visible);
        }
        List<String> availableActions = new ArrayList<>();
        boolean accepted = "accepted".equals(relation.getAcceptanceStatus());
        if (operational && owner && !accepted && permissionApi.hasAnyPermissions(userId, PERMISSION_ACCEPT)) {
            availableActions.add(CONTEXT_ACTION_ACCEPT);
        }
        if (operational && owner && accepted && task != null && "pending".equals(task.getStatus())) {
            String permission = switch (task.getTaskType()) {
                case TYPE_FIRST_CONTACT -> PERMISSION_FIRST_CONTACT_SUBMIT;
                case TYPE_STUDY_PLAN -> PERMISSION_STUDY_PLAN_SUBMIT;
                case TYPE_CONTACT -> PERMISSION_CONTACT_SUBMIT;
                default -> null;
            };
            String action = switch (task.getTaskType()) {
                case TYPE_FIRST_CONTACT -> CONTEXT_ACTION_FIRST_CONTACT;
                case TYPE_STUDY_PLAN -> CONTEXT_ACTION_STUDY_PLAN;
                case TYPE_CONTACT -> CONTEXT_ACTION_FOLLOW_UP;
                default -> null;
            };
            if (permission != null && action != null && permissionApi.hasAnyPermissions(userId, permission)) {
                availableActions.add(action);
            }
        }
        if (operational && owner && accepted && permissionApi.hasAnyPermissions(userId, PERMISSION_UPDATE_BASIC_INFO)) {
            availableActions.add(CONTEXT_ACTION_EDIT_BASIC_INFO);
        }
        boolean canAssign = operational && accepted
                && ((owner && permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_ASSIGN))
                || permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT));
        boolean ownerCanAssign = operational && owner && permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_ASSIGN);
        if (canAssign && (relation.getContentDirectorUserId() == null
                || ownerCanAssign || permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT))) {
            availableActions.add(CONTEXT_ACTION_ASSIGN_CONTENT_DIRECTOR);
        }
        if (canAssign && (relation.getCareerPlannerUserId() == null
                || ownerCanAssign || permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT))) {
            availableActions.add(CONTEXT_ACTION_ASSIGN_CAREER_PLANNER);
        }
        boolean director = Objects.equals(relation.getContentDirectorUserId(), userId);
        String directorStage = StrUtil.blankToDefault(relation.getDirectorStage(), "precheck");
        if (operational && accepted && director && "precheck".equals(directorStage)
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DIRECTOR_PRECHECK)) {
            availableActions.add(CONTEXT_ACTION_DIRECTOR_PRECHECK);
        }
        if (operational && accepted && director && "interview".equals(directorStage)
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DIRECTOR_INTERVIEW)) {
            availableActions.add(CONTEXT_ACTION_DIRECTOR_INTERVIEW);
        }
        if (operational && accepted && director
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DIRECTOR_OPERATOR_ASSIGN)) {
            availableActions.add(CONTEXT_ACTION_ASSIGN_OPERATOR);
        }
        result.setAvailableActions(availableActions);
        if (task != null) {
            StudentContactContextRespVO.CurrentTaskVO row = new StudentContactContextRespVO.CurrentTaskVO();
            row.setId(task.getId()); row.setType(task.getTaskType()); row.setStatus(task.getStatus()); row.setDueAt(task.getDueAt());
            row.setOverdue(task.getDueAt() != null && task.getDueAt().isBefore(LocalDateTime.now())); result.setCurrentTask(row);
        }
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(List.of(
                relation.getOwnerUserId() == null ? -1L : relation.getOwnerUserId(),
                relation.getContentDirectorUserId() == null ? -1L : relation.getContentDirectorUserId(),
                relation.getCareerPlannerUserId() == null ? -1L : relation.getCareerPlannerUserId(),
                relation.getOperatorUserId() == null ? -1L : relation.getOperatorUserId()));
        result.setOwnerUserId(relation.getOwnerUserId());
        result.setOwnerUserName(name(users.get(relation.getOwnerUserId())));
        result.setContentDirectorUserId(relation.getContentDirectorUserId());
        result.setContentDirectorUserName(name(users.get(relation.getContentDirectorUserId())));
        result.setCareerPlannerUserId(relation.getCareerPlannerUserId());
        result.setCareerPlannerUserName(name(users.get(relation.getCareerPlannerUserId())));
        result.setOperatorUserId(relation.getOperatorUserId());
        result.setOperatorUserName(name(users.get(relation.getOperatorUserId())));
        result.setDirectorStage(directorStage);
        result.setDirectorInterviewAt(relation.getDirectorInterviewAt());
        result.setDirectorInterviewAppointmentHours(directorConfigService.interviewAppointmentHours());
        result.setDirectorTrialDays(directorConfigService.trialDays());
        result.setDefaultDirectorInterviewAt(LocalDateTime.now(BUSINESS_ZONE)
                .plusHours(directorConfigService.interviewAppointmentHours()));
        String stage = relation.getDeliveryStage();
        if (stage == null || stage.isBlank()) stage = STAGE_FIRST_CONTACT;
        result.setDeliveryStage(stage);
        result.setDeliveryStageLabel(stageLabel(stage));
        boolean canSubmitDeliveryStage = operational && owner && accepted
                && !Set.of(STAGE_FIRST_CONTACT, STAGE_STUDY_PLAN, STAGE_COMPLETED).contains(stage)
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DELIVERY_STAGE_SUBMIT);
        result.setDeliveryStages(deliveryStages(stage, canSubmitDeliveryStage));
        result.setExamDate(relation.getExamDate());
        result.setFormFields(director ? formFields(config, "director_" + directorStage, null)
                : formFields(config, stage, task));
        result.setDirectorForms(directorForms(relation, config));
        boolean operatorConflict = relation.getPersonId() != null
                && relationMapper.selectActiveByPersonIds(List.of(relation.getPersonId())).stream()
                .map(ServiceRelationDO::getOperatorUserId).filter(Objects::nonNull).distinct().limit(2).count() > 1;
        result.setOperatorAssignmentConflict(operatorConflict);
        if (operational && owner && accepted && permissionApi.hasAnyPermissions(userId, PERMISSION_UPDATE_EXAM_DATE)
                && Set.of(STAGE_SUPERVISION, STAGE_EXAM_PREPARATION).contains(stage)) {
            availableActions.add(CONTEXT_ACTION_UPDATE_EXAM_DATE);
        }
        if (operational && owner && accepted && Set.of(STAGE_EXAM_PREPARATION, STAGE_POST_EXAM).contains(stage)
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DELIVERY_STAGE_SUBMIT)) {
            availableActions.add(stage.equals(STAGE_EXAM_PREPARATION) ? CONTEXT_ACTION_EXAM_NOTICE_DONE : CONTEXT_ACTION_POST_EXAM_DONE);
        } else if (operational && owner && accepted && Set.of(STAGE_RESULT, STAGE_CERTIFICATE).contains(stage)
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DELIVERY_STAGE_SUBMIT)) {
            availableActions.add(CONTEXT_ACTION_COMPLETE_STAGE);
        } else if (operational && owner && accepted && STAGE_CONTINUOUS_FOLLOW_UP.equals(stage)
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DELIVERY_STAGE_SUBMIT)) {
            availableActions.add(CONTEXT_ACTION_END_SERVICE);
        }
        result.setAvailableActions(availableActions);
        return result;
    }

    @Override
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "read")
    public PageResult<StudentContactRecordRespVO> getRecords(Long relationId, PageParam page, Long userId) {
        requireReadable(relationId, userId);
        PageResult<StudentContactRecordDO> rows = recordMapper.selectPageByRelationId(page, relationId);
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(rows.getList().stream()
                .map(StudentContactRecordDO::getOperatorUserId).toList());
        return new PageResult<>(rows.getList().stream().map(row -> {
            StudentContactRecordRespVO result = new StudentContactRecordRespVO();
            result.setId(row.getId()); result.setContactType(row.getContactType()); result.setSuccessful(row.getSuccessful());
            result.setUnsuccessfulReasonValue(row.getUnsuccessfulReasonValue());
            result.setUnsuccessfulReasonLabel(row.getUnsuccessfulReasonLabelSnapshot()); result.setRemark(row.getRemark());
            result.setAttachmentFileIds(parseLongs(row.getAttachmentFileIdsJson()));
            result.setCompletedChecklistKeys(parseStrings(row.getChecklistResultJson())); result.setNextContactAt(row.getNextContactAt());
            result.setDeliveryStage(row.getDeliveryStage());
            result.setDeliveryData(row.getDeliveryDataJson());
            result.setOperatorUserId(row.getOperatorUserId()); result.setOperatorUserName(name(users.get(row.getOperatorUserId())));
            result.setSubmittedAt(row.getSubmittedAt()); return result;
        }).toList(), rows.getTotal());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "accept")
    public void accept(Long relationId, StudentServiceAcceptReqVO request, Long userId) {
        ServiceRelationDO relation = requireOwnedForUpdate(relationId, userId);
        if ("accepted".equals(relation.getAcceptanceStatus())) return;
        StudentContactConfigVersionDO config = configService.requirePublished();
        LocalDateTime now = LocalDateTime.now();
        if (relationMapper.accept(relationId, userId, now, request.getVersion()) != 1) {
            throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        }
        createTask(relation, TYPE_FIRST_CONTACT, now.plusMinutes(config.getFirstContactTimeoutMinutes()),
                "student-contact:accept:" + relationId, config.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "update-basic-info")
    public void updateBasicInfo(Long relationId, StudentBasicInfoUpdateReqVO request, Long userId) {
        ServiceRelationDO relation = requireOwnedForUpdate(relationId, userId);
        if (!"accepted".equals(relation.getAcceptanceStatus())) throw exception(STUDENT_SERVICE_NOT_ACCEPTED);
        String name = request.getName().trim();
        String mobile = StrUtil.trimToNull(request.getMobile());
        String wechatId = StrUtil.trimToNull(request.getWechatId());
        if (mobile == null && wechatId == null) throw exception(LEAD_CONTACT_REQUIRED);
        if (mobile != null && !ValidationUtils.isMobile(mobile)) throw exception(LEAD_MOBILE_INVALID);

        PersonDO before = personMapper.selectById(relation.getPersonId());
        if (before == null) throw exception(STUDENT_SERVICE_NOT_EXISTS);
        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(before.getName(), name)) changedFields.add("name");
        if (!Objects.equals(before.getMobile(), mobile)) changedFields.add("mobile");
        if (!Objects.equals(before.getWechatId(), wechatId)) changedFields.add("wechatId");
        personIdentityWriteService.update(relation.getPersonId(), name, mobile, wechatId);

        BusinessEventDO event = new BusinessEventDO();
        event.setEventType("student_basic_info_updated");
        event.setAggregateType(BIZ_TYPE);
        event.setAggregateId(relationId);
        event.setOperatorUserId(userId);
        event.setReason(request.getReason().trim());
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of(
                "serviceRelationId", relationId,
                "personId", relation.getPersonId(),
                "changedFields", changedFields)));
        event.setOccurredAt(LocalDateTime.now());
        event.setIdempotencyKey("student-basic-info:" + UUID.randomUUID());
        eventMapper.insert(event);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "contact")
    public Long submitFirstContact(Long relationId, StudentFirstContactSubmitReqVO request, Long userId) {
        return submit(relationId, request.getTaskId(), TYPE_FIRST_CONTACT, request.getSuccessful(),
                request.getUnsuccessfulReasonValue(), request.getRemark(), request.getAttachmentFileIds(),
                request.getCompletedChecklistKeys(), request.getNextContactAt(), request.getExtensionReasonValue(),
                request.getExtensionDescription(), request.getExtensionAttachmentFileIds(), request.getData(), request.getIdempotencyKey(), userId);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "contact")
    public Long submitStudyPlan(Long relationId, StudentStudyPlanSubmitReqVO request, Long userId) {
        return submit(relationId, request.getTaskId(), TYPE_STUDY_PLAN, request.getSuccessful(),
                request.getUnsuccessfulReasonValue(), request.getRemark(), request.getAttachmentFileIds(), null,
                request.getNextContactAt(), request.getExtensionReasonValue(), request.getExtensionDescription(),
                request.getExtensionAttachmentFileIds(), request.getData(), request.getIdempotencyKey(), userId);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "contact")
    public Long submitContact(Long relationId, StudentContactSubmitReqVO request, Long userId) {
        return submit(relationId, request.getTaskId(), TYPE_CONTACT, request.getSuccessful(),
                request.getUnsuccessfulReasonValue(), request.getRemark(), request.getAttachmentFileIds(), null,
                request.getNextContactAt(), null, null, null, request.getData(), request.getIdempotencyKey(), userId);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "delivery-stage")
    public Long submitDeliveryStage(Long relationId, StudentDeliveryStageSubmitReqVO request, Long userId) {
        if (!permissionApi.hasAnyPermissions(userId, PERMISSION_DELIVERY_STAGE_SUBMIT)) {
            throw exception(STUDENT_PERMISSION_DENIED);
        }
        ServiceRelationDO relation = requireOwnedForUpdate(relationId, userId);
        if (!"accepted".equals(relation.getAcceptanceStatus())) throw exception(STUDENT_SERVICE_NOT_ACCEPTED);
        String fingerprint = deliveryFingerprint(relationId, request);
        StudentContactRecordDO replay = recordMapper.selectByIdempotencyKey(request.getIdempotencyKey());
        if (replay != null) {
            if (!fingerprint.equals(replay.getRequestFingerprint()) || !relationId.equals(replay.getServiceRelationId())) {
                throw exception(STUDENT_CONTACT_FORM_INVALID);
            }
            return replay.getId();
        }
        List<String> stages = deliveryStageCodes();
        String current = relation.getDeliveryStage();
        boolean invalidPersistedStage = current == null || current.isBlank() || !stages.contains(current);
        if (invalidPersistedStage) current = STAGE_FIRST_CONTACT;
        if (!stages.contains(request.getStage()) || invalidPersistedStage || !request.getStage().equals(current)
                || Set.of(STAGE_FIRST_CONTACT, STAGE_STUDY_PLAN, STAGE_COMPLETED).contains(current)) {
            throw exception(STUDENT_CONTACT_TASK_INVALID);
        }
        validateAttachments(request.getAttachmentFileIds(), relationId, userId);
        if (Boolean.TRUE.equals(request.getSuccessful())) validateDeliveryData(request.getStage(), request.getData());
        String next = Boolean.TRUE.equals(request.getSuccessful()) ? stages.get(stages.indexOf(current) + 1 < stages.size() ? stages.indexOf(current) + 1 : stages.size() - 1) : current;
        LocalDateTime now = LocalDateTime.now();
        StudentContactRecordDO record = new StudentContactRecordDO();
        record.setServiceRelationId(relationId); record.setTaskId(null); record.setContactType(TYPE_DELIVERY_STAGE);
        record.setSuccessful(request.getSuccessful()); record.setRemark(request.getRemark().trim());
        List<Long> normalizedAttachments = normalizeAttachmentIds(request.getAttachmentFileIds());
        record.setAttachmentFileIdsJson(JsonUtils.toJsonString(normalizedAttachments));
        record.setChecklistResultJson("[]"); record.setDeliveryStage(request.getStage());
        record.setDeliveryDataJson(JsonUtils.toJsonString(request.getData() == null ? Map.of() : request.getData()));
        record.setNextContactAt(now); record.setOperatorUserId(userId); record.setSubmittedAt(now);
        record.setIdempotencyKey(request.getIdempotencyKey()); record.setRequestFingerprint(fingerprint);
        try { recordMapper.insert(record); } catch (DuplicateKeyException duplicate) {
            StudentContactRecordDO concurrent = recordMapper.selectByIdempotencyKey(request.getIdempotencyKey());
            if (concurrent == null) throw duplicate;
            if (!fingerprint.equals(concurrent.getRequestFingerprint())
                    || !relationId.equals(concurrent.getServiceRelationId())) throw exception(STUDENT_CONTACT_FORM_INVALID);
            return concurrent.getId();
        }
        if (Boolean.TRUE.equals(request.getSuccessful())
                && relationMapper.advanceDeliveryStage(relationId, userId, current, next,
                record.getDeliveryDataJson(), relation.getVersion()) != 1) {
            throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        }
        return record.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "update-exam-date")
    public void updateExamDate(Long relationId, StudentExamDateUpdateReqVO request, Long userId) {
        ServiceRelationDO relation = relationMapper.selectByIdForUpdate(relationId, TenantContextHolder.getRequiredTenantId());
        if (relation == null || !"active".equals(relation.getStatus())) throw exception(STUDENT_SERVICE_NOT_EXISTS);
        if (!Objects.equals(relation.getOwnerUserId(), userId)
                && !permissionApi.hasAnyPermissions(userId, PERMISSION_UPDATE_EXAM_DATE)) throw exception(STUDENT_PERMISSION_DENIED);
        if (!"accepted".equals(relation.getAcceptanceStatus())
                || !Set.of(STAGE_SUPERVISION, STAGE_EXAM_PREPARATION).contains(relation.getDeliveryStage())) {
            throw exception(STUDENT_CONTACT_TASK_INVALID);
        }
        if (request.getExamDate().isBefore(LocalDate.now())) throw exception(STUDENT_CONTACT_FORM_INVALID);
        String nextStage = STAGE_SUPERVISION;
        if (relation.getExamDate() != null && relation.getExamNoticeSentAt() == null) nextStage = relation.getDeliveryStage();
        if (relationMapper.updateExamDate(relationId, userId, request.getExamDate(), request.getVersion(), nextStage, LocalDateTime.now()) != 1) {
            throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        }
    }

    private Long submit(Long relationId, Long taskId, String expectedType, Boolean successful, String unsuccessfulReason,
                          String remark, List<Long> attachments, List<String> checklistKeys, LocalDateTime nextAt,
                          String extensionReason, String extensionDescription, List<Long> extensionAttachments,
                          Map<String, Object> data, String idempotencyKey, Long userId) {
        ServiceRelationDO relation = requireOwnedForUpdate(relationId, userId);
        String requestFingerprint = contactFingerprint(expectedType, successful, unsuccessfulReason, remark,
                attachments, checklistKeys, nextAt, extensionReason, extensionDescription, extensionAttachments, data);
        StudentContactRecordDO replay = recordMapper.selectByIdempotencyKey(idempotencyKey);
        if (replay != null) {
            validateReplay(replay, relationId, taskId, userId, requestFingerprint);
            return replay.getId();
        }
        if (!"accepted".equals(relation.getAcceptanceStatus())) throw exception(STUDENT_SERVICE_NOT_ACCEPTED);
        BusinessTaskDO task = taskMapper.selectByIdForUpdate(taskId, TenantContextHolder.getRequiredTenantId());
        if (task == null || !"pending".equals(task.getStatus()) || !expectedType.equals(task.getTaskType())
                || !BIZ_TYPE.equals(task.getBizType()) || !relationId.equals(task.getBizId())
                || !userId.equals(task.getAssigneeId()) || nextAt == null || !nextAt.isAfter(LocalDateTime.now())) {
            throw exception(STUDENT_CONTACT_TASK_INVALID);
        }
        StudentContactConfigVersionDO config = configFromTask(task);
        validateAttachments(attachments, relationId, userId);
        String reasonLabel = Boolean.TRUE.equals(successful) ? null : dictLabel(DICT_UNSUCCESSFUL_REASON, unsuccessfulReason);
        if (Boolean.TRUE.equals(successful) && TYPE_FIRST_CONTACT.equals(expectedType)) {
            validateChecklist(config, checklistKeys, attachments);
        }
        StudentContactRecordDO record = new StudentContactRecordDO();
        record.setServiceRelationId(relationId); record.setTaskId(taskId); record.setContactType(expectedType);
        record.setSuccessful(successful); record.setUnsuccessfulReasonValue(Boolean.TRUE.equals(successful) ? null : unsuccessfulReason);
        record.setUnsuccessfulReasonLabelSnapshot(reasonLabel); record.setRemark(remark.trim());
        record.setAttachmentFileIdsJson(JsonUtils.toJsonString(attachments == null ? List.of() : attachments));
        record.setChecklistResultJson(JsonUtils.toJsonString(checklistKeys == null ? List.of() : checklistKeys));
        record.setDeliveryDataJson(JsonUtils.toJsonString(data == null ? Map.of() : data));
        record.setNextContactAt(nextAt); record.setOperatorUserId(userId); record.setSubmittedAt(LocalDateTime.now());
        record.setIdempotencyKey(idempotencyKey); record.setRequestFingerprint(requestFingerprint);
        try { recordMapper.insert(record); } catch (DuplicateKeyException duplicate) {
            StudentContactRecordDO concurrent = recordMapper.selectByIdempotencyKey(idempotencyKey);
            if (concurrent == null) throw duplicate;
            validateReplay(concurrent, relationId, taskId, userId, requestFingerprint);
            return concurrent.getId();
        }
        taskCommandService.completeByKey(task.getIdempotencyKey(), record.getSubmittedAt());
        if (TYPE_FIRST_CONTACT.equals(expectedType)) {
            taskCommandService.completeByKey("student-assistance:" + taskId, record.getSubmittedAt());
        }
        String nextType = nextType(expectedType, successful);
        int limit = timeout(config, nextType);
        LocalDateTime allowedDueAt = limit == 0 ? nextAt : record.getSubmittedAt().plusMinutes(limit);
        boolean extensionRequired = limit > 0 && nextAt.isAfter(allowedDueAt);
        Long nextTaskId = createTask(relation, nextType, extensionRequired ? allowedDueAt : nextAt,
                "student-contact:record:" + record.getId(), config.getId());
        if (extensionRequired) createExtension(relation, nextTaskId, allowedDueAt, nextAt, extensionReason,
                extensionDescription, extensionAttachments, userId, idempotencyKey + ":extension");
        if (Boolean.TRUE.equals(successful) && TYPE_FIRST_CONTACT.equals(expectedType)) {
            if (relationMapper.advanceDeliveryStage(relationId, userId, STAGE_FIRST_CONTACT, STAGE_STUDY_PLAN,
                    null, relation.getVersion()) != 1) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        } else if (Boolean.TRUE.equals(successful) && TYPE_STUDY_PLAN.equals(expectedType)) {
            if (relationMapper.advanceDeliveryStage(relationId, userId, STAGE_STUDY_PLAN, STAGE_SUPERVISION,
                    null, relation.getVersion()) != 1) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        }
        return record.getId();
    }

    @Override
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "assign")
    public List<StudyPlannerSimpleRespVO> getCollaboratorCandidates(Long relationId, String type, Long userId) {
        ServiceRelationDO relation = relationMapper.selectById(relationId);
        if (relation == null || !Set.of("active", "paused", "completed").contains(relation.getStatus())) {
            throw exception(STUDENT_SERVICE_NOT_EXISTS);
        }
        boolean operatorAssignment = COLLABORATOR_OPERATOR.equals(type);
        boolean owner = Objects.equals(relation.getOwnerUserId(), userId);
        boolean director = Objects.equals(relation.getContentDirectorUserId(), userId);
        boolean correction = permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT);
        boolean assign = permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_ASSIGN);
        boolean directorCanAssignOperator = operatorAssignment && director
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DIRECTOR_OPERATOR_ASSIGN);
        if (((!owner || !assign) && !directorCanAssignOperator) && !correction) throw exception(STUDENT_PERMISSION_DENIED);
        if (!"accepted".equals(relation.getAcceptanceStatus())) throw exception(STUDENT_SERVICE_NOT_ACCEPTED);
        String scene = scene(type);
        if (scene == null) throw exception(STUDENT_COLLABORATOR_INVALID);
        Set<Long> ids = new LinkedHashSet<>();
        Long sourceUserId = operatorAssignment ? relation.getContentDirectorUserId() : relation.getOwnerUserId();
        if (sourceUserId == null) throw exception(STUDENT_COLLABORATOR_INVALID);
        userRelationMapper.selectListBySourceUserIds(scene, List.of(sourceUserId)).stream()
                .filter(row -> CommonStatusEnum.ENABLE.getStatus().equals(row.getStatus()))
                .map(LeadAssignmentRelationDO::getTargetUserId).forEach(ids::add);
        return adminUserApi.getUserList(ids).stream().filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .sorted(Comparator.comparing(AdminUserRespDTO::getNickname).thenComparing(AdminUserRespDTO::getId))
                .map(user -> new StudyPlannerSimpleRespVO(user.getId(), user.getNickname())).toList();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "assign")
    public void assignCollaborator(Long relationId, StudentCollaboratorAssignReqVO request, Long userId) {
        ServiceRelationDO relation = relationMapper.selectByIdForUpdate(relationId, TenantContextHolder.getRequiredTenantId());
        if (relation == null || !"active".equals(relation.getStatus())) throw exception(STUDENT_SERVICE_NOT_EXISTS);
        boolean operatorAssignment = COLLABORATOR_OPERATOR.equals(request.getCollaboratorType());
        boolean owner = Objects.equals(relation.getOwnerUserId(), userId);
        boolean director = Objects.equals(relation.getContentDirectorUserId(), userId);
        boolean operational = "active".equals(relation.getStatus());
        boolean correction = permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT);
        boolean assign = permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_ASSIGN);
        boolean ownerCanAssign = operational && owner && assign;
        boolean directorCanAssignOperator = operatorAssignment && director
                && permissionApi.hasAnyPermissions(userId, PERMISSION_DIRECTOR_OPERATOR_ASSIGN);
        if ((!ownerCanAssign && !directorCanAssignOperator && !correction)
                || !"accepted".equals(relation.getAcceptanceStatus())) throw exception(STUDENT_PERMISSION_DENIED);
        StudentCollaboratorAssignmentLogDO replay = assignmentLogMapper.selectByIdempotencyKey(request.getIdempotencyKey());
        if (replay != null) {
            if (!Objects.equals(replay.getServiceRelationId(), relationId)
                    || !Objects.equals(replay.getCollaboratorType(), request.getCollaboratorType())
                    || !Objects.equals(replay.getAssignedUserId(), request.getUserId())
                    || !Objects.equals(replay.getOperatorUserId(), userId)) throw exception(STUDENT_COLLABORATOR_INVALID);
            return;
        }
        if (!Objects.equals(relation.getVersion(), request.getVersion())) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        boolean candidate = getCollaboratorCandidates(relationId, request.getCollaboratorType(), userId).stream()
                .anyMatch(row -> row.getId().equals(request.getUserId()));
        if (!candidate) throw exception(STUDENT_COLLABORATOR_INVALID);
        if (operatorAssignment) {
            assignUnifiedOperator(relation, request, userId);
            return;
        }
        Long previous = COLLABORATOR_DIRECTOR.equals(request.getCollaboratorType())
                ? relation.getContentDirectorUserId() : COLLABORATOR_CAREER.equals(request.getCollaboratorType())
                ? relation.getCareerPlannerUserId() : relation.getOperatorUserId();
        if (previous != null && !ownerCanAssign && !directorCanAssignOperator && !correction) {
            throw exception(STUDENT_COLLABORATOR_ALREADY_ASSIGNED);
        }
        if (previous != null && (request.getCorrectionReason() == null || request.getCorrectionReason().isBlank())) {
            throw exception(STUDENT_COLLABORATOR_CORRECTION_REASON_REQUIRED);
        }
        if (COLLABORATOR_DIRECTOR.equals(request.getCollaboratorType())) relation.setContentDirectorUserId(request.getUserId());
        else if (COLLABORATOR_CAREER.equals(request.getCollaboratorType())) relation.setCareerPlannerUserId(request.getUserId());
        else relation.setOperatorUserId(request.getUserId());
        relation.setVersion(relation.getVersion() + 1); relationMapper.updateById(relation);
        writeAssignmentLog(relationId, request.getCollaboratorType(), previous, request.getUserId(), userId,
                request.getCorrectionReason(), request.getIdempotencyKey());
        if (COLLABORATOR_DIRECTOR.equals(request.getCollaboratorType())) {
            SalesOrderDO order = orderMapper.selectById(relation.getOrderId());
            LeadDO lead = order == null || order.getLeadId() == null ? null : leadMapper.selectById(order.getLeadId());
            PersonDO student = personMapper.selectById(relation.getPersonId());
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("registrationCaseId", relation.getRegistrationCaseId());
            context.put("orderNo", order == null ? "" : order.getOrderNo());
            context.put("leadNo", lead == null || lead.getLeadNo() == null ? "" : lead.getLeadNo());
            context.put("studentName", student == null || student.getName() == null ? "" : student.getName());
            context.put("contentDirectorUserId", request.getUserId());
            studentContactNotifyPublisher.publish(
                    cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.NOTIFY_SCENE_DIRECTOR_ASSIGNED,
                    relationId, "student-director-assigned:" + relationId + ":" + relation.getVersion()
                            + ":" + request.getUserId(), null, LocalDateTime.now(), context);
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-contact-extension", bizId = "#extensionId", action = "withdraw")
    public void withdrawExtension(Long extensionId, Integer version, String reason, String idempotencyKey, Long userId) {
        StudentContactExtensionDO extension = extensionMapper.selectByIdForUpdate(
                extensionId, TenantContextHolder.getRequiredTenantId());
        if (extension != null && "withdrawn".equals(extension.getStatus())
                && idempotencyKey.equals(extension.getWithdrawalIdempotencyKey())) return;
        if (extension == null || !"pending".equals(extension.getStatus())
                || !userId.equals(extension.getApplicantUserId()) || !version.equals(extension.getVersion())) {
            throw exception(STUDENT_CONTACT_EXTENSION_NOT_EXISTS);
        }
        if (extensionMapper.transitionPending(extensionId, version, "withdrawn", reason.trim(), idempotencyKey,
                LocalDateTime.now()) != 1) throw exception(STUDENT_CONTACT_EXTENSION_NOT_EXISTS);
        String processInstanceId = extension.getProcessInstanceId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { processInstanceApi.cancelProcessInstanceByStartUser(userId, processInstanceId, reason.trim()); }
                catch (RuntimeException ex) {
                    log.error("[withdrawExtension][extensionId({}) BPM cancellation failed after commit]", extensionId, ex);
                }
            }
        });
    }

    @Override
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "student-contact.extension-process-result", targetType = "student-contact")
    @Transactional(rollbackFor = Exception.class)
    public void handleExtensionResult(String processInstanceId, Integer processStatus, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) return;
        StudentContactExtensionDO extension = extensionMapper.selectByProcessIdForUpdate(
                processInstanceId, TenantContextHolder.getRequiredTenantId());
        if (extension == null || !"pending".equals(extension.getStatus())) return;
        String status;
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)
                && taskMapper.updatePendingDueAt(extension.getTaskId(), extension.getRequestedDueAt()) == 1) status = "approved";
        else status = BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus) ? "rejected" : "cancelled";
        if (extensionMapper.transitionPending(extension.getId(), extension.getVersion(), status, reason, null,
                LocalDateTime.now()) != 1) throw exception(STUDENT_CONTACT_EXTENSION_NOT_EXISTS);
    }

    @Override
    public PageResult<StudentContactExtensionRespVO> getExtensions(PageParam page, String statusScope, Long userId) {
        if (statusScope != null && !Set.of("pending", "history", "all").contains(statusScope)) {
            throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
        PageResult<StudentContactExtensionDO> rows = extensionMapper.selectVisiblePage(page, userId, statusScope);
        return new PageResult<>(rows.getList().stream().map(row -> {
            StudentContactExtensionRespVO result = new StudentContactExtensionRespVO();
            result.setId(row.getId()); result.setServiceRelationId(row.getServiceRelationId()); result.setTaskId(row.getTaskId());
            result.setStatus(row.getStatus()); result.setOriginalDueAt(row.getOriginalDueAt());
            result.setRequestedDueAt(row.getRequestedDueAt()); result.setReasonValue(row.getReasonValue());
            result.setReasonLabel(row.getReasonLabelSnapshot()); result.setDescription(row.getDescription());
            result.setAttachmentFileIds(parseLongs(row.getAttachmentFileIdsJson()));
            result.setApplicantUserId(row.getApplicantUserId()); result.setReviewerUserId(row.getReviewerUserId());
            result.setProcessInstanceId(row.getProcessInstanceId()); result.setDecisionReason(row.getDecisionReason());
            result.setSubmittedAt(row.getSubmittedAt()); result.setResolvedAt(row.getResolvedAt());
            result.setVersion(row.getVersion());
            return result;
        }).toList(), rows.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-assistance", bizId = "#taskId", action = "complete")
    public void completeAssistance(Long taskId, String remark, Long userId) {
        BusinessTaskDO task = taskMapper.selectByIdForUpdate(taskId, TenantContextHolder.getRequiredTenantId());
        if (task == null || !TYPE_ASSISTANCE.equals(task.getTaskType()) || !userId.equals(task.getAssigneeId())) {
            throw exception(STUDENT_CONTACT_TASK_INVALID);
        }
        if ("completed".equals(task.getStatus())) return;
        if (!"pending".equals(task.getStatus())) throw exception(STUDENT_CONTACT_TASK_INVALID);
        Map<?, ?> existing = JsonUtils.parseObject(task.getPayload(), Map.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (existing != null) existing.forEach((key, value) -> payload.put(String.valueOf(key), value));
        payload.put("assistanceRemark", remark.trim());
        if (taskMapper.completeAssistance(taskId, userId, LocalDateTime.now(), JsonUtils.toJsonString(payload)) != 1) {
            throw exception(STUDENT_CONTACT_TASK_INVALID);
        }
    }

    private void createExtension(ServiceRelationDO relation, Long taskId, LocalDateTime originalDueAt,
                                 LocalDateTime requestedDueAt, String reasonValue, String description,
                                 List<Long> attachments, Long applicant, String idempotencyKey) {
        if (reasonValue == null || description == null || description.isBlank()) throw exception(STUDENT_CONTACT_FORM_INVALID);
        validateAttachments(attachments, relation.getId(), applicant);
        String reasonLabel = dictLabel(DICT_EXTENSION_REASON, reasonValue);
        Long reviewer = requireSupervisor(applicant);
        StudentContactExtensionDO extension = new StudentContactExtensionDO();
        extension.setServiceRelationId(relation.getId()); extension.setTaskId(taskId); extension.setStatus("pending");
        extension.setOriginalDueAt(originalDueAt); extension.setRequestedDueAt(requestedDueAt);
        extension.setReasonValue(reasonValue); extension.setReasonLabelSnapshot(reasonLabel); extension.setDescription(description.trim());
        extension.setAttachmentFileIdsJson(JsonUtils.toJsonString(attachments == null ? List.of() : attachments));
        extension.setApplicantUserId(applicant); extension.setReviewerUserId(reviewer); extension.setSubmittedAt(LocalDateTime.now());
        extension.setIdempotencyKey(idempotencyKey); extension.setVersion(0); extensionMapper.insert(extension);
        BpmProcessInstanceCreateReqDTO process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey(PROCESS_EXTENSION); process.setBusinessKey("student-contact-extension:" + extension.getId());
        process.setVariables(new java.util.HashMap<>(Map.of(
                "extensionId", extension.getId(),
                "serviceRelationId", relation.getId(),
                "originalDueAt", originalDueAt.toString(),
                "requestedDueAt", requestedDueAt.toString(),
                "reasonValue", extension.getReasonValue(),
                "reasonLabel", extension.getReasonLabelSnapshot(),
                "description", extension.getDescription(),
                "attachmentFileIds", extension.getAttachmentFileIdsJson(),
                "applicantUserId", extension.getApplicantUserId(),
                "submittedAt", extension.getSubmittedAt().toString())));
        process.setStartUserSelectAssignees(Map.of(TASK_EXTENSION_REVIEW, List.of(reviewer)));
        try { extension.setProcessInstanceId(processInstanceApi.createProcessInstance(applicant, process)); }
        catch (RuntimeException ex) {
            log.error("[createExtension][extensionId({}) process start failed]", extension.getId(), ex);
            throw exception(STUDENT_CONTACT_PROCESS_UNAVAILABLE);
        }
        String processInstanceId = extension.getProcessInstanceId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                try { processInstanceApi.cancelProcessInstanceByStartUser(applicant, processInstanceId, "业务事务已回滚"); }
                catch (RuntimeException ex) {
                    log.error("[createExtension][extensionId({}) rollback compensation failed]", extension.getId(), ex);
                }
            }
        });
        if (extensionMapper.updateById(extension) != 1) throw exception(STUDENT_CONTACT_PROCESS_UNAVAILABLE);
    }

    private Long createTask(ServiceRelationDO relation, String type, LocalDateTime dueAt, String key, Long configId) {
        SalesOrderDO order = orderMapper.selectById(relation.getOrderId());
        String number = order == null ? null : order.getOrderNo();
        String label = TYPE_FIRST_CONTACT.equals(type) ? "首次联系" : TYPE_STUDY_PLAN.equals(type) ? "制定学习计划" : "联系学员";
        return taskCommandService.create(new BusinessTaskCreateCommand(type, BIZ_TYPE, relation.getId(), relation.getOwnerUserId(),
                label + (number == null ? "" : " · " + number), "请在截止时间前完成本次学员联系", action(type),
                dueAt, dueAt, JsonUtils.toJsonString(Map.of("serviceRelationId", relation.getId(), "configVersionId", configId)), key));
    }

    private BusinessTaskDO currentTask(Long relationId) {
        for (String type : List.of(TYPE_FIRST_CONTACT, TYPE_STUDY_PLAN, TYPE_CONTACT)) {
            BusinessTaskDO value = taskMapper.selectPendingByRelationAndType(relationId, type); if (value != null) return value;
        }
        return null;
    }


    private StudentContactConfigVersionDO configFromTask(BusinessTaskDO task) {
        Map<?, ?> payload = JsonUtils.parseObject(task.getPayload(), Map.class);
        Object id = payload == null ? null : payload.get("configVersionId");
        StudentContactConfigVersionDO config = id == null ? null : studentContactConfigVersionMapper.selectById(Long.valueOf(String.valueOf(id)));
        if (config == null) throw exception(STUDENT_CONTACT_CONFIG_INVALID); return config;
    }

    private void validateChecklist(StudentContactConfigVersionDO config, List<String> completed, List<Long> attachments) {
        List<StudentContactContextRespVO.ChecklistItemVO> items = checklist(config);
        Set<String> required = items.stream().map(StudentContactContextRespVO.ChecklistItemVO::getKey)
                .collect(java.util.stream.Collectors.toSet());
        if (completed == null || !new HashSet<>(completed).containsAll(required)) throw exception(STUDENT_CONTACT_FORM_INVALID);
        if (items.stream().anyMatch(item -> Boolean.TRUE.equals(item.getAttachmentRequired()))
                && (attachments == null || attachments.isEmpty())) throw exception(STUDENT_CONTACT_FORM_INVALID);
    }

    private List<StudentContactContextRespVO.ChecklistItemVO> checklist(StudentContactConfigVersionDO config) {
        return JsonUtils.parseArray(config.getChecklistJson(), StudentContactConfigRespVO.ChecklistItemVO.class).stream()
                .filter(StudentContactConfigRespVO.ChecklistItemVO::getEnabled).sorted(Comparator.comparing(StudentContactConfigRespVO.ChecklistItemVO::getSort))
                .map(item -> { StudentContactContextRespVO.ChecklistItemVO row = new StudentContactContextRespVO.ChecklistItemVO();
                    row.setKey(item.getKey()); row.setTitle(item.getTitle()); row.setType(item.getType());
                    row.setAttachmentRequired(item.getAttachmentRequired()); return row; }).toList();
    }

    private ServiceRelationDO requireOwned(Long id, Long userId) {
        ServiceRelationDO relation = relationMapper.selectById(id);
        if (relation == null || !"active".equals(relation.getStatus())) throw exception(STUDENT_SERVICE_NOT_EXISTS);
        if (!Objects.equals(relation.getOwnerUserId(), userId)) throw exception(STUDENT_PERMISSION_DENIED); return relation;
    }
    private ServiceRelationDO requireOwnedForUpdate(Long id, Long userId) {
        ServiceRelationDO relation = relationMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (relation == null || !"active".equals(relation.getStatus())) throw exception(STUDENT_SERVICE_NOT_EXISTS);
        if (!Objects.equals(relation.getOwnerUserId(), userId)) throw exception(STUDENT_PERMISSION_DENIED); return relation;
    }
    private ServiceRelationDO requireReadable(Long id, Long userId) {
        ServiceRelationDO relation = relationMapper.selectById(id);
        if (relation == null || !Set.of("active", "paused", "completed").contains(relation.getStatus())) {
            throw exception(STUDENT_SERVICE_NOT_EXISTS);
        }
        if (!Objects.equals(relation.getOwnerUserId(), userId)
                && !Objects.equals(relation.getContentDirectorUserId(), userId)
                && !Objects.equals(relation.getCareerPlannerUserId(), userId)
                && !Objects.equals(relation.getOperatorUserId(), userId)) {
            throw exception(STUDENT_PERMISSION_DENIED);
        }
        return relation;
    }
    private Long requireSupervisor(Long plannerId) {
        AdminUserRespDTO planner = adminUserApi.getUser(plannerId);
        DeptRespDTO dept = planner == null || planner.getDeptId() == null ? null : deptApi.getDept(planner.getDeptId());
        Long supervisorId = dept == null ? null : dept.getLeaderUserId();
        AdminUserRespDTO supervisor = supervisorId == null ? null : adminUserApi.getUser(supervisorId);
        if (supervisor == null || Objects.equals(plannerId, supervisorId)
                || !CommonStatusEnum.ENABLE.getStatus().equals(supervisor.getStatus())
                || !permissionApi.hasAnyPermissions(supervisorId, PERMISSION_EXTENSION_REVIEW)) {
            throw exception(STUDENT_CONTACT_SUPERVISOR_INVALID);
        }
        return supervisorId;
    }
    private String dictLabel(String type, String value) {
        if (value == null || value.isBlank()) throw exception(STUDENT_CONTACT_REASON_INVALID);
        try { dictDataApi.validateDictDataList(type, List.of(value)); }
        catch (RuntimeException ex) { throw exception(STUDENT_CONTACT_REASON_INVALID); }
        return dictDataApi.getDictDataList(type).stream().filter(row -> value.equals(row.getValue()))
                .map(DictDataRespDTO::getLabel).findFirst().orElseThrow(() -> exception(STUDENT_CONTACT_REASON_INVALID));
    }
    private int timeout(StudentContactConfigVersionDO config, String type) {
        return TYPE_FIRST_CONTACT.equals(type) ? config.getFirstContactTimeoutMinutes()
                : TYPE_STUDY_PLAN.equals(type) ? config.getStudyPlanTimeoutMinutes() : 0;
    }
    private String nextType(String current, Boolean success) {
        if (TYPE_FIRST_CONTACT.equals(current)) return Boolean.TRUE.equals(success) ? TYPE_STUDY_PLAN : TYPE_FIRST_CONTACT;
        if (TYPE_STUDY_PLAN.equals(current)) return Boolean.TRUE.equals(success) ? TYPE_CONTACT : TYPE_STUDY_PLAN;
        return TYPE_CONTACT;
    }
    private String action(String type) { return TYPE_FIRST_CONTACT.equals(type) ? ACTION_FIRST_CONTACT : TYPE_STUDY_PLAN.equals(type) ? ACTION_STUDY_PLAN : ACTION_CONTACT; }
    private String scene(String type) { return COLLABORATOR_DIRECTOR.equals(type) ? RELATION_PLANNER_DIRECTOR
            : COLLABORATOR_CAREER.equals(type) ? RELATION_PLANNER_CAREER
            : COLLABORATOR_OPERATOR.equals(type) ? RELATION_DIRECTOR_OPERATOR : null; }
    private String name(AdminUserRespDTO user) { return user == null ? null : user.getNickname(); }
    @Override
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "contact")
    public StudentContactAttachmentRespVO uploadAttachment(Long relationId, Long userId, MultipartFile file) throws IOException {
        requireOwned(relationId, userId);
        if (file == null || file.isEmpty() || file.getSize() > 20L * 1024 * 1024) {
            throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
        FileInfoRespDTO saved = fileApi.createFileInfo(file.getBytes(), file.getOriginalFilename(),
                attachmentDirectory(relationId, userId), file.getContentType());
        return new StudentContactAttachmentRespVO(saved.getId(), file.getOriginalFilename(), saved.getUrl(),
                file.getContentType(), file.getSize());
    }
    private void validateAttachments(List<Long> fileIds, Long relationId, Long userId) {
        if (fileIds == null) return;
        if (fileIds.size() > 9 || fileIds.stream().anyMatch(Objects::isNull)) throw exception(STUDENT_CONTACT_FORM_INVALID);
        for (Long fileId : new LinkedHashSet<>(fileIds)) {
            FileInfoRespDTO file;
            try { file = fileApi.getFileInfo(fileId); }
            catch (ServiceException ex) { throw exception(STUDENT_CONTACT_FORM_INVALID); }
            String prefix = attachmentDirectory(relationId, userId) + "/";
            if (file == null || !String.valueOf(userId).equals(file.getCreator()) || file.getPath() == null
                    || !file.getPath().startsWith(prefix)) throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
    }
    private void validateReplay(StudentContactRecordDO replay, Long relationId, Long taskId, Long userId,
                                String requestFingerprint) {
        if (!Objects.equals(replay.getServiceRelationId(), relationId) || !Objects.equals(replay.getTaskId(), taskId)
                || !Objects.equals(replay.getOperatorUserId(), userId)
                || !Objects.equals(replay.getRequestFingerprint(), requestFingerprint)) {
            throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
    }
    private String contactFingerprint(String type, Boolean successful, String unsuccessfulReason, String remark,
                                      List<Long> attachments, List<String> checklistKeys, LocalDateTime nextAt,
                                      String extensionReason, String extensionDescription,
                                      List<Long> extensionAttachments, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type); payload.put("successful", successful);
        payload.put("unsuccessfulReason", unsuccessfulReason); payload.put("remark", remark);
        payload.put("attachments", attachments == null ? List.of() : attachments);
        payload.put("checklistKeys", checklistKeys == null ? List.of() : checklistKeys);
        payload.put("nextAt", nextAt == null ? null : nextAt.toString());
        payload.put("extensionReason", extensionReason); payload.put("extensionDescription", extensionDescription);
        payload.put("extensionAttachments", extensionAttachments == null ? List.of() : extensionAttachments);
        payload.put("data", data == null ? Map.of() : data);
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(payload));
    }
    private String deliveryFingerprint(Long relationId, StudentDeliveryStageSubmitReqVO request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("relationId", relationId); payload.put("stage", request.getStage());
        payload.put("successful", request.getSuccessful()); payload.put("remark", request.getRemark().trim());
        payload.put("data", request.getData() == null ? Map.of() : request.getData());
        payload.put("attachmentFileIds", normalizeAttachmentIds(request.getAttachmentFileIds()));
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(payload));
    }
    private String attachmentDirectory(Long relationId, Long userId) {
        return "zsjos/student-contact/" + relationId + "/" + userId;
    }
    private List<Long> parseLongs(String json) { return json == null ? List.of() : JsonUtils.parseArray(json, Long.class); }
    private List<String> parseStrings(String json) { return json == null ? List.of() : JsonUtils.parseArray(json, String.class); }

    private List<String> deliveryStageCodes() {
        return List.of(STAGE_FIRST_CONTACT, STAGE_STUDY_PLAN, STAGE_SUPERVISION,
                STAGE_EXAM_PREPARATION, STAGE_POST_EXAM, STAGE_RESULT,
                STAGE_CERTIFICATE, STAGE_CONTINUOUS_FOLLOW_UP, STAGE_COMPLETED);
    }

    private String stageLabel(String code) {
        return switch (code) {
            case STAGE_FIRST_CONTACT -> "首联";
            case STAGE_STUDY_PLAN -> "制定学习计划";
            case STAGE_SUPERVISION -> "常规督学";
            case STAGE_EXAM_PREPARATION -> "考前通知与冲刺";
            case STAGE_POST_EXAM -> "考后回访";
            case STAGE_RESULT -> "成绩通知";
            case STAGE_CERTIFICATE -> "证书通知与邮寄";
            case STAGE_CONTINUOUS_FOLLOW_UP -> "持续回访";
            case STAGE_COMPLETED -> "服务完成";
            default -> code;
        };
    }

    private List<StudentContactContextRespVO.DeliveryStageVO> deliveryStages(String current, boolean canSubmit) {
        List<String> stages = deliveryStageCodes();
        int currentIndex = stages.indexOf(current);
        List<StudentContactContextRespVO.DeliveryStageVO> result = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            StudentContactContextRespVO.DeliveryStageVO row = new StudentContactContextRespVO.DeliveryStageVO();
            row.setCode(stages.get(i)); row.setLabel(stageLabel(stages.get(i))); row.setCurrent(i == currentIndex);
            row.setAvailable(canSubmit && i == currentIndex
                    && !Set.of(STAGE_FIRST_CONTACT, STAGE_STUDY_PLAN, STAGE_COMPLETED).contains(stages.get(i)));
            row.setStatus(i < currentIndex ? "done" : i == currentIndex ? "current" : "pending");
            result.add(row);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "director-precheck")
    public Integer saveDirectorPrecheckDraft(Long relationId, DirectorStageSaveReqVO request, Long userId) {
        return saveDirectorStage(relationId, "precheck", false, request, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "director-precheck")
    public void submitDirectorPrecheck(Long relationId, DirectorStageSaveReqVO request, Long userId) {
        saveDirectorStage(relationId, "precheck", true, request, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "director-interview")
    public Integer saveDirectorInterviewDraft(Long relationId, DirectorStageSaveReqVO request, Long userId) {
        return saveDirectorStage(relationId, "interview", false, request, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "director-interview")
    public void submitDirectorInterview(Long relationId, DirectorStageSaveReqVO request, Long userId) {
        saveDirectorStage(relationId, "interview", true, request, userId);
    }

    private Integer saveDirectorStage(Long relationId, String stage, boolean submit,
                                      DirectorStageSaveReqVO request, Long userId) {
        String permission = "precheck".equals(stage) ? PERMISSION_DIRECTOR_PRECHECK : PERMISSION_DIRECTOR_INTERVIEW;
        if (!permissionApi.hasAnyPermissions(userId, permission)) throw exception(STUDENT_PERMISSION_DENIED);
        ServiceRelationDO relation = relationMapper.selectByIdForUpdate(relationId,
                TenantContextHolder.getRequiredTenantId());
        if (relation == null || !"active".equals(relation.getStatus())
                || !"accepted".equals(relation.getAcceptanceStatus())
                || !Objects.equals(relation.getContentDirectorUserId(), userId)) {
            throw exception(STUDENT_PERMISSION_DENIED);
        }
        String current = StrUtil.blankToDefault(relation.getDirectorStage(), "precheck");
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "stage", stage, "submit", submit,
                "interviewAt", request.getInterviewAt() == null ? "" : request.getInterviewAt().toString(),
                "data", request.getData())));
        String previousJson = "precheck".equals(stage) ? relation.getDirectorPrecheckDraftJson()
                : relation.getDirectorInterviewDraftJson();
        Integer draftVersion = "precheck".equals(stage) ? relation.getDirectorPrecheckDraftVersion()
                : relation.getDirectorInterviewDraftVersion();
        if (draftVersion == null) draftVersion = 0;
        if (!Objects.equals(draftVersion, request.getVersion())
                && directorCommandReplay(previousJson, request.getIdempotencyKey(), fingerprint)) {
            return draftVersion;
        }
        if (!current.equals(stage) || !Objects.equals(draftVersion, request.getVersion())) {
            throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        }
        boolean precheck = "precheck".equals(stage);
        if (precheck && request.getData() != null && !request.getData().isEmpty()) {
            throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
        StudentContactContextRespVO.DirectorFormVO existing = directorForm(stage, previousJson, null, relation, draftVersion);
        DirectorFormTemplateVO.Snapshot templateSnapshot = null;
        if (!precheck) {
            templateSnapshot = "empty".equals(existing.getState())
                    ? directorFormTemplateService.validateAndSnapshot(DirectorFormTemplateService.SCENE_INTERVIEW,
                            null, request.getData(), submit)
                    : directorFormTemplateService.validateAndSnapshotVersion(DirectorFormTemplateService.SCENE_INTERVIEW,
                            existing.getTemplateVersionId(), request.getData(), submit, existing.getDictSnapshots());
        }
        if (submit) {
            LocalDateTime businessNow = LocalDateTime.now(BUSINESS_ZONE);
            if (precheck
                    && (request.getInterviewAt() == null || !request.getInterviewAt().isAfter(businessNow))) {
                log.warn("[saveDirectorStage][invalid interviewAt] relationId={}, stage={}, interviewAt={}, businessNow={}, zone={}",
                        relationId, stage, request.getInterviewAt(), businessNow, BUSINESS_ZONE);
                throw exception(STUDENT_DIRECTOR_INTERVIEW_AT_INVALID);
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configId", null);
        payload.put("configVersion", null);
        payload.put("templateId", templateSnapshot == null ? null : templateSnapshot.getTemplateId());
        payload.put("templateVersionId", templateSnapshot == null ? null : templateSnapshot.getTemplateVersionId());
        payload.put("templateVersionNo", templateSnapshot == null ? null : templateSnapshot.getTemplateVersionNo());
        payload.put("fields", templateSnapshot == null ? List.of() : templateSnapshot.getFields());
        payload.put("values", templateSnapshot == null ? Map.of() : templateSnapshot.getValues());
        payload.put("dictSnapshots", templateSnapshot == null ? Map.of() : templateSnapshot.getDictSnapshots());
        payload.put("savedAt", LocalDateTime.now());
        payload.put("savedByUserId", userId);
        if (submit) payload.put("submittedAt", LocalDateTime.now());
        payload.put("idempotencyKey", request.getIdempotencyKey());
        payload.put("requestFingerprint", fingerprint);
        String json = JsonUtils.toJsonString(payload);
        if ("precheck".equals(stage)) {
            relation.setDirectorPrecheckDraftJson(json);
            if (submit) {
                relation.setDirectorPrecheckSnapshotJson(json);
                relation.setDirectorInterviewAt(request.getInterviewAt());
                relation.setDirectorStage("interview");
            }
        } else {
            relation.setDirectorInterviewDraftJson(json);
            if (submit) {
                relation.setDirectorInterviewSnapshotJson(json);
                relation.setDirectorStage("positioning_ready");
            }
        }
        relation.setDirectorFormConfigId(templateSnapshot == null ? null : templateSnapshot.getTemplateId());
        relation.setDirectorFormConfigVersion(templateSnapshot == null ? null : templateSnapshot.getTemplateVersionNo());
        int nextDraftVersion = draftVersion + 1;
        if ("precheck".equals(stage)) relation.setDirectorPrecheckDraftVersion(nextDraftVersion);
        else relation.setDirectorInterviewDraftVersion(nextDraftVersion);
        if (submit) relation.setVersion(relation.getVersion() + 1);
        relationMapper.updateById(relation);
        return nextDraftVersion;
    }

    private boolean directorCommandReplay(String json, String idempotencyKey, String fingerprint) {
        if (StrUtil.isBlank(json)) return false;
        Map<?, ?> payload = JsonUtils.parseObject(json, Map.class);
        if (payload == null || !Objects.equals(idempotencyKey, payload.get("idempotencyKey"))) return false;
        if (!Objects.equals(fingerprint, payload.get("requestFingerprint"))) {
            throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
        return true;
    }

    private StudentContactContextRespVO.DirectorFormsVO directorForms(ServiceRelationDO relation,
                                                                        StudentContactConfigVersionDO config) {
        StudentContactContextRespVO.DirectorFormsVO forms = new StudentContactContextRespVO.DirectorFormsVO();
        forms.setPrecheck(directorForm("precheck", relation.getDirectorPrecheckDraftJson(),
                relation.getDirectorPrecheckSnapshotJson(), relation, relation.getDirectorPrecheckDraftVersion()));
        forms.setInterview(directorForm("interview", relation.getDirectorInterviewDraftJson(),
                relation.getDirectorInterviewSnapshotJson(), relation, relation.getDirectorInterviewDraftVersion()));
        return forms;
    }

    private StudentContactContextRespVO.DirectorFormVO directorForm(String stage, String draftJson,
            String snapshotJson, ServiceRelationDO relation, Integer draftVersion) {
        StudentContactContextRespVO.DirectorFormVO result = new StudentContactContextRespVO.DirectorFormVO();
        result.setVersion(draftVersion == null ? 0 : draftVersion);
        String sourceJson = StrUtil.isNotBlank(snapshotJson) ? snapshotJson : draftJson;
        result.setState(StrUtil.isNotBlank(snapshotJson) ? "submitted" : StrUtil.isNotBlank(draftJson) ? "draft" : "empty");
        result.setInterviewAt("precheck".equals(stage) ? relation.getDirectorInterviewAt() : null);
        if (StrUtil.isBlank(sourceJson)) {
            if ("precheck".equals(stage)) {
                result.setFields(List.of());
            } else {
                var version = directorFormTemplateService.requirePublished(
                        DirectorFormTemplateService.SCENE_INTERVIEW, null);
                result.setTemplateId(version.getTemplateId());
                result.setTemplateVersionId(version.getId());
                result.setTemplateVersionNo(version.getVersionNo());
                result.setFields(directorFormTemplateService.fields(version).stream()
                        .filter(DirectorFormTemplateVO.Field::getEnabled).map(this::toContextField).toList());
            }
            result.setValues(Map.of());
            return result;
        }
        Map<?, ?> payload = JsonUtils.parseObject(sourceJson, Map.class);
        if (payload == null) throw exception(STUDENT_CONTACT_FORM_INVALID);
        result.setConfigId(numberAsLong(payload.get("configId")));
        result.setConfigVersion(numberAsInteger(payload.get("configVersion")));
        result.setTemplateId(numberAsLong(payload.get("templateId")));
        result.setTemplateVersionId(numberAsLong(payload.get("templateVersionId")));
        result.setTemplateVersionNo(numberAsInteger(payload.get("templateVersionNo")));
        Object rawFields = payload.get("fields");
        if (rawFields instanceof Collection<?> values) {
            result.setFields(values.stream()
                    .map(value -> JsonUtils.parseObject(JsonUtils.toJsonString(value),
                            StudentContactContextRespVO.FormFieldVO.class))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(StudentContactContextRespVO.FormFieldVO::getSort,
                            Comparator.nullsLast(Integer::compareTo))).toList());
        } else result.setFields(List.of());
        Map<String, Object> formValues = new LinkedHashMap<>();
        if (payload.get("values") instanceof Map<?, ?> values) {
            values.forEach((key, value) -> formValues.put(String.valueOf(key), value));
        }
        result.setValues(formValues);
        Map<String, Object> dictSnapshots = new LinkedHashMap<>();
        if (payload.get("dictSnapshots") instanceof Map<?, ?> snapshots) {
            snapshots.forEach((key, value) -> dictSnapshots.put(String.valueOf(key), value));
        }
        result.setDictSnapshots(dictSnapshots);
        result.setSavedAt(dateTime(payload.get("savedAt")));
        result.setSavedByUserId(numberAsLong(payload.get("savedByUserId")));
        result.setSubmittedAt(dateTime(payload.get("submittedAt")));
        return result;
    }

    private StudentContactContextRespVO.FormFieldVO toContextField(DirectorFormTemplateVO.Field source) {
        return JsonUtils.parseObject(JsonUtils.toJsonString(source), StudentContactContextRespVO.FormFieldVO.class);
    }

    private Long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer numberAsInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Map<String, Object> snapshotDictValues(List<StudentContactContextRespVO.FormFieldVO> fields,
                                                   Map<String, Object> values) {
        Map<String, Object> snapshots = new LinkedHashMap<>();
        if (values == null) return snapshots;
        for (StudentContactContextRespVO.FormFieldVO field : fields) {
            if (StrUtil.isBlank(field.getDictType())) continue;
            Object raw = values.get(field.getKey());
            List<String> selected = raw instanceof Collection<?> collection
                    ? collection.stream().filter(Objects::nonNull).map(String::valueOf).toList()
                    : raw == null ? List.of() : List.of(String.valueOf(raw));
            if (selected.isEmpty()) continue;
            List<DictDataRespDTO> options;
            try {
                dictDataApi.validateDictDataList(field.getDictType(), selected);
                options = dictDataApi.getDictDataList(field.getDictType());
            } catch (RuntimeException ex) {
                throw exception(STUDENT_CONTACT_FORM_DICT_INVALID);
            }
            Map<String, String> labels = options.stream().collect(java.util.stream.Collectors.toMap(
                    DictDataRespDTO::getValue, DictDataRespDTO::getLabel, (left, right) -> left));
            if (selected.stream().anyMatch(value -> !labels.containsKey(value))) {
                throw exception(STUDENT_CONTACT_FORM_DICT_INVALID);
            }
            List<Map<String, String>> entries = selected.stream().map(value -> {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("value", value);
                entry.put("labelSnapshot", labels.get(value));
                entry.put("dictType", field.getDictType());
                return entry;
            }).toList();
            snapshots.put(field.getKey(), entries.size() == 1 && !(raw instanceof Collection<?>)
                    ? entries.get(0) : entries);
        }
        return snapshots;
    }

    private LocalDateTime dateTime(Object value) {
        if (value == null) return null;
        try {
            return JsonUtils.parseObject(JsonUtils.toJsonString(value), LocalDateTime.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void assignUnifiedOperator(ServiceRelationDO selected, StudentCollaboratorAssignReqVO request, Long userId) {
        List<ServiceRelationDO> relations = relationMapper.selectActiveAcceptedByPersonForUpdate(selected.getPersonId(),
                TenantContextHolder.getRequiredTenantId());
        boolean correction = relations.stream().map(ServiceRelationDO::getOperatorUserId).filter(Objects::nonNull)
                .anyMatch(operatorId -> !Objects.equals(operatorId, request.getUserId()));
        if (correction && StrUtil.isBlank(request.getCorrectionReason())) {
            throw exception(STUDENT_COLLABORATOR_CORRECTION_REASON_REQUIRED);
        }
        for (ServiceRelationDO relation : relations) {
            Long previous = relation.getOperatorUserId();
            boolean selectedRelation = Objects.equals(relation.getId(), selected.getId());
            if (!Objects.equals(previous, request.getUserId())) {
                relation.setOperatorUserId(request.getUserId());
                relation.setVersion(relation.getVersion() + 1);
                relationMapper.updateById(relation);
            }
            if (selectedRelation || !Objects.equals(previous, request.getUserId())) {
                String key = selectedRelation ? request.getIdempotencyKey()
                        : request.getIdempotencyKey() + ":" + relation.getId();
                writeAssignmentLog(relation.getId(), COLLABORATOR_OPERATOR, previous, request.getUserId(), userId,
                        request.getCorrectionReason(), key);
            }
        }
        positioningCardMapper.updateCurrentOperatorByServiceRelations(
                relations.stream().map(ServiceRelationDO::getId).toList(), request.getUserId());
        for (var account : mediaAccountMapper.selectByStudent(selected.getPersonId())) {
            if (!Objects.equals(account.getOwnerOperatorUserId(), request.getUserId())) {
                if (mediaAccountMapper.updateOwnerOperator(account.getId(), request.getUserId(), account.getVersion()) == 0) {
                    throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
                }
            }
        }
        PersonDO student = personMapper.selectById(selected.getPersonId());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("operatorUserId", request.getUserId());
        context.put("studentName", student == null || student.getName() == null ? "" : student.getName());
        studentContactNotifyPublisher.publish(NOTIFY_OPERATOR_ASSIGNED, selected.getId(),
                "student-operator-assigned:" + selected.getPersonId() + ":" + request.getIdempotencyKey(),
                null, LocalDateTime.now(), context);
    }

    private void writeAssignmentLog(Long relationId, String type, Long previous, Long assigned, Long operator,
                                    String reason, String idempotencyKey) {
        StudentCollaboratorAssignmentLogDO log = new StudentCollaboratorAssignmentLogDO();
        log.setServiceRelationId(relationId);
        log.setCollaboratorType(type);
        log.setPreviousUserId(previous);
        log.setAssignedUserId(assigned);
        log.setOperatorUserId(operator);
        log.setReason(reason);
        log.setIdempotencyKey(idempotencyKey);
        assignmentLogMapper.insert(log);
    }

    private List<Long> normalizeAttachmentIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    private void validateDeliveryData(String stage, Map<String, Object> values) {
        if (values == null) throw exception(STUDENT_CONTACT_FORM_INVALID);
        List<String> required = switch (stage) {
            case STAGE_EXAM_PREPARATION -> List.of("examNoticeSent", "admissionTicketNoticeSent");
            case STAGE_POST_EXAM -> List.of("examFeedback");
            case STAGE_RESULT -> List.of("result");
            case STAGE_CERTIFICATE -> List.of("certificateNotice", "mailingInfo");
            default -> List.of();
        };
        if (required.stream().anyMatch(key -> values.get(key) == null
                || Boolean.FALSE.equals(values.get(key)) || String.valueOf(values.get(key)).isBlank())) {
            throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
    }

    private List<StudentContactContextRespVO.FormFieldVO> formFields(StudentContactConfigVersionDO config,
                                                                       String stage, BusinessTaskDO task) {
        if (config == null || StrUtil.isBlank(config.getFormsJson())) return defaultDirectorFields(stage);
        Map<?, ?> forms = JsonUtils.parseObject(config.getFormsJson(), Map.class);
        Object raw = forms == null ? null : forms.get(task == null ? stage : task.getTaskType());
        if (!(raw instanceof Collection<?> values)) return defaultDirectorFields(stage);
        return values.stream().map(value -> JsonUtils.parseObject(JsonUtils.toJsonString(value), StudentContactContextRespVO.FormFieldVO.class))
                .filter(Objects::nonNull).sorted(Comparator.comparing(StudentContactContextRespVO.FormFieldVO::getSort,
                Comparator.nullsLast(Integer::compareTo))).toList();
    }

    private List<StudentContactContextRespVO.FormFieldVO> defaultDirectorFields(String stage) {
        return List.of();
    }
}
