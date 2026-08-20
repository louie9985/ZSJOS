package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.io.IOException;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.*;

@Service
@Slf4j
public class StudentContactServiceImpl implements StudentContactService {
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private StudentContactRecordMapper recordMapper;
    @Resource private StudentContactExtensionMapper extensionMapper;
    @Resource private StudentCollaboratorAssignmentLogMapper assignmentLogMapper;
    @Resource private StudentContactConfigVersionMapper studentContactConfigVersionMapper;
    @Resource private StudentContactConfigService configService;
    @Resource private BusinessTaskMapper taskMapper;
    @Resource private BusinessTaskCommandService taskCommandService;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private LeadAssignmentRelationMapper userRelationMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PermissionApi permissionApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private FileApi fileApi;

    @Override public StudentContactContextRespVO getContext(Long relationId, Long userId) {
        ServiceRelationDO relation = requireReadable(relationId, userId);
        StudentContactContextRespVO result = new StudentContactContextRespVO();
        result.setServiceRelationId(relationId); result.setAcceptanceStatus(relation.getAcceptanceStatus());
        result.setAcceptedAt(relation.getAcceptedAt()); result.setVersion(relation.getVersion());
        StudentContactConfigVersionDO config = configService.requirePublished();
        result.setFirstContactChecklist(checklist(config));
        result.setQuickNotes(JsonUtils.parseArray(config.getQuickNotesJson(), String.class));
        result.setFirstContactTimeoutMinutes(config.getFirstContactTimeoutMinutes());
        result.setStudyPlanTimeoutMinutes(config.getStudyPlanTimeoutMinutes());
        boolean owner = Objects.equals(relation.getOwnerUserId(), userId);
        if (owner) result.setVisibleTabs(List.of("overview", "first-contact", "study-plan", "contacts"));
        else {
            String type = Objects.equals(relation.getContentDirectorUserId(), userId) ? COLLABORATOR_DIRECTOR : COLLABORATOR_CAREER;
            Map<?, ?> tabs = JsonUtils.parseObject(config.getCollaboratorTabsJson(), Map.class);
            List<String> visible = new ArrayList<>(List.of("overview"));
            Object configured = tabs == null ? null : tabs.get(type);
            if (configured instanceof Collection<?> values) values.stream().map(String::valueOf).forEach(visible::add);
            result.setVisibleTabs(visible);
        }
        BusinessTaskDO task = currentTask(relationId);
        if (task != null) {
            StudentContactContextRespVO.CurrentTaskVO row = new StudentContactContextRespVO.CurrentTaskVO();
            row.setId(task.getId()); row.setType(task.getTaskType()); row.setStatus(task.getStatus()); row.setDueAt(task.getDueAt());
            row.setOverdue(task.getDueAt() != null && task.getDueAt().isBefore(LocalDateTime.now())); result.setCurrentTask(row);
        }
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(List.of(
                relation.getContentDirectorUserId() == null ? -1L : relation.getContentDirectorUserId(),
                relation.getCareerPlannerUserId() == null ? -1L : relation.getCareerPlannerUserId()));
        result.setContentDirectorUserId(relation.getContentDirectorUserId());
        result.setContentDirectorUserName(name(users.get(relation.getContentDirectorUserId())));
        result.setCareerPlannerUserId(relation.getCareerPlannerUserId());
        result.setCareerPlannerUserName(name(users.get(relation.getCareerPlannerUserId())));
        return result;
    }

    @Override public List<StudentContactRecordRespVO> getRecords(Long relationId, Long userId) {
        requireReadable(relationId, userId);
        List<StudentContactRecordDO> rows = recordMapper.selectByRelationId(relationId);
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(rows.stream().map(StudentContactRecordDO::getOperatorUserId).toList());
        return rows.stream().map(row -> {
            StudentContactRecordRespVO result = new StudentContactRecordRespVO();
            result.setId(row.getId()); result.setContactType(row.getContactType()); result.setSuccessful(row.getSuccessful());
            result.setUnsuccessfulReasonValue(row.getUnsuccessfulReasonValue());
            result.setUnsuccessfulReasonLabel(row.getUnsuccessfulReasonLabelSnapshot()); result.setRemark(row.getRemark());
            result.setAttachmentFileIds(parseLongs(row.getAttachmentFileIdsJson()));
            result.setCompletedChecklistKeys(parseStrings(row.getChecklistResultJson())); result.setNextContactAt(row.getNextContactAt());
            result.setOperatorUserId(row.getOperatorUserId()); result.setOperatorUserName(name(users.get(row.getOperatorUserId())));
            result.setSubmittedAt(row.getSubmittedAt()); return result;
        }).toList();
    }

    @Override @Transactional(rollbackFor = Exception.class)
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

    @Override public Long submitFirstContact(Long relationId, StudentFirstContactSubmitReqVO request, Long userId) {
        return submit(relationId, request.getTaskId(), TYPE_FIRST_CONTACT, request.getSuccessful(),
                request.getUnsuccessfulReasonValue(), request.getRemark(), request.getAttachmentFileIds(),
                request.getCompletedChecklistKeys(), request.getNextContactAt(), request.getExtensionReasonValue(),
                request.getExtensionDescription(), request.getExtensionAttachmentFileIds(), request.getIdempotencyKey(), userId);
    }

    @Override public Long submitStudyPlan(Long relationId, StudentStudyPlanSubmitReqVO request, Long userId) {
        return submit(relationId, request.getTaskId(), TYPE_STUDY_PLAN, request.getSuccessful(),
                request.getUnsuccessfulReasonValue(), request.getRemark(), request.getAttachmentFileIds(), null,
                request.getNextContactAt(), request.getExtensionReasonValue(), request.getExtensionDescription(),
                request.getExtensionAttachmentFileIds(), request.getIdempotencyKey(), userId);
    }

    @Override public Long submitContact(Long relationId, StudentContactSubmitReqVO request, Long userId) {
        return submit(relationId, request.getTaskId(), TYPE_CONTACT, request.getSuccessful(),
                request.getUnsuccessfulReasonValue(), request.getRemark(), request.getAttachmentFileIds(), null,
                request.getNextContactAt(), null, null, null, request.getIdempotencyKey(), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    protected Long submit(Long relationId, Long taskId, String expectedType, Boolean successful, String unsuccessfulReason,
                          String remark, List<Long> attachments, List<String> checklistKeys, LocalDateTime nextAt,
                          String extensionReason, String extensionDescription, List<Long> extensionAttachments,
                          String idempotencyKey, Long userId) {
        StudentContactRecordDO replay = recordMapper.selectByIdempotencyKey(idempotencyKey);
        if (replay != null) {
            if (!Objects.equals(replay.getServiceRelationId(), relationId) || !Objects.equals(replay.getTaskId(), taskId)) {
                throw exception(STUDENT_CONTACT_FORM_INVALID);
            }
            return replay.getId();
        }
        ServiceRelationDO relation = requireOwnedForUpdate(relationId, userId);
        if (!"accepted".equals(relation.getAcceptanceStatus())) throw exception(STUDENT_SERVICE_NOT_ACCEPTED);
        BusinessTaskDO task = taskMapper.selectByIdForUpdate(taskId, TenantContextHolder.getRequiredTenantId());
        if (task == null || !"pending".equals(task.getStatus()) || !expectedType.equals(task.getTaskType())
                || !BIZ_TYPE.equals(task.getBizType()) || !relationId.equals(task.getBizId())
                || !userId.equals(task.getAssigneeId()) || nextAt == null || !nextAt.isAfter(LocalDateTime.now())) {
            throw exception(STUDENT_CONTACT_TASK_INVALID);
        }
        StudentContactConfigVersionDO config = configFromTask(task);
        validateAttachments(attachments);
        String reasonLabel = Boolean.TRUE.equals(successful) ? null : dictLabel(DICT_UNSUCCESSFUL_REASON, unsuccessfulReason);
        if (Boolean.TRUE.equals(successful) && TYPE_FIRST_CONTACT.equals(expectedType)) validateChecklist(config, checklistKeys);
        StudentContactRecordDO record = new StudentContactRecordDO();
        record.setServiceRelationId(relationId); record.setTaskId(taskId); record.setContactType(expectedType);
        record.setSuccessful(successful); record.setUnsuccessfulReasonValue(Boolean.TRUE.equals(successful) ? null : unsuccessfulReason);
        record.setUnsuccessfulReasonLabelSnapshot(reasonLabel); record.setRemark(remark.trim());
        record.setAttachmentFileIdsJson(JsonUtils.toJsonString(attachments == null ? List.of() : attachments));
        record.setChecklistResultJson(JsonUtils.toJsonString(checklistKeys == null ? List.of() : checklistKeys));
        record.setNextContactAt(nextAt); record.setOperatorUserId(userId); record.setSubmittedAt(LocalDateTime.now());
        record.setIdempotencyKey(idempotencyKey);
        try { recordMapper.insert(record); } catch (DuplicateKeyException duplicate) {
            StudentContactRecordDO concurrent = recordMapper.selectByIdempotencyKey(idempotencyKey);
            if (concurrent == null) throw duplicate; return concurrent.getId();
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
        return record.getId();
    }

    @Override public List<StudyPlannerSimpleRespVO> getCollaboratorCandidates(Long relationId, String type, Long userId) {
        ServiceRelationDO relation = requireOwned(relationId, userId);
        if (!"accepted".equals(relation.getAcceptanceStatus())) throw exception(STUDENT_SERVICE_NOT_ACCEPTED);
        String scene = scene(type);
        if (scene == null) throw exception(STUDENT_COLLABORATOR_INVALID);
        Set<Long> ids = new LinkedHashSet<>();
        userRelationMapper.selectListBySourceUserIds(scene, List.of(userId)).stream()
                .filter(row -> CommonStatusEnum.ENABLE.getStatus().equals(row.getStatus()))
                .map(LeadAssignmentRelationDO::getTargetUserId).forEach(ids::add);
        return adminUserApi.getUserList(ids).stream().filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .sorted(Comparator.comparing(AdminUserRespDTO::getNickname).thenComparing(AdminUserRespDTO::getId))
                .map(user -> new StudyPlannerSimpleRespVO(user.getId(), user.getNickname())).toList();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void assignCollaborator(Long relationId, StudentCollaboratorAssignReqVO request, Long userId) {
        StudentCollaboratorAssignmentLogDO replay = assignmentLogMapper.selectByIdempotencyKey(request.getIdempotencyKey());
        if (replay != null) return;
        ServiceRelationDO relation = relationMapper.selectByIdForUpdate(relationId, TenantContextHolder.getRequiredTenantId());
        if (relation == null || !"active".equals(relation.getStatus())) throw exception(STUDENT_SERVICE_NOT_EXISTS);
        boolean owner = Objects.equals(relation.getOwnerUserId(), userId);
        boolean correction = permissionApi.hasAnyPermissions(userId, PERMISSION_COLLABORATOR_CORRECT);
        if (!owner && !correction || !"accepted".equals(relation.getAcceptanceStatus())) throw exception(STUDENT_PERMISSION_DENIED);
        Long previous = COLLABORATOR_DIRECTOR.equals(request.getCollaboratorType())
                ? relation.getContentDirectorUserId() : relation.getCareerPlannerUserId();
        if (previous != null && !correction) throw exception(STUDENT_COLLABORATOR_ALREADY_ASSIGNED);
        if (previous != null && (request.getCorrectionReason() == null || request.getCorrectionReason().isBlank())) {
            throw exception(STUDENT_COLLABORATOR_CORRECTION_REASON_REQUIRED);
        }
        Long relationSource = relation.getOwnerUserId();
        boolean candidate = getCollaboratorCandidates(relationId, request.getCollaboratorType(), relationSource).stream()
                .anyMatch(row -> row.getId().equals(request.getUserId()));
        if (!candidate) throw exception(STUDENT_COLLABORATOR_INVALID);
        if (!Objects.equals(relation.getVersion(), request.getVersion())) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
        if (COLLABORATOR_DIRECTOR.equals(request.getCollaboratorType())) relation.setContentDirectorUserId(request.getUserId());
        else relation.setCareerPlannerUserId(request.getUserId());
        relation.setVersion(relation.getVersion() + 1); relationMapper.updateById(relation);
        StudentCollaboratorAssignmentLogDO log = new StudentCollaboratorAssignmentLogDO();
        log.setServiceRelationId(relationId); log.setCollaboratorType(request.getCollaboratorType());
        log.setPreviousUserId(previous); log.setAssignedUserId(request.getUserId()); log.setOperatorUserId(userId);
        log.setReason(request.getCorrectionReason()); log.setIdempotencyKey(request.getIdempotencyKey()); assignmentLogMapper.insert(log);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void withdrawExtension(Long extensionId, String reason, Long userId) {
        StudentContactExtensionDO extension = extensionMapper.selectById(extensionId);
        if (extension == null || !"pending".equals(extension.getStatus()) || !userId.equals(extension.getApplicantUserId())) {
            throw exception(STUDENT_CONTACT_EXTENSION_NOT_EXISTS);
        }
        processInstanceApi.cancelProcessInstanceByStartUser(userId, extension.getProcessInstanceId(), reason);
        extension.setStatus("withdrawn"); extension.setDecisionReason(reason); extension.setResolvedAt(LocalDateTime.now());
        extension.setVersion(extension.getVersion() + 1); extensionMapper.updateById(extension);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void handleExtensionResult(String processInstanceId, Integer processStatus, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) return;
        StudentContactExtensionDO extension = extensionMapper.selectByProcessIdForUpdate(
                processInstanceId, TenantContextHolder.getRequiredTenantId());
        if (extension == null || !"pending".equals(extension.getStatus())) return;
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)
                && taskMapper.updatePendingDueAt(extension.getTaskId(), extension.getRequestedDueAt()) == 1) {
            extension.setStatus("approved");
        } else {
            extension.setStatus(BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus) ? "rejected" : "cancelled");
        }
        extension.setDecisionReason(reason); extension.setResolvedAt(LocalDateTime.now());
        extension.setVersion(extension.getVersion() + 1); extensionMapper.updateById(extension);
    }

    @Override
    public List<StudentContactExtensionRespVO> getExtensions(Long userId) {
        return extensionMapper.selectVisible(userId).stream().map(row -> {
            StudentContactExtensionRespVO result = new StudentContactExtensionRespVO();
            result.setId(row.getId()); result.setServiceRelationId(row.getServiceRelationId()); result.setTaskId(row.getTaskId());
            result.setStatus(row.getStatus()); result.setOriginalDueAt(row.getOriginalDueAt());
            result.setRequestedDueAt(row.getRequestedDueAt()); result.setReasonValue(row.getReasonValue());
            result.setReasonLabel(row.getReasonLabelSnapshot()); result.setDescription(row.getDescription());
            result.setAttachmentFileIds(parseLongs(row.getAttachmentFileIdsJson()));
            result.setApplicantUserId(row.getApplicantUserId()); result.setReviewerUserId(row.getReviewerUserId());
            result.setProcessInstanceId(row.getProcessInstanceId()); result.setDecisionReason(row.getDecisionReason());
            result.setSubmittedAt(row.getSubmittedAt()); result.setResolvedAt(row.getResolvedAt());
            return result;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        validateAttachments(attachments);
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
        process.setVariables(Map.of("extensionId", extension.getId(), "serviceRelationId", relation.getId(),
                "requestedDueAt", requestedDueAt.toString()));
        process.setStartUserSelectAssignees(Map.of(TASK_EXTENSION_REVIEW, List.of(reviewer)));
        try { extension.setProcessInstanceId(processInstanceApi.createProcessInstance(applicant, process)); }
        catch (RuntimeException ex) {
            log.error("[createExtension][extensionId({}) process start failed]", extension.getId(), ex);
            throw exception(STUDENT_CONTACT_PROCESS_UNAVAILABLE);
        }
        extensionMapper.updateById(extension);
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

    private void validateChecklist(StudentContactConfigVersionDO config, List<String> completed) {
        Set<String> required = checklist(config).stream().map(StudentContactContextRespVO.ChecklistItemVO::getKey)
                .collect(java.util.stream.Collectors.toSet());
        if (completed == null || !new HashSet<>(completed).containsAll(required)) throw exception(STUDENT_CONTACT_FORM_INVALID);
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
        if (relation == null || !"active".equals(relation.getStatus())) throw exception(STUDENT_SERVICE_NOT_EXISTS);
        if (!Objects.equals(relation.getOwnerUserId(), userId)
                && !Objects.equals(relation.getContentDirectorUserId(), userId)
                && !Objects.equals(relation.getCareerPlannerUserId(), userId)) {
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
    private String scene(String type) { return COLLABORATOR_DIRECTOR.equals(type) ? RELATION_PLANNER_DIRECTOR : COLLABORATOR_CAREER.equals(type) ? RELATION_PLANNER_CAREER : null; }
    private String name(AdminUserRespDTO user) { return user == null ? null : user.getNickname(); }
    @Override
    public StudentContactAttachmentRespVO uploadAttachment(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 20L * 1024 * 1024) {
            throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
        FileInfoRespDTO saved = fileApi.createFileInfo(file.getBytes(), file.getOriginalFilename(),
                "zsjos/student-contact", file.getContentType());
        return new StudentContactAttachmentRespVO(saved.getId(), file.getOriginalFilename(), saved.getUrl(),
                file.getContentType(), file.getSize());
    }
    private void validateAttachments(List<Long> fileIds) {
        if (fileIds == null) return;
        if (fileIds.size() > 9 || fileIds.stream().anyMatch(Objects::isNull)) throw exception(STUDENT_CONTACT_FORM_INVALID);
        for (Long fileId : new LinkedHashSet<>(fileIds)) {
            if (fileApi.getFileInfo(fileId) == null) throw exception(STUDENT_CONTACT_FORM_INVALID);
        }
    }
    private List<Long> parseLongs(String json) { return json == null ? List.of() : JsonUtils.parseArray(json, Long.class); }
    private List<String> parseStrings(String json) { return json == null ? List.of() : JsonUtils.parseArray(json, String.class); }
}
