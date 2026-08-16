package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.PageParam.PAGE_SIZE_NONE;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class WorkPlanServiceImpl implements WorkPlanService {
    private static final Set<String> FIELD_TYPES = Set.of("text", "textarea", "integer", "decimal", "money", "date", "datetime",
            "single_select", "multi_select", "user", "dept", "dict", "attachment", "link");
    private static final Set<String> NUMBER_TYPES = Set.of("integer", "decimal", "money");
    private static final Set<String> TEXT_TYPES = Set.of("text", "textarea", "link");

    @Resource private WorkPlanMapper planMapper;
    @Resource private WorkTaskMapper taskMapper;
    @Resource private WorkReportMapper reportMapper;
    @Resource private WorkPlanSummaryMapper summaryMapper;
    @Resource private WorkAttachmentMapper attachmentMapper;
    @Resource private WorkChangeMapper changeMapper;
    @Resource private WorkPlanFieldDefinitionMapper definitionMapper;
    @Resource private WorkPlanFieldValueMapper fieldValueMapper;
    @Resource private WorkPlanTemplateMapper templateMapper;
    @Resource private WorkPlanTemplateVersionMapper templateVersionMapper;
    @Resource private WorkPlanTemplateFieldMapper templateFieldMapper;
    @Resource private WorkPlanTemplateService templateService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PermissionApi permissionApi;
    @Resource private FileApi fileApi;
    @Resource private WorkPlanObjectPermissionProvider permissionProvider;
    @Resource private BusinessTaskCommandService taskCommandService;
    @Resource private WorkPlanNotifyEventPublisher notifyPublisher;

    @Override
    public PageResult<WorkPlanRespVO> getPage(WorkPlanPageReqVO reqVO, Long userId) {
        Visibility visibility = visibility(userId);
        PageResult<WorkPlanDO> page = planMapper.selectVisiblePage(reqVO, userId, visibility.planIds(), visibility.all());
        return new PageResult<>(page.getList().stream().map(plan -> convertPlan(plan, userId, false)).toList(), page.getTotal());
    }

    @Override
    public PageResult<WorkPlanRespVO> searchPage(WorkPlanSearchReqVO reqVO, Long userId) {
        Visibility visibility = visibility(userId);
        Collection<Long> matched = resolveDynamicFilters(reqVO);
        if (matched != null && matched.isEmpty()) return new PageResult<>(List.of(), 0L);
        PageResult<WorkPlanDO> page = planMapper.selectSearchPage(reqVO, userId, visibility.planIds(), visibility.all(), matched);
        return new PageResult<>(page.getList().stream().map(plan -> convertPlan(plan, userId, false)).toList(), page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_PLAN, bizId = "#id", action = "read")
    public WorkPlanRespVO get(Long id, Long userId) {
        return convertPlan(requirePlan(id), userId, true);
    }

    @Override
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_TASK, bizId = "#id", action = "read")
    public WorkTaskRespVO getTask(Long id, Long userId) {
        WorkTaskDO task = requireTask(id);
        List<WorkTaskDO> all = task.getPlanId() == null ? List.of(task) : taskMapper.selectListByPlanId(task.getPlanId());
        return convertTask(task, all, userId);
    }

    @Override
    public PageResult<WorkTaskRespVO> getMyTaskPage(PageParam pageParam, String status, Long userId) {
        PageResult<WorkTaskDO> page = taskMapper.selectMyPage(pageParam, status, userId);
        return new PageResult<>(page.getList().stream().map(task -> convertTask(task,
                task.getPlanId() == null ? List.of(task) : taskMapper.selectListByPlanId(task.getPlanId()), userId)).toList(), page.getTotal());
    }

    @Override
    @Transactional
    public Long create(WorkPlanSaveReqVO reqVO, Long userId) {
        validatePlanRequest(reqVO);
        List<WorkTaskSaveReqVO> tasks = reqVO.getTasks() == null ? List.of() : reqVO.getTasks();
        if (tasks.stream().anyMatch(task -> task.getParentTaskId() != null)) throw exception(WORK_PLAN_STATE_INVALID);
        WorkPlanTemplateVersionDO version = requireAvailableTemplateVersion(reqVO.getTemplateVersionId(), userId);
        WorkPlanTemplateDO template = requireTemplate(version.getTemplateId());
        if (!Objects.equals(version.getPeriodMode(), reqVO.getPeriodType())) throw exception(WORK_PLAN_PERIOD_INVALID);
        AdminUserRespDTO owner = requireEnabledUser(reqVO.getOwnerUserId(), false);
        WorkPlanDO plan = new WorkPlanDO();
        copyPlan(reqVO, plan); plan.setPlanTypeId(template.getTypeId()); plan.setTemplateId(template.getId());
        plan.setStatus(PLAN_DRAFT); plan.setCreatorUserId(userId); plan.setOwnerDeptId(owner.getDeptId()); plan.setVersion(0);
        planMapper.insert(plan);
        createFieldSnapshot(plan.getId(), version.getId(), reqVO.getSupplementalFields());
        replaceFields(plan.getId(), SECTION_PLAN, SUBJECT_PLAN, plan.getId(), reqVO.getPlanFields(), userId);
        for (WorkTaskSaveReqVO task : tasks) insertTask(plan, task, userId, TASK_DRAFT);
        return plan.getId();
    }

    @Override
    @Transactional
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_PLAN, bizId = "#id", action = "update")
    public void update(Long id, WorkPlanSaveReqVO reqVO, Long userId) {
        WorkPlanDO existing = requirePlan(id);
        requireVersion(reqVO.getVersion(), existing.getVersion());
        validatePlanRequest(reqVO);
        if (!Objects.equals(existing.getTemplateVersionId(), reqVO.getTemplateVersionId())) throw exception(WORK_PLAN_FIELD_INVALID);
        AdminUserRespDTO owner = requireEnabledUser(reqVO.getOwnerUserId(), false);
        WorkPlanDO update = new WorkPlanDO(); update.setId(id); copyPlan(reqVO, update); update.setOwnerDeptId(owner.getDeptId());
        if (PLAN_DRAFT.equals(existing.getStatus())) {
            List<WorkTaskSaveReqVO> tasks = reqVO.getTasks() == null ? List.of() : reqVO.getTasks();
            if (tasks.stream().anyMatch(task -> task.getParentTaskId() != null)) throw exception(WORK_PLAN_STATE_INVALID);
            if (planMapper.updateDraft(update, reqVO.getVersion()) != 1) conflict();
            clearDraftStructure(id);
            createFieldSnapshot(id, existing.getTemplateVersionId(), reqVO.getSupplementalFields());
            replaceFields(id, SECTION_PLAN, SUBJECT_PLAN, id, reqVO.getPlanFields(), userId);
            for (WorkTaskSaveReqVO task : tasks) insertTask(updatePlanIdentity(existing, update), task, userId, TASK_DRAFT);
            return;
        }
        requireState(PLAN_ACTIVE.equals(existing.getStatus()), true);
        if (!Objects.equals(existing.getOwnerUserId(), update.getOwnerUserId())) requireReason(reqVO.getReason());
        Map<String, Object> before = planSnapshot(existing);
        if (planMapper.adjustActive(update, reqVO.getVersion()) != 1) conflict();
        appendSupplementalFields(id, reqVO.getSupplementalFields());
        replaceFields(id, SECTION_PLAN, SUBJECT_PLAN, id, reqVO.getPlanFields(), userId);
        update.setTemplateId(existing.getTemplateId()); update.setTemplateVersionId(existing.getTemplateVersionId());
        update.setStatus(PLAN_ACTIVE); update.setCreatorUserId(existing.getCreatorUserId()); update.setVersion(reqVO.getVersion() + 1);
        addChange(SUBJECT_PLAN, id, "adjusted", before, planSnapshot(update), reqVO.getReason(), userId);
        if (!Objects.equals(existing.getOwnerUserId(), update.getOwnerUserId()) && isSummaryReady(id)) {
            cancelSummaryTask(id, "计划负责人已调整"); createSummaryTask(update);
        }
    }

    @Override
    @Transactional
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_PLAN, bizId = "#id", action = "publish")
    public void publish(Long id, Integer version, Long userId) {
        WorkPlanDO plan = requirePlan(id); requireVersion(version, plan.getVersion()); requireState(PLAN_DRAFT.equals(plan.getStatus()), true);
        List<WorkTaskDO> tasks = taskMapper.selectListByPlanId(id);
        LocalDateTime now = LocalDateTime.now();
        if (planMapper.transition(id, version, PLAN_DRAFT, PLAN_ACTIVE, now, null) != 1) conflict();
        for (WorkTaskDO task : tasks) {
            if (taskMapper.transition(task.getId(), task.getVersion(), List.of(TASK_DRAFT), TASK_PENDING, now, null) != 1) conflict();
            task.setStatus(TASK_PENDING); task.setVersion(task.getVersion() + 1);
            createExecutionTask(task, 1);
            notifyPublisher.publishTask(SCENE_ASSIGNED, task, "work-task-assigned:" + task.getId(), userId, now, Map.of());
        }
    }

    @Override
    @Transactional
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_PLAN, bizId = "#id", action = "cancel")
    public void cancel(Long id, WorkPlanCancelReqVO reqVO, Long userId) {
        WorkPlanDO plan = requirePlanForUpdate(id); requireVersion(reqVO.getVersion(), plan.getVersion());
        requireState(Set.of(PLAN_DRAFT, PLAN_ACTIVE).contains(plan.getStatus()), true);
        LocalDateTime now = LocalDateTime.now();
        List<WorkTaskDO> tasks = taskMapper.selectListByPlanId(id);
        for (WorkTaskDO task : tasks) {
            if (Set.of(TASK_DRAFT, TASK_PENDING, TASK_AWAITING_CONFIRMATION).contains(task.getStatus())) {
                cancelTaskRow(task, reqVO.getReason(), userId, now, PLAN_ACTIVE.equals(plan.getStatus()));
            }
        }
        cancelSummaryTask(id, reqVO.getReason());
        if (planMapper.transition(id, reqVO.getVersion(), plan.getStatus(), PLAN_CANCELLED, now, reqVO.getReason()) != 1) conflict();
        if (PLAN_ACTIVE.equals(plan.getStatus())) addChange(SUBJECT_PLAN, id, "cancelled", planSnapshot(plan), null, reqVO.getReason(), userId);
    }

    @Override
    @Transactional
    public Long addTask(Long planId, WorkTaskSaveReqVO reqVO, Long userId) {
        WorkPlanDO plan = requirePlanForUpdate(planId); requireState(PLAN_ACTIVE.equals(plan.getStatus()), true); requireReason(reqVO.getReason());
        if (reqVO.getParentTaskId() == null) {
            permissionProvider.check(planId, "assign", userId);
        } else {
            WorkTaskDO parent = requireTask(reqVO.getParentTaskId());
            if (!Objects.equals(parent.getPlanId(), planId) || !TASK_PENDING.equals(parent.getStatus())
                    || !permissionProvider.hasTaskPermission(parent, "decompose", userId)) throw exception(WORK_PLAN_PERMISSION_DENIED);
        }
        WorkTaskDO task = insertTask(plan, reqVO, userId, TASK_PENDING);
        addChange(SUBJECT_TASK, task.getId(), "added", null, task, reqVO.getReason(), userId);
        cancelSummaryTask(planId, "新增工作任务");
        createExecutionTask(task, 1);
        notifyPublisher.publishTask(SCENE_ASSIGNED, task, "work-task-added:" + task.getId(), userId, LocalDateTime.now(), Map.of("reason", reqVO.getReason()));
        return task.getId();
    }

    @Override
    @Transactional
    public Long createTemporaryTask(WorkTaskSaveReqVO reqVO, Long userId) {
        if (reqVO.getParentTaskId() != null || reqVO.getTaskFields() != null && !reqVO.getTaskFields().isEmpty()) throw exception(WORK_PLAN_FIELD_INVALID);
        WorkTaskDO task = buildTask(null, reqVO, userId, TASK_PENDING);
        taskMapper.insert(task); createExecutionTask(task, 1);
        notifyPublisher.publishTask(SCENE_ASSIGNED, task, "work-task-temporary:" + task.getId(), userId, LocalDateTime.now(), Map.of());
        return task.getId();
    }

    @Override
    @Transactional
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_TASK, bizId = "#taskId", action = "assign")
    public void adjustTask(Long taskId, WorkTaskSaveReqVO reqVO, Long userId) {
        WorkTaskDO existing = requireTask(taskId); requireVersion(reqVO.getVersion(), existing.getVersion());
        requireState(TASK_PENDING.equals(existing.getStatus()), false);
        if (!Objects.equals(existing.getParentTaskId(), reqVO.getParentTaskId())) throw exception(WORK_TASK_STATE_INVALID);
        WorkPlanDO plan = existing.getPlanId() == null ? null : requirePlan(existing.getPlanId());
        WorkTaskDO update = buildTask(plan, reqVO, existing.getAssignerUserId(), TASK_PENDING); update.setId(taskId);
        if (assignmentChanged(existing, update)) requireReason(reqVO.getReason());
        WorkTaskDO before = copyTask(existing);
        if (taskMapper.adjust(update, reqVO.getVersion()) != 1) conflict();
        replaceFields(existing.getPlanId(), SECTION_TASK, SUBJECT_TASK, taskId, reqVO.getTaskFields(), userId);
        update.setVersion(reqVO.getVersion() + 1); addChange(SUBJECT_TASK, taskId, "adjusted", before, update, reqVO.getReason(), userId);
        taskCommandService.updatePending(TASK_TYPE_WORK_TASK, taskId, update.getAssigneeUserId(), update.getTitle(), "工作任务",
                update.getDueAt(), update.getRemindAt());
        notifyPublisher.publishTask(SCENE_ADJUSTED, update, "work-task-adjusted:" + taskId + ":" + update.getVersion(), userId,
                LocalDateTime.now(), Map.of("reason", Objects.toString(reqVO.getReason(), "")));
    }

    @Override
    @Transactional
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_TASK, bizId = "#taskId", action = "cancel")
    public void cancelTask(Long taskId, WorkPlanCancelReqVO reqVO, Long userId) {
        WorkTaskDO task = requireTask(taskId); requireVersion(reqVO.getVersion(), task.getVersion());
        requireState(Set.of(TASK_PENDING, TASK_AWAITING_CONFIRMATION).contains(task.getStatus()), false);
        List<WorkTaskDO> all = task.getPlanId() == null ? List.of(task) : taskMapper.selectListByPlanId(task.getPlanId());
        List<WorkTaskDO> targets = cancellationTargets(taskId, all, Boolean.TRUE.equals(reqVO.getCascadeChildren()));
        Collections.reverse(targets);
        LocalDateTime now = LocalDateTime.now();
        for (WorkTaskDO row : targets) {
            if (!Set.of(TASK_COMPLETED, TASK_CANCELLED).contains(row.getStatus())) cancelTaskRow(row, reqVO.getReason(), userId, now, true);
        }
        markSummaryReady(task.getPlanId(), userId);
    }

    @Override
    @Transactional
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_TASK, bizId = "#taskId", action = "report")
    public void submitReport(Long taskId, WorkReportSubmitReqVO reqVO, Long userId) {
        WorkTaskDO task = requireTask(taskId); requireVersion(reqVO.getVersion(), task.getVersion());
        requireState(TASK_PENDING.equals(task.getStatus()), false);
        List<Long> fileIds = normalizeFileIds(reqVO.getInfraFileIds(), userId);
        WorkReportDO latest = reportMapper.selectLatest(taskId);
        int revision = latest == null ? 1 : latest.getRevisionNo() + 1;
        LocalDateTime now = LocalDateTime.now();
        WorkReportDO report = new WorkReportDO().setTaskId(taskId).setRevisionNo(revision)
                .setCompletionSummary(reqVO.getCompletionSummary()).setSubmitterUserId(userId).setSubmittedAt(now);
        if (!Boolean.TRUE.equals(task.getConfirmationRequired())) report.setConfirmationDecision(CONFIRM_AUTO_APPROVED)
                .setConfirmedByUserId(userId).setConfirmedAt(now);
        reportMapper.insert(report);
        replaceFields(task.getPlanId(), SECTION_REPORT, SUBJECT_REPORT, report.getId(), reqVO.getReportFields(), userId);
        saveAttachments(SUBJECT_REPORT, report.getId(), fileIds);
        String target = Boolean.TRUE.equals(task.getConfirmationRequired()) ? TASK_AWAITING_CONFIRMATION : TASK_COMPLETED;
        if (taskMapper.transition(taskId, reqVO.getVersion(), List.of(TASK_PENDING), target, now, null) != 1) conflict();
        taskCommandService.complete(TASK_TYPE_WORK_TASK, taskId, userId, now);
        task.setStatus(target); task.setVersion(reqVO.getVersion() + 1);
        if (TASK_AWAITING_CONFIRMATION.equals(target)) {
            createConfirmationTask(task, revision);
            notifyPublisher.publishTask(SCENE_REPORT_SUBMITTED, task, "work-report-submitted:" + report.getId(), userId, now, Map.of());
        } else markSummaryReady(task.getPlanId(), userId);
    }

    @Override
    @Transactional
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_TASK, bizId = "#taskId", action = "confirm")
    public void confirmReport(Long taskId, WorkReportConfirmReqVO reqVO, Long userId) {
        WorkTaskDO task = requireTask(taskId); requireVersion(reqVO.getVersion(), task.getVersion());
        requireState(TASK_AWAITING_CONFIRMATION.equals(task.getStatus()), false);
        if (!Set.of(CONFIRM_APPROVED, CONFIRM_REJECTED).contains(reqVO.getDecision())) throw exception(WORK_TASK_STATE_INVALID);
        WorkReportDO report = reportMapper.selectLatest(taskId);
        LocalDateTime now = LocalDateTime.now();
        if (report == null || reportMapper.confirm(report.getId(), reqVO.getDecision(), reqVO.getComment(), userId, now) != 1) {
            throw exception(WORK_TASK_STATE_INVALID);
        }
        String target = CONFIRM_APPROVED.equals(reqVO.getDecision()) ? TASK_COMPLETED : TASK_PENDING;
        if (taskMapper.transition(taskId, reqVO.getVersion(), List.of(TASK_AWAITING_CONFIRMATION), target, now, null) != 1) conflict();
        taskCommandService.complete(TASK_TYPE_WORK_CONFIRM, taskId, userId, now);
        task.setStatus(target); task.setVersion(reqVO.getVersion() + 1);
        if (TASK_PENDING.equals(target)) {
            cancelSummaryTask(task.getPlanId(), "完成汇报被退回");
            createExecutionTask(task, report.getRevisionNo() + 1);
            notifyPublisher.publishTask(SCENE_CONFIRM_REJECTED, task, "work-report-rejected:" + report.getId(), userId, now,
                    Map.of("reason", Objects.toString(reqVO.getComment(), "")));
        } else {
            notifyPublisher.publishTask(SCENE_CONFIRM_APPROVED, task, "work-report-approved:" + report.getId(), userId, now, Map.of());
            markSummaryReady(task.getPlanId(), userId);
        }
    }

    @Override
    @Transactional
    @ZsjosPermission(bizType = BIZ_TYPE_WORK_PLAN, bizId = "#planId", action = "close")
    public void submitSummary(Long planId, WorkPlanSummaryReqVO reqVO, Long userId) {
        WorkPlanDO plan = requirePlanForUpdate(planId); requireVersion(reqVO.getVersion(), plan.getVersion());
        requireState(PLAN_ACTIVE.equals(plan.getStatus()), true);
        if (summaryMapper.selectByPlanId(planId) != null) throw exception(WORK_PLAN_STATE_INVALID);
        if (taskMapper.countActiveByPlanId(planId) > 0) throw exception(WORK_PLAN_STATE_INVALID);
        List<Long> fileIds = normalizeFileIds(reqVO.getInfraFileIds(), userId);
        LocalDateTime now = LocalDateTime.now();
        WorkPlanSummaryDO summary = new WorkPlanSummaryDO().setPlanId(planId).setSummary(reqVO.getSummary())
                .setSubmitterUserId(userId).setSubmittedAt(now);
        summaryMapper.insert(summary);
        replaceFields(planId, SECTION_SUMMARY, SUBJECT_SUMMARY, summary.getId(), reqVO.getSummaryFields(), userId);
        saveAttachments(SUBJECT_SUMMARY, summary.getId(), fileIds);
        if (planMapper.transition(planId, reqVO.getVersion(), PLAN_ACTIVE, PLAN_COMPLETED, now, null) != 1) conflict();
        taskCommandService.complete(TASK_TYPE_PLAN_SUMMARY, planId, userId, now);
    }

    @Override
    public WorkPlanAttachmentUploadRespVO uploadAttachment(MultipartFile file, Long userId) throws IOException {
        if (file.isEmpty()) throw exception(WORK_PLAN_ATTACHMENT_INVALID);
        FileInfoRespDTO info = fileApi.createFileInfo(file.getBytes(), file.getOriginalFilename(), "zsjos/work-plan", file.getContentType());
        if (info == null || info.getId() == null || !String.valueOf(userId).equals(info.getCreator())) throw exception(WORK_PLAN_ATTACHMENT_INVALID);
        return new WorkPlanAttachmentUploadRespVO(info.getId(), info.getName(), info.getType(), info.getSize());
    }

    @Override
    public WorkPlanExportData export(WorkPlanSearchReqVO reqVO, Long userId) {
        if (reqVO.getTemplateId() == null) throw exception(WORK_PLAN_FIELD_INVALID);
        reqVO.setPageSize(PAGE_SIZE_NONE);
        List<WorkPlanRespVO> plans = searchPage(reqVO, userId).getList().stream()
                .map(plan -> get(plan.getId(), userId)).toList();
        WorkPlanTemplateVersionDO current = templateVersionMapper.selectPublished(reqVO.getTemplateId());
        List<WorkPlanTemplateFieldDO> columns = current == null ? List.of() : templateFieldMapper.selectListByVersionId(current.getId()).stream()
                .filter(field -> Boolean.TRUE.equals(field.getExportable())).toList();
        List<List<String>> headers = exportHeaders(columns);
        List<List<Object>> rows = new ArrayList<>();
        Map<Long, String> userNames = new HashMap<>();
        for (WorkPlanRespVO plan : plans) {
            for (WorkTaskRespVO task : plan.getTasks()) rows.add(exportRow(plan, task, columns, userNames));
        }
        return new WorkPlanExportData(headers, rows);
    }

    private WorkTaskDO insertTask(WorkPlanDO plan, WorkTaskSaveReqVO reqVO, Long userId, String status) {
        WorkTaskDO task = buildTask(plan, reqVO, userId, status);
        taskMapper.insert(task);
        replaceFields(plan.getId(), SECTION_TASK, SUBJECT_TASK, task.getId(), reqVO.getTaskFields(), userId);
        return task;
    }

    private WorkTaskDO buildTask(WorkPlanDO plan, WorkTaskSaveReqVO reqVO, Long assignerUserId, String status) {
        AdminUserRespDTO assignee = requireEnabledUser(reqVO.getAssigneeUserId(), false);
        if (Boolean.TRUE.equals(reqVO.getConfirmationRequired())) requireEnabledUser(reqVO.getConfirmerUserId(), true);
        return new WorkTaskDO().setPlanId(plan == null ? null : plan.getId()).setParentTaskId(reqVO.getParentTaskId())
                .setTitle(reqVO.getTitle()).setDescription(reqVO.getDescription()).setDeliverableRequirement(reqVO.getDeliverableRequirement())
                .setAssigneeUserId(reqVO.getAssigneeUserId()).setAssigneeDeptId(assignee.getDeptId()).setAssignerUserId(assignerUserId)
                .setDueAt(reqVO.getDueAt()).setRemindAt(reqVO.getRemindAt())
                .setConfirmationRequired(Boolean.TRUE.equals(reqVO.getConfirmationRequired()))
                .setConfirmerUserId(Boolean.TRUE.equals(reqVO.getConfirmationRequired()) ? reqVO.getConfirmerUserId() : null)
                .setStatus(status).setVersion(0);
    }

    void validatePlanRequest(WorkPlanSaveReqVO reqVO) {
        if (!PERIOD_TYPES.contains(reqVO.getPeriodType()) || reqVO.getEndDate().isBefore(reqVO.getStartDate())) throw exception(WORK_PLAN_PERIOD_INVALID);
    }

    private WorkPlanTemplateVersionDO requireAvailableTemplateVersion(Long versionId, Long userId) {
        WorkPlanTemplateVersionDO version = templateVersionMapper.selectById(versionId);
        if (version == null || !"published".equals(version.getStatus()) || templateService.getAvailableTemplates(userId).stream()
                .noneMatch(template -> Objects.equals(template.getVersionId(), versionId))) throw exception(WORK_PLAN_FIELD_INVALID);
        return version;
    }

    private void createFieldSnapshot(Long planId, Long versionId, List<WorkPlanTemplateFieldSaveReqVO> supplemental) {
        Set<String> keys = new HashSet<>();
        for (WorkPlanTemplateFieldDO field : templateFieldMapper.selectListByVersionId(versionId)) {
            keys.add(field.getFieldKey());
            definitionMapper.insert(new WorkPlanFieldDefinitionDO().setPlanId(planId).setTemplateFieldId(field.getId())
                    .setFieldKey(field.getFieldKey()).setLabel(field.getLabel()).setSection(field.getSection()).setFieldType(field.getFieldType())
                    .setRequired(field.getRequired()).setUnit(field.getUnit()).setPlaceholder(field.getPlaceholder())
                    .setFilterable(field.getFilterable()).setExportable(field.getExportable()).setOptionsJson(field.getOptionsJson())
                    .setDefaultValueJson(field.getDefaultValueJson()).setOrigin(FIELD_ORIGIN_TEMPLATE).setSort(field.getSort()));
        }
        List<WorkPlanTemplateFieldSaveReqVO> extra = supplemental == null ? List.of() : supplemental;
        appendSupplementalFields(planId, extra, keys);
    }

    private void appendSupplementalFields(Long planId, List<WorkPlanTemplateFieldSaveReqVO> supplemental) {
        Set<String> keys = definitionMapper.selectListByPlanId(planId).stream()
                .map(WorkPlanFieldDefinitionDO::getFieldKey).collect(Collectors.toSet());
        appendSupplementalFields(planId, supplemental == null ? List.of() : supplemental, keys);
    }

    private void appendSupplementalFields(Long planId, List<WorkPlanTemplateFieldSaveReqVO> supplemental, Set<String> keys) {
        List<WorkPlanTemplateFieldSaveReqVO> extra = supplemental == null ? List.of() : supplemental;
        for (WorkPlanTemplateFieldSaveReqVO field : extra) {
            validateSupplementalField(field);
            String key;
            do key = "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24); while (!keys.add(key));
            definitionMapper.insert(new WorkPlanFieldDefinitionDO().setPlanId(planId).setFieldKey(key).setLabel(field.getLabel())
                    .setSection(field.getSection()).setFieldType(field.getFieldType()).setRequired(Boolean.TRUE.equals(field.getRequired()))
                    .setUnit(field.getUnit()).setPlaceholder(field.getPlaceholder()).setFilterable(false)
                    .setExportable(!Boolean.FALSE.equals(field.getExportable())).setOptionsJson(field.getOptionsJson())
                    .setDefaultValueJson(field.getDefaultValueJson()).setOrigin(FIELD_ORIGIN_SUPPLEMENTAL).setSort(field.getSort() == null ? 0 : field.getSort()));
        }
    }

    private void validateSupplementalField(WorkPlanTemplateFieldSaveReqVO field) {
        if (field.getLabel() == null || field.getLabel().isBlank() || !FIELD_SECTIONS.contains(field.getSection())
                || !FIELD_TYPES.contains(field.getFieldType()) || Boolean.TRUE.equals(field.getFilterable())) throw exception(WORK_PLAN_FIELD_INVALID);
        if (Set.of("single_select", "multi_select").contains(field.getFieldType())) {
            try {
                if (field.getOptionsJson() == null || JsonUtils.parseArray(field.getOptionsJson(), Object.class).isEmpty()) throw exception(WORK_PLAN_FIELD_INVALID);
            } catch (RuntimeException ex) { throw exception(WORK_PLAN_FIELD_INVALID); }
        }
    }

    private void replaceFields(Long planId, String section, String subjectType, Long subjectId,
                               Map<String, Object> values, Long userId) {
        if (planId == null) {
            if (values != null && !values.isEmpty()) throw exception(WORK_PLAN_FIELD_INVALID);
            return;
        }
        Map<String, Object> input = values == null ? Map.of() : values;
        List<WorkPlanFieldDefinitionDO> fields = definitionMapper.selectListByPlanId(planId).stream()
                .filter(field -> section.equals(field.getSection())).toList();
        Set<String> allowed = fields.stream().map(WorkPlanFieldDefinitionDO::getFieldKey).collect(Collectors.toSet());
        if (!allowed.containsAll(input.keySet())) throw exception(WORK_PLAN_FIELD_INVALID);
        List<TypedValue> typed = new ArrayList<>();
        for (WorkPlanFieldDefinitionDO field : fields) {
            Object raw = input.get(field.getFieldKey());
            if (isEmpty(raw) && field.getDefaultValueJson() != null) raw = JsonUtils.parseObject(field.getDefaultValueJson(), Object.class);
            if (isEmpty(raw)) {
                if (Boolean.TRUE.equals(field.getRequired())) throw exception(WORK_PLAN_FIELD_INVALID);
                continue;
            }
            if ("attachment".equals(field.getFieldType())) raw = normalizeAttachmentValue(raw, userId);
            validateReferenceValue(field, raw);
            typed.add(new TypedValue(field, raw));
        }
        fieldValueMapper.deleteBySubject(subjectType, subjectId);
        for (TypedValue value : typed) {
            WorkPlanFieldValueDO row = new WorkPlanFieldValueDO().setFieldDefinitionId(value.field().getId())
                    .setSubjectType(subjectType).setSubjectId(subjectId);
            applyValue(row, value); fieldValueMapper.insert(row);
        }
    }

    private void validateReferenceValue(WorkPlanFieldDefinitionDO field, Object raw) {
        try {
            if ("user".equals(field.getFieldType())) requireEnabledUser(Long.valueOf(String.valueOf(raw)), false);
            if ("dept".equals(field.getFieldType())) deptApi.validateDeptList(List.of(Long.valueOf(String.valueOf(raw))));
        } catch (RuntimeException ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) throw ex;
            throw exception(WORK_PLAN_FIELD_INVALID);
        }
    }

    private Collection<Long> resolveDynamicFilters(WorkPlanSearchReqVO reqVO) {
        List<WorkPlanDynamicFilterReqVO> filters = reqVO.getDynamicFilters() == null ? List.of() : reqVO.getDynamicFilters();
        if (filters.isEmpty()) return null;
        if (reqVO.getTemplateId() == null) throw exception(WORK_PLAN_FIELD_INVALID);
        List<WorkPlanTemplateVersionDO> versions = templateVersionMapper.selectListByTemplateId(reqVO.getTemplateId());
        Map<String, WorkPlanTemplateFieldDO> definitions = versions.stream().flatMap(version -> templateFieldMapper.selectListByVersionId(version.getId()).stream())
                .filter(field -> SECTION_PLAN.equals(field.getSection()) && Boolean.TRUE.equals(field.getFilterable()))
                .collect(Collectors.toMap(WorkPlanTemplateFieldDO::getFieldKey, Function.identity(), (first, second) -> second));
        Set<Long> matched = null;
        for (WorkPlanDynamicFilterReqVO filter : filters) {
            WorkPlanTemplateFieldDO field = definitions.get(filter.getFieldKey());
            if (field == null) throw exception(WORK_PLAN_FIELD_INVALID);
            Set<Long> current = new LinkedHashSet<>(queryFilter(field, filter));
            if (matched == null) matched = current; else matched.retainAll(current);
            if (matched.isEmpty()) return matched;
        }
        return matched;
    }

    private List<Long> queryFilter(WorkPlanTemplateFieldDO field, WorkPlanDynamicFilterReqVO filter) {
        String operator = filter.getOperator(); Object raw = filter.getValue(); String type = field.getFieldType();
        try {
            if (TEXT_TYPES.contains(type) && "contains".equals(operator)) return fieldValueMapper.selectPlanIdsTextContains(field.getFieldKey(), String.valueOf(raw));
            if (NUMBER_TYPES.contains(type)) {
                BigDecimal value = new BigDecimal(String.valueOf(raw));
                return switch (operator) {
                    case "eq" -> fieldValueMapper.selectPlanIdsDecimalEquals(field.getFieldKey(), value);
                    case "gte" -> fieldValueMapper.selectPlanIdsDecimalGte(field.getFieldKey(), value);
                    case "lte" -> fieldValueMapper.selectPlanIdsDecimalLte(field.getFieldKey(), value);
                    default -> throw exception(WORK_PLAN_FIELD_INVALID);
                };
            }
            if (Set.of("date", "datetime").contains(type)) {
                LocalDateTime value = "date".equals(type) ? LocalDate.parse(String.valueOf(raw)).atStartOfDay() : LocalDateTime.parse(String.valueOf(raw));
                return switch (operator) {
                    case "eq", "gte" -> fieldValueMapper.selectPlanIdsDatetimeGte(field.getFieldKey(), value);
                    case "lte" -> fieldValueMapper.selectPlanIdsDatetimeLte(field.getFieldKey(), value);
                    default -> throw exception(WORK_PLAN_FIELD_INVALID);
                };
            }
            if ("multi_select".equals(type) && "eq".equals(operator)) return fieldValueMapper.selectPlanIdsJsonContains(field.getFieldKey(), String.valueOf(raw));
            if (!"attachment".equals(type) && "eq".equals(operator)) {
                Long ref = Set.of("user", "dept").contains(type) ? Long.valueOf(String.valueOf(raw)) : null;
                return fieldValueMapper.selectPlanIdsExact(field.getFieldKey(), String.valueOf(raw), ref);
            }
        } catch (RuntimeException ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) throw ex;
        }
        throw exception(WORK_PLAN_FIELD_INVALID);
    }

    private void clearDraftStructure(Long planId) {
        List<WorkTaskDO> tasks = taskMapper.selectListByPlanId(planId);
        for (WorkTaskDO task : tasks) fieldValueMapper.deleteBySubject(SUBJECT_TASK, task.getId());
        if (!tasks.isEmpty()) taskMapper.deleteByIds(tasks.stream().map(WorkTaskDO::getId).toList());
        fieldValueMapper.deleteBySubject(SUBJECT_PLAN, planId);
        definitionMapper.deleteHardByPlanId(planId);
    }

    private void cancelTaskRow(WorkTaskDO task, String reason, Long userId, LocalDateTime now, boolean audit) {
        WorkTaskDO before = copyTask(task);
        if (taskMapper.transition(task.getId(), task.getVersion(), List.of(task.getStatus()), TASK_CANCELLED, now, reason) != 1) conflict();
        taskCommandService.cancel(TASK_TYPE_WORK_TASK, task.getId(), null, now, reason);
        taskCommandService.cancel(TASK_TYPE_WORK_CONFIRM, task.getId(), null, now, reason);
        task.setStatus(TASK_CANCELLED); task.setVersion(task.getVersion() + 1); task.setCancelReason(reason); task.setCancelledAt(now);
        if (audit) addChange(SUBJECT_TASK, task.getId(), "cancelled", before, task, reason, userId);
        notifyPublisher.publishTask(SCENE_CANCELLED, task, "work-task-cancelled:" + task.getId(), userId, now, Map.of("reason", reason));
    }

    List<WorkTaskDO> cancellationTargets(Long rootId, List<WorkTaskDO> all, boolean cascadeChildren) {
        if (!cascadeChildren) return new ArrayList<>(all.stream().filter(row -> Objects.equals(row.getId(), rootId)).toList());
        Map<Long, List<WorkTaskDO>> children = all.stream().filter(task -> task.getParentTaskId() != null)
                .collect(Collectors.groupingBy(WorkTaskDO::getParentTaskId));
        List<WorkTaskDO> result = new ArrayList<>(); Deque<Long> stack = new ArrayDeque<>(); stack.push(rootId);
        while (!stack.isEmpty()) {
            Long id = stack.pop(); WorkTaskDO task = all.stream().filter(row -> Objects.equals(row.getId(), id)).findFirst().orElse(null);
            if (task != null) result.add(task);
            for (WorkTaskDO child : children.getOrDefault(id, List.of())) stack.push(child.getId());
        }
        return result;
    }

    boolean assignmentChanged(WorkTaskDO existing, WorkTaskDO update) {
        return !Objects.equals(existing.getAssigneeUserId(), update.getAssigneeUserId())
                || !Objects.equals(existing.getConfirmationRequired(), update.getConfirmationRequired())
                || !Objects.equals(existing.getConfirmerUserId(), update.getConfirmerUserId());
    }

    private boolean isSummaryReady(Long planId) {
        return planId != null && taskMapper.countActiveByPlanId(planId) == 0 && taskMapper.countCompletedByPlanId(planId) > 0;
    }

    private void markSummaryReady(Long planId, Long operatorUserId) {
        if (planId == null || !isSummaryReady(planId)) return;
        WorkPlanDO plan = requirePlan(planId);
        if (!PLAN_ACTIVE.equals(plan.getStatus()) || summaryMapper.selectByPlanId(planId) != null) return;
        createSummaryTask(plan);
        notifyPublisher.publishPlan(SCENE_SUMMARY_READY, plan, "work-plan-summary-ready:" + planId,
                operatorUserId, LocalDateTime.now(), Map.of());
    }

    private void createExecutionTask(WorkTaskDO task, int revision) {
        taskCommandService.create(new BusinessTaskCreateCommand(TASK_TYPE_WORK_TASK, BIZ_TYPE_WORK_TASK, task.getId(),
                task.getAssigneeUserId(), task.getTitle(), "工作任务", ACTION_OPEN_WORK_TASK,
                task.getDueAt(), task.getRemindAt(), null, "work-task:" + task.getId() + ":execute:" + revision));
    }

    private void createConfirmationTask(WorkTaskDO task, int revision) {
        taskCommandService.create(new BusinessTaskCreateCommand(TASK_TYPE_WORK_CONFIRM, BIZ_TYPE_WORK_TASK, task.getId(),
                task.getConfirmerUserId(), "确认：" + task.getTitle(), "任务完成确认", ACTION_CONFIRM_WORK_TASK,
                null, null, null, "work-task:" + task.getId() + ":confirm:" + revision));
    }

    private void createSummaryTask(WorkPlanDO plan) {
        taskCommandService.create(new BusinessTaskCreateCommand(TASK_TYPE_PLAN_SUMMARY, BIZ_TYPE_WORK_PLAN, plan.getId(),
                plan.getOwnerUserId(), "计划总结：" + plan.getTitle(), "请结合任务完成情况提交计划总结", ACTION_SUMMARIZE_WORK_PLAN,
                null, null, null, "work-plan:" + plan.getId() + ":summary"));
    }

    private void cancelSummaryTask(Long planId, String reason) {
        if (planId != null) taskCommandService.cancel(TASK_TYPE_PLAN_SUMMARY, planId, null, LocalDateTime.now(), reason);
    }

    private void saveAttachments(String subjectType, Long subjectId, List<Long> fileIds) {
        for (int i = 0; i < fileIds.size(); i++) attachmentMapper.insert(new WorkAttachmentDO()
                .setSubjectType(subjectType).setSubjectId(subjectId).setInfraFileId(fileIds.get(i)).setSort(i));
    }

    private List<Long> normalizeFileIds(List<Long> values, Long userId) {
        List<Long> ids = values == null ? List.of() : values.stream().filter(Objects::nonNull).distinct().toList();
        ids.forEach(id -> validateAttachmentReference(id, userId)); return ids;
    }

    private List<Long> normalizeAttachmentValue(Object raw, Long userId) {
        Collection<?> values = raw instanceof Collection<?> collection ? collection
                : raw instanceof Object[] array ? Arrays.asList(array) : null;
        if (values == null) throw exception(WORK_PLAN_FIELD_INVALID);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        try { for (Object value : values) ids.add(Long.valueOf(String.valueOf(value))); }
        catch (RuntimeException ex) { throw exception(WORK_PLAN_FIELD_INVALID); }
        ids.forEach(id -> validateAttachmentReference(id, userId)); return new ArrayList<>(ids);
    }

    private void validateAttachmentReference(Long fileId, Long userId) {
        FileInfoRespDTO file;
        try { file = fileApi.getFileInfo(fileId); } catch (RuntimeException ex) { throw exception(WORK_PLAN_ATTACHMENT_INVALID); }
        if (file == null || file.getPath() == null || !file.getPath().startsWith("zsjos/work-plan/")
                || !String.valueOf(userId).equals(file.getCreator())) throw exception(WORK_PLAN_ATTACHMENT_INVALID);
    }

    private void applyValue(WorkPlanFieldValueDO row, TypedValue value) {
        String type = value.field().getFieldType(); Object raw = value.value();
        try {
            if (NUMBER_TYPES.contains(type)) row.setValueDecimal(new BigDecimal(String.valueOf(raw)));
            else if ("date".equals(type)) row.setValueDatetime(LocalDate.parse(String.valueOf(raw)).atStartOfDay());
            else if ("datetime".equals(type)) row.setValueDatetime(LocalDateTime.parse(String.valueOf(raw)));
            else if (Set.of("user", "dept").contains(type)) row.setValueRefId(Long.valueOf(String.valueOf(raw)));
            else if (Set.of("multi_select", "attachment").contains(type)) row.setValueJson(JsonUtils.toJsonString(raw));
            else row.setValueText(String.valueOf(raw));
        } catch (RuntimeException ex) { throw exception(WORK_PLAN_FIELD_INVALID); }
    }

    private Map<String, Object> readFields(Long planId, String subjectType, Long subjectId) {
        if (planId == null) return Map.of();
        Map<Long, WorkPlanFieldDefinitionDO> definitions = definitionMapper.selectListByPlanId(planId).stream()
                .collect(Collectors.toMap(WorkPlanFieldDefinitionDO::getId, Function.identity()));
        Map<String, Object> result = new LinkedHashMap<>();
        for (WorkPlanFieldValueDO value : fieldValueMapper.selectListBySubject(subjectType, subjectId)) {
            WorkPlanFieldDefinitionDO field = definitions.get(value.getFieldDefinitionId()); if (field == null) continue;
            Object resolved = value.getValueText() != null ? value.getValueText()
                    : value.getValueDecimal() != null ? value.getValueDecimal()
                    : value.getValueDatetime() != null ? ("date".equals(field.getFieldType()) ? value.getValueDatetime().toLocalDate() : value.getValueDatetime())
                    : value.getValueRefId() != null ? value.getValueRefId()
                    : value.getValueJson() == null ? null : JsonUtils.parseObject(value.getValueJson(), Object.class);
            result.put(field.getFieldKey(), resolved);
        }
        return result;
    }

    private WorkPlanRespVO convertPlan(WorkPlanDO plan, Long userId, boolean details) {
        List<WorkTaskDO> allTasks = taskMapper.selectListByPlanId(plan.getId());
        boolean summaryReady = PLAN_ACTIVE.equals(plan.getStatus()) && isSummaryReady(plan.getId());
        WorkPlanRespVO result = new WorkPlanRespVO().setId(plan.getId()).setTitle(plan.getTitle()).setPeriodType(plan.getPeriodType())
                .setPlanTypeId(plan.getPlanTypeId()).setTemplateId(plan.getTemplateId()).setTemplateVersionId(plan.getTemplateVersionId())
                .setOwnerUserId(plan.getOwnerUserId()).setOwnerDeptId(plan.getOwnerDeptId()).setObjective(plan.getObjective())
                .setKeyRequirements(plan.getKeyRequirements()).setStartDate(plan.getStartDate()).setEndDate(plan.getEndDate())
                .setStatus(plan.getStatus()).setSummaryReady(summaryReady).setCreatorUserId(plan.getCreatorUserId())
                .setPublishedAt(plan.getPublishedAt()).setCompletedAt(plan.getCompletedAt()).setCancelledAt(plan.getCancelledAt())
                .setCancelReason(plan.getCancelReason()).setVersion(plan.getVersion()).setPlanFields(readFields(plan.getId(), SUBJECT_PLAN, plan.getId()))
                .setFieldDefinitions(definitionMapper.selectListByPlanId(plan.getId()).stream().map(this::convertDefinition).toList())
                .setAvailableActions(permissionProvider.availablePlanActions(plan, summaryReady, userId));
        if (details) {
            Set<Long> visibleIds = visibleTaskIds(plan, allTasks, userId);
            result.setTasks(allTasks.stream().filter(task -> visibleIds.contains(task.getId())).map(task -> convertTask(task, allTasks, userId)).toList());
            result.setSummary(convertSummary(plan));
            result.setChanges(changeMapper.selectListByPlan(plan.getId(), visibleIds.stream().toList()).stream()
                    .map(this::convertChange).toList());
        }
        return result;
    }

    private Set<Long> visibleTaskIds(WorkPlanDO plan, List<WorkTaskDO> tasks, Long userId) {
        if (permissionProvider.hasFullPlanAccess(plan, userId)) return tasks.stream().map(WorkTaskDO::getId).collect(Collectors.toSet());
        Map<Long, WorkTaskDO> byId = tasks.stream().collect(Collectors.toMap(WorkTaskDO::getId, Function.identity()));
        Map<Long, List<WorkTaskDO>> children = tasks.stream().filter(task -> task.getParentTaskId() != null)
                .collect(Collectors.groupingBy(WorkTaskDO::getParentTaskId));
        Set<Long> visible = new LinkedHashSet<>();
        for (WorkTaskDO task : tasks) {
            if (Objects.equals(task.getAssigneeUserId(), userId) || Objects.equals(task.getAssignerUserId(), userId)) addDescendants(task.getId(), children, visible);
            if (Objects.equals(task.getConfirmerUserId(), userId)) visible.add(task.getId());
        }
        for (Long id : new ArrayList<>(visible)) {
            WorkTaskDO cursor = byId.get(id);
            while (cursor != null && cursor.getParentTaskId() != null) { visible.add(cursor.getParentTaskId()); cursor = byId.get(cursor.getParentTaskId()); }
        }
        return visible;
    }

    private void addDescendants(Long id, Map<Long, List<WorkTaskDO>> children, Set<Long> visible) {
        if (!visible.add(id)) return;
        for (WorkTaskDO child : children.getOrDefault(id, List.of())) addDescendants(child.getId(), children, visible);
    }

    private WorkTaskRespVO convertTask(WorkTaskDO task, List<WorkTaskDO> all, Long userId) {
        List<WorkTaskDO> children = all.stream().filter(row -> Objects.equals(row.getParentTaskId(), task.getId())).toList();
        boolean blocked = children.stream().anyMatch(row -> Set.of(TASK_DRAFT, TASK_PENDING, TASK_AWAITING_CONFIRMATION).contains(row.getStatus()));
        return new WorkTaskRespVO().setId(task.getId()).setPlanId(task.getPlanId()).setParentTaskId(task.getParentTaskId())
                .setTitle(task.getTitle()).setDescription(task.getDescription()).setDeliverableRequirement(task.getDeliverableRequirement())
                .setAssigneeUserId(task.getAssigneeUserId()).setAssigneeDeptId(task.getAssigneeDeptId()).setAssignerUserId(task.getAssignerUserId())
                .setDueAt(task.getDueAt()).setRemindAt(task.getRemindAt()).setConfirmationRequired(task.getConfirmationRequired())
                .setConfirmerUserId(task.getConfirmerUserId()).setStatus(task.getStatus()).setReportedAt(task.getReportedAt())
                .setCompletedAt(task.getCompletedAt()).setCancelledAt(task.getCancelledAt()).setCancelReason(task.getCancelReason()).setVersion(task.getVersion())
                .setBlockedByChildren(blocked).setTotalChildCount(children.size())
                .setCompletedChildCount((int) children.stream().filter(row -> TASK_COMPLETED.equals(row.getStatus())).count())
                .setTaskFields(readFields(task.getPlanId(), SUBJECT_TASK, task.getId()))
                .setReports(reportMapper.selectListByTaskId(task.getId()).stream().map(report -> convertReport(task.getPlanId(), report)).toList())
                .setAvailableActions(permissionProvider.availableTaskActions(task, blocked, userId));
    }

    private WorkReportRespVO convertReport(Long planId, WorkReportDO report) {
        return new WorkReportRespVO().setId(report.getId()).setRevisionNo(report.getRevisionNo())
                .setCompletionSummary(report.getCompletionSummary()).setSubmitterUserId(report.getSubmitterUserId())
                .setSubmittedAt(report.getSubmittedAt()).setConfirmationDecision(report.getConfirmationDecision())
                .setConfirmationComment(report.getConfirmationComment()).setConfirmedByUserId(report.getConfirmedByUserId())
                .setConfirmedAt(report.getConfirmedAt()).setInfraFileIds(attachmentMapper.selectListBySubject(SUBJECT_REPORT, report.getId())
                        .stream().map(WorkAttachmentDO::getInfraFileId).toList())
                .setReportFields(readFields(planId, SUBJECT_REPORT, report.getId()));
    }

    private WorkPlanSummaryRespVO convertSummary(WorkPlanDO plan) {
        WorkPlanSummaryDO summary = summaryMapper.selectByPlanId(plan.getId()); if (summary == null) return null;
        return new WorkPlanSummaryRespVO().setId(summary.getId()).setSummary(summary.getSummary())
                .setSubmitterUserId(summary.getSubmitterUserId()).setSubmittedAt(summary.getSubmittedAt())
                .setInfraFileIds(attachmentMapper.selectListBySubject(SUBJECT_SUMMARY, summary.getId()).stream().map(WorkAttachmentDO::getInfraFileId).toList())
                .setSummaryFields(readFields(plan.getId(), SUBJECT_SUMMARY, summary.getId()));
    }

    private WorkPlanTemplateFieldSaveReqVO convertDefinition(WorkPlanFieldDefinitionDO field) {
        return new WorkPlanTemplateFieldSaveReqVO().setId(field.getId()).setFieldKey(field.getFieldKey()).setLabel(field.getLabel())
                .setSection(field.getSection()).setFieldType(field.getFieldType()).setRequired(field.getRequired())
                .setUnit(field.getUnit()).setPlaceholder(field.getPlaceholder()).setFilterable(field.getFilterable())
                .setExportable(field.getExportable()).setOptionsJson(field.getOptionsJson()).setDefaultValueJson(field.getDefaultValueJson()).setSort(field.getSort());
    }

    private WorkPlanChangeRespVO convertChange(WorkChangeDO change) {
        return new WorkPlanChangeRespVO().setId(change.getId()).setSubjectType(change.getSubjectType()).setSubjectId(change.getSubjectId())
                .setChangeType(change.getChangeType()).setBeforeSnapshot(change.getBeforeSnapshot()).setAfterSnapshot(change.getAfterSnapshot())
                .setReason(change.getReason()).setOperatorUserId(change.getOperatorUserId()).setChangedAt(change.getChangedAt());
    }

    private List<List<String>> exportHeaders(List<WorkPlanTemplateFieldDO> fields) {
        List<String> fixed = new ArrayList<>(List.of("计划名称", "周期", "计划负责人", "负责部门", "计划状态", "任务路径", "任务责任人", "任务截止时间", "任务状态"));
        fields.stream().filter(field -> SECTION_PLAN.equals(field.getSection())).forEach(field -> fixed.add("计划目标/" + field.getLabel()));
        fields.stream().filter(field -> SECTION_TASK.equals(field.getSection())).forEach(field -> fixed.add("任务要求/" + field.getLabel()));
        fields.stream().filter(field -> SECTION_REPORT.equals(field.getSection())).forEach(field -> fixed.add("完成汇报/" + field.getLabel()));
        fields.stream().filter(field -> SECTION_SUMMARY.equals(field.getSection())).forEach(field -> fixed.add("计划总结/" + field.getLabel()));
        fixed.addAll(List.of("完成说明", "确认状态", "计划总结说明", "补充计划字段", "补充任务字段", "补充汇报字段", "补充总结字段"));
        return fixed.stream().map(List::of).toList();
    }

    private List<Object> exportRow(WorkPlanRespVO plan, WorkTaskRespVO task, List<WorkPlanTemplateFieldDO> columns,
                                   Map<Long, String> userNames) {
        WorkReportRespVO latest = task.getReports().isEmpty() ? null : task.getReports().get(task.getReports().size() - 1);
        List<Object> row = new ArrayList<>(List.of(plan.getTitle(), plan.getPeriodType(), userName(plan.getOwnerUserId(), userNames),
                Objects.toString(plan.getOwnerDeptId(), ""), plan.getStatus(), taskPath(task, plan.getTasks()),
                userName(task.getAssigneeUserId(), userNames), Objects.toString(task.getDueAt(), ""), task.getStatus()));
        appendSectionValues(row, columns, SECTION_PLAN, plan.getPlanFields());
        appendSectionValues(row, columns, SECTION_TASK, task.getTaskFields());
        appendSectionValues(row, columns, SECTION_REPORT, latest == null ? Map.of() : latest.getReportFields());
        appendSectionValues(row, columns, SECTION_SUMMARY, plan.getSummary() == null ? Map.of() : plan.getSummary().getSummaryFields());
        row.add(latest == null ? "" : latest.getCompletionSummary()); row.add(latest == null ? "" : latest.getConfirmationDecision());
        row.add(plan.getSummary() == null ? "" : plan.getSummary().getSummary());
        row.add(supplementalText(plan, SECTION_PLAN, plan.getPlanFields())); row.add(supplementalText(plan, SECTION_TASK, task.getTaskFields()));
        row.add(supplementalText(plan, SECTION_REPORT, latest == null ? Map.of() : latest.getReportFields()));
        row.add(supplementalText(plan, SECTION_SUMMARY, plan.getSummary() == null ? Map.of() : plan.getSummary().getSummaryFields()));
        return row;
    }

    private void appendSectionValues(List<Object> row, List<WorkPlanTemplateFieldDO> columns, String section, Map<String, Object> values) {
        columns.stream().filter(field -> section.equals(field.getSection())).forEach(field -> row.add(formatValue(values.get(field.getFieldKey()))));
    }

    private String supplementalText(WorkPlanRespVO plan, String section, Map<String, Object> values) {
        return plan.getFieldDefinitions().stream().filter(field -> section.equals(field.getSection()) && field.getFieldKey().startsWith("p_"))
                .map(field -> field.getLabel() + "：" + formatValue(values.get(field.getFieldKey()))).collect(Collectors.joining("；"));
    }

    private String taskPath(WorkTaskRespVO task, List<WorkTaskRespVO> tasks) {
        Map<Long, WorkTaskRespVO> byId = tasks.stream().collect(Collectors.toMap(WorkTaskRespVO::getId, Function.identity()));
        LinkedList<String> path = new LinkedList<>(); WorkTaskRespVO cursor = task;
        while (cursor != null) { path.addFirst(cursor.getTitle()); cursor = cursor.getParentTaskId() == null ? null : byId.get(cursor.getParentTaskId()); }
        return String.join(" / ", path);
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return String.valueOf(value);
    }

    private String userName(Long userId, Map<Long, String> cache) {
        if (userId == null) return "";
        return cache.computeIfAbsent(userId, id -> Optional.ofNullable(adminUserApi.getUser(id)).map(AdminUserRespDTO::getNickname).orElse("用户 #" + id));
    }

    private Visibility visibility(Long userId) {
        DeptDataPermissionRespDTO scope = permissionApi.getDeptDataPermission(userId);
        boolean all = scope != null && Boolean.TRUE.equals(scope.getAll());
        Set<Long> deptIds = scope == null || scope.getDeptIds() == null ? Set.of() : scope.getDeptIds();
        return new Visibility(all, taskMapper.selectVisiblePlanIds(userId, deptIds, all));
    }

    private WorkPlanDO updatePlanIdentity(WorkPlanDO existing, WorkPlanDO update) {
        update.setId(existing.getId()); update.setTemplateId(existing.getTemplateId()); update.setTemplateVersionId(existing.getTemplateVersionId());
        update.setPlanTypeId(existing.getPlanTypeId()); return update;
    }

    private void copyPlan(WorkPlanSaveReqVO request, WorkPlanDO plan) {
        plan.setTitle(request.getTitle()); plan.setPeriodType(request.getPeriodType()); plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate()); plan.setTemplateVersionId(request.getTemplateVersionId()); plan.setOwnerUserId(request.getOwnerUserId());
        plan.setObjective(request.getObjective()); plan.setKeyRequirements(request.getKeyRequirements());
    }

    private Map<String, Object> planSnapshot(WorkPlanDO plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>(); snapshot.put("plan", plan); snapshot.put("fields", readFields(plan.getId(), SUBJECT_PLAN, plan.getId())); return snapshot;
    }

    private WorkTaskDO copyTask(WorkTaskDO source) {
        WorkTaskDO copy = new WorkTaskDO(); copy.setId(source.getId()); copy.setPlanId(source.getPlanId()); copy.setParentTaskId(source.getParentTaskId());
        copy.setTitle(source.getTitle()); copy.setDescription(source.getDescription()); copy.setDeliverableRequirement(source.getDeliverableRequirement());
        copy.setAssigneeUserId(source.getAssigneeUserId()); copy.setAssigneeDeptId(source.getAssigneeDeptId()); copy.setAssignerUserId(source.getAssignerUserId());
        copy.setDueAt(source.getDueAt()); copy.setRemindAt(source.getRemindAt()); copy.setConfirmationRequired(source.getConfirmationRequired());
        copy.setConfirmerUserId(source.getConfirmerUserId()); copy.setStatus(source.getStatus()); copy.setVersion(source.getVersion()); return copy;
    }

    private void addChange(String subjectType, Long subjectId, String type, Object before, Object after,
                           String reason, Long userId) {
        changeMapper.insert(new WorkChangeDO().setSubjectType(subjectType).setSubjectId(subjectId).setChangeType(type)
                .setBeforeSnapshot(before == null ? null : JsonUtils.toJsonString(before))
                .setAfterSnapshot(after == null ? null : JsonUtils.toJsonString(after)).setReason(reason)
                .setOperatorUserId(userId).setChangedAt(LocalDateTime.now()));
    }

    private AdminUserRespDTO requireEnabledUser(Long userId, boolean confirmer) {
        AdminUserRespDTO user = userId == null ? null : adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.isEnable(user.getStatus())
                || confirmer && !permissionApi.hasAnyPermissions(userId, PERMISSION_REVIEW)) {
            throw exception(confirmer ? WORK_PLAN_CONFIRMER_INVALID : WORK_PLAN_ASSIGNEE_INVALID);
        }
        return user;
    }

    private WorkPlanDO requirePlan(Long id) { WorkPlanDO plan = planMapper.selectById(id); if (plan == null) throw exception(WORK_PLAN_NOT_EXISTS); return plan; }
    private WorkPlanDO requirePlanForUpdate(Long id) { WorkPlanDO plan = planMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId()); if (plan == null) throw exception(WORK_PLAN_NOT_EXISTS); return plan; }
    private WorkTaskDO requireTask(Long id) { WorkTaskDO task = taskMapper.selectById(id); if (task == null) throw exception(WORK_TASK_NOT_EXISTS); return task; }
    private WorkPlanTemplateDO requireTemplate(Long id) { WorkPlanTemplateDO template = templateMapper.selectById(id); if (template == null) throw exception(WORK_PLAN_FIELD_INVALID); return template; }
    private void requireVersion(Integer expected, Integer actual) { if (!Objects.equals(expected, actual)) conflict(); }
    private void requireState(boolean allowed, boolean plan) { if (!allowed) throw exception(plan ? WORK_PLAN_STATE_INVALID : WORK_TASK_STATE_INVALID); }
    private void requireReason(String reason) { if (reason == null || reason.isBlank()) throw exception(WORK_PLAN_CHANGE_REASON_REQUIRED); }
    private void conflict() { throw exception(WORK_PLAN_VERSION_CONFLICT); }
    private boolean isEmpty(Object value) { return value == null || value instanceof String text && text.isBlank() || value instanceof Collection<?> values && values.isEmpty(); }

    private record TypedValue(WorkPlanFieldDefinitionDO field, Object value) {}
    private record Visibility(boolean all, List<Long> planIds) {}
}
