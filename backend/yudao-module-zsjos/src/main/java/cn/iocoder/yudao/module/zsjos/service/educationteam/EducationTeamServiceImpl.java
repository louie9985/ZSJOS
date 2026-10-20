package cn.iocoder.yudao.module.zsjos.service.educationteam;

import cn.iocoder.yudao.module.zsjos.service.lead.*;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class EducationTeamServiceImpl implements EducationTeamService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @Resource private AdminUserApi adminUserApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private PostApi postApi;
    @Resource private cn.iocoder.yudao.module.system.api.dept.DeptApi deptApi;
    @Resource private LeadSubmissionIdentityService identityService;
    @Resource private LeadTeamTransferPolicy teamTransferPolicy;
    @Resource private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;

    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.EducationTeamDailyMapper dailyMapper;
    @Resource private EducationTeamScope scope;
    @Resource private EducationTeamLeadReader leadReader;
    @Resource private LeadMapper leadMapper;
    @Resource private BusinessTaskMapper taskMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private EducationTeamCommandService commandService;
    @Resource private AdvancedFilterService advancedFilterService;

    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper assignmentHistoryMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper followUpRecordMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityFollowUpRecordMapper opportunityFollowUpRecordMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper eventMapper;

    @Override
    public PageResult<EducationTeamRespVO> getPage(EducationTeamPageReqVO reqVO, Long managerUserId) {
        String initialKeyword=trimToNull(reqVO.getKeyword());
        List<AdminUserRespDTO> users = scope.readable(managerUserId).stream()
            .filter(user -> reqVO.getAccountStatus()==null||Objects.equals(reqVO.getAccountStatus(),user.getStatus()))
            .filter(user -> initialKeyword==null||contains(user.getNickname(),initialKeyword)||contains(user.getUsername(),initialKeyword)||contains(user.getMobile(),initialKeyword)).toList();
        String keyword = trimToNull(reqVO.getKeyword());
        List<EducationTeamRespVO> rows = buildRows(users).stream()
                .filter(row -> keyword == null || contains(row.getName(), keyword)
                        || contains(row.getUsername(), keyword) || contains(row.getMobile(), keyword))
                .filter(row -> reqVO.getAccountStatus() == null || Objects.equals(row.getAccountStatus(), reqVO.getAccountStatus()))
                .filter(row -> advancedFilterService.matches("education_team", reqVO.getAdvancedFilter(),
                        key -> subordinateFilterValue(row, key)))
                .sorted(Comparator.comparing(EducationTeamRespVO::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(EducationTeamRespVO::getUserId))
                .toList();
        int from = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), rows.size());
        int to = Math.min(from + reqVO.getPageSize(), rows.size());
        return new PageResult<>(rows.subList(from, to), (long) rows.size());
    }

    private Object subordinateFilterValue(EducationTeamRespVO row, String key) {
        return switch (key) {
            case "educationTeam.todayNewIntentionCount" -> row.getTodayNewIntentionCount();
            case "educationTeam.todayOldIntentionFollowUpRecordCount" -> row.getTodayOldIntentionFollowUpRecordCount();
            case "educationTeam.todayStudentContactCount" -> row.getTodayStudentContactCount();
            case "educationTeam.name" -> row.getName();
            case "educationTeam.username" -> row.getUsername();
            case "educationTeam.mobile" -> row.getMobile();
            case "educationTeam.accountStatus" -> row.getAccountStatus();
            case "educationTeam.todayPendingCount" -> row.getTodayPendingCount();
            case "educationTeam.todayFollowUpStatus" -> row.getTodayFollowUpStatus();
            case "educationTeam.firstFollowTimeoutCount" -> row.getFirstFollowTimeoutCount();
            case "educationTeam.suspendedLeadCount" -> row.getSuspendedLeadCount();
            case "educationTeam.validLeadCount" -> row.getValidLeadCount();
            case "educationTeam.convertedLeadCount" -> row.getConvertedLeadCount();
            case "educationTeam.effectiveOrderCount" -> row.getEffectiveOrderCount();
            case "educationTeam.effectiveOrderAmount" -> row.getEffectiveOrderAmount();
            case "educationTeam.todayAssignedCount" -> row.getTodayAssignedCount();
            case "educationTeam.todayMissedCount" -> row.getTodayMissedCount();
            case "educationTeam.todayReceivedCount" -> row.getTodayReceivedCount();
            case "educationTeam.todayQualifiedCount" -> row.getTodayQualifiedCount();
            case "educationTeam.todayFollowUpRecordCount" -> row.getTodayFollowUpRecordCount();
            case "educationTeam.todayOrderAmount" -> row.getTodayOrderAmount();
            case "educationTeam.pendingQualificationCount" -> row.getPendingQualificationCount();
            case "educationTeam.todayFollowUpTotalCount" -> row.getTodayFollowUpTotalCount();
            case "educationTeam.todayFollowUpRemainingCount" -> row.getTodayFollowUpRemainingCount();
            default -> null;
        };
    }

    @Override
    public EducationTeamRespVO getOverview(Long salesUserId, Long managerUserId) {
        AdminUserRespDTO user = requireReadableSales(salesUserId, managerUserId);
        return buildRows(List.of(user)).get(0);
    }

    @Override
    public PageResult<LeadManagementRespVO> getLeadPage(Long salesUserId, LeadManagementPageReqVO reqVO,
                                                         Long managerUserId) {
        requireReadableSales(salesUserId, managerUserId);
        return leadReader.page(salesUserId, reqVO, managerUserId);
    }

    @Override
    public PageResult<EducationTeamTaskRespVO> getTaskPage(Long salesUserId, EducationTeamTaskPageReqVO reqVO,
                                                          Long managerUserId) {
        requireReadableSales(salesUserId, managerUserId);
        LocalDate today = LocalDate.now(BEIJING);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<BusinessTaskDO> tasks = taskMapper.selectMyPending(salesUserId).stream()
                .filter(task -> isFollowUpTask(task.getTaskType()))
                .filter(task -> matchesBucket(task.getDueAt(), reqVO.getBucket(), start, end))
                .toList();
        Set<Long> leadIds = tasks.stream().filter(task -> BIZ_TYPE_LEAD.equals(task.getBizType()))
                .map(BusinessTaskDO::getBizId).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        List<EducationTeamTaskRespVO> rows = tasks.stream().filter(task -> {var lead=leads.get(task.getBizId());return lead!=null&&OWNER_EDUCATION.equals(lead.getOwnerIdentity())&&Objects.equals(salesUserId,lead.getOwnerUserId());}).map(task -> {
            EducationTeamTaskRespVO row = new EducationTeamTaskRespVO();
            row.setId(task.getId()); row.setTaskType(task.getTaskType()); row.setLeadId(task.getBizId());
            LeadDO lead = leads.get(task.getBizId());
            row.setLeadNo(lead == null ? null : lead.getLeadNo());
            row.setLeadName(lead == null ? null : lead.getSubmittedName());
            row.setDueAt(task.getDueAt()); row.setOverdue(task.getDueAt() != null && task.getDueAt().isBefore(LocalDateTime.now(BEIJING)));
            return row;
        }).toList();
        int from = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), rows.size());
        int to = Math.min(from + reqVO.getPageSize(), rows.size());
        return new PageResult<>(rows.subList(from, to), (long) rows.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccountStatus(Long salesUserId, EducationTeamAccountStatusReqVO reqVO, Long managerUserId) {
        AdminUserRespDTO user = scope.requireManaged(salesUserId, managerUserId);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(reqVO.getStatus())
                && !CommonStatusEnum.DISABLE.getStatus().equals(reqVO.getStatus())) throw exception(EducationTeamErrors.STATUS_INVALID);
        String reason = requireReason(reqVO.getReason());
        adminUserApi.updateUserStatus(salesUserId, reqVO.getStatus(), reason);
        commandService.addAudit("account_status", managerUserId, salesUserId, null,
                String.valueOf(user.getStatus()), String.valueOf(reqVO.getStatus()), reason);
    }

    @Override
    public void transferLead(Long leadId, EducationTeamLeadActionReqVO reqVO, Long managerUserId) {
        String reason = requireReason(reqVO.getReason());
        commandService.transferOne(leadId, reqVO.getTargetUserId(), managerUserId, reason, reqVO.getIdempotencyKey(), reqVO.getVersion());
    }

    @Override
    public void restoreLead(Long leadId, EducationTeamLeadActionReqVO reqVO, Long managerUserId) {
        commandService.restoreOne(leadId, managerUserId, requireReason(reqVO.getReason()), reqVO.getIdempotencyKey(), reqVO.getVersion());
    }

    @Override
    public void recycleLead(Long leadId, EducationTeamLeadActionReqVO reqVO, Long managerUserId) {
        commandService.recycleOne(leadId, managerUserId, requireReason(reqVO.getReason()), reqVO.getIdempotencyKey());
    }

    @Override
    public EducationTeamBatchResultVO batchLeadAction(String action, EducationTeamBatchLeadActionReqVO reqVO,
                                                     Long managerUserId) {
        String reason = requireReason(reqVO.getReason());
        String fingerprint = EducationTeamCommandService.fingerprint(action, reqVO.getLeadIds(),
                reqVO.getTargetUserId(), reqVO.getCollaboratorUserId(), reason);
        EducationTeamBatchResultVO replay = commandService.replayBatch(
                "batch-" + action, managerUserId, reqVO.getIdempotencyKey(), fingerprint);
        if (replay != null) return replay;
        commandService.validateManagedLeads(reqVO.getLeadIds(), managerUserId);
        if ("transfer".equals(action)) {
            for (Long id : reqVO.getLeadIds()) requireTransferTarget(id, reqVO.getTargetUserId(), managerUserId);
        }
        replay = commandService.beginBatch("batch-" + action, managerUserId, reqVO.getIdempotencyKey(), fingerprint);
        if (replay != null) return replay;
        EducationTeamBatchResultVO result = executeBatch(reqVO.getLeadIds(), leadId -> {
            String itemKey = reqVO.getIdempotencyKey() + ":" + leadId;
            switch (action) {
                case "transfer" -> commandService.transferOne(
                        leadId, reqVO.getTargetUserId(), managerUserId, reason, itemKey);
                case "restore" -> commandService.restoreOne(leadId, managerUserId, reason, itemKey);
                case "recycle" -> commandService.recycleOne(leadId, managerUserId, reason, itemKey);
                default -> throw exception(SUBORDINATE_LEAD_STATE_INVALID);
            }
        });
        commandService.completeBatch(managerUserId, reqVO.getIdempotencyKey(), result);
        return result;
    }

    private List<EducationTeamRespVO> buildRows(List<AdminUserRespDTO> users) {
        if (users.isEmpty()) return List.of();
        List<Long> ids = users.stream().map(AdminUserRespDTO::getId).toList();
        Map<Long, List<LeadDO>> leads = leadMapper.selectByOwnerUserIds(ids).stream().filter(lead -> OWNER_EDUCATION.equals(lead.getOwnerIdentity()))
                .collect(Collectors.groupingBy(LeadDO::getOwnerUserId));
        var allTasks = taskMapper.selectByAssigneeIds(ids);
        Set<Long> taskLeadIds = allTasks.stream().filter(task -> BIZ_TYPE_LEAD.equals(task.getBizType())).map(BusinessTaskDO::getBizId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, LeadDO> taskLeads = taskLeadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(taskLeadIds).stream().collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        Map<Long, List<BusinessTaskDO>> tasks = allTasks.stream().filter(task -> {
            LeadDO lead = taskLeads.get(task.getBizId());
            return BIZ_TYPE_LEAD.equals(task.getBizType()) && lead != null && OWNER_EDUCATION.equals(lead.getOwnerIdentity()) && Objects.equals(task.getAssigneeId(), lead.getOwnerUserId());
        }).collect(Collectors.groupingBy(BusinessTaskDO::getAssigneeId));
        Map<Long, List<SalesOrderDO>> orders = orderMapper.selectEffectiveBySubmitterIds(ids).stream()
                .collect(Collectors.groupingBy(SalesOrderDO::getSubmitterUserId));
        Set<Long> eligible = identityService.getEligibleEducationOwners(users);
        List<DictDataRespDTO> categories = dictDataApi.getDictDataList(DICT_CATEGORY).stream()
                .filter(data -> CommonStatusEnum.ENABLE.getStatus().equals(data.getStatus())).toList();
        LocalDateTime start = LocalDate.now(BEIJING).atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        LocalDateTime now = LocalDateTime.now(BEIJING);
        Long tenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        var intentions = dailyMapper.selectNewIntentions(tenant, ids, start, end);
        var oldFollowUps = dailyMapper.selectOldIntentionFollowUps(tenant, ids, start, end);
        var studentContacts = dailyMapper.selectContactedStudents(tenant, ids, start, end);
        var assignments = assignmentHistoryMapper.selectTodayByUserIds(ids, start, end).stream().filter(item -> OWNER_EDUCATION.equals(item.getOwnerIdentitySnapshot())).toList();
        var followUps = followUpRecordMapper.selectTodayByUserIds(ids, start, end);
        var opportunityFollowUps = opportunityFollowUpRecordMapper.selectTodayByUserIds(ids, start, end);
        var qualifications = eventMapper.selectTodayByUserIds(ids, start, end);
        Set<Long> deptIds = users.stream().map(AdminUserRespDTO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        var departments = deptIds.isEmpty() ? Map.<Long,cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO>of() : deptApi.getDeptMap(deptIds);
        Set<Long> postIds = users.stream().filter(user -> user.getPostIds()!=null).flatMap(user -> user.getPostIds().stream()).collect(Collectors.toSet());
        var posts = postApi.getPostMap(postIds);

        Set<Long> educationEligible = identityService.getEligibleEducationOwners(users);
        return users.stream().map(user -> {
            var row = buildRow(user, leads.getOrDefault(user.getId(), List.of()),
                tasks.getOrDefault(user.getId(), List.of()), orders.getOrDefault(user.getId(), List.of()),
                categories, eligible.contains(user.getId()), start, end, now);
            row.setTodayNewIntentionCount(intentions.stream().filter(item -> Objects.equals(item.getOperatorUserId(), user.getId())).map(item -> item.getAggregateId()).distinct().count());
            row.setTodayOldIntentionFollowUpRecordCount(oldFollowUps.stream().filter(item -> Objects.equals(item.getOperatorUserId(), user.getId())).count());
            row.setTodayStudentContactCount(studentContacts.stream().filter(item -> Objects.equals(item.getOperatorUserId(), user.getId())).count());
            row.setDeptId(user.getDeptId());
            var department=user.getDeptId()==null?null:departments.get(user.getDeptId());row.setDepartmentName(department==null?null:department.getName());
            row.setPostNames(user.getPostIds()==null?List.of():user.getPostIds().stream().map(posts::get).filter(Objects::nonNull).map(PostRespDTO::getName).toList());

            row.setEducationOwnershipSupported(educationEligible.contains(user.getId()));
            row.setOrderCreationSupported(permissionApi.hasAnyPermissions(user.getId(), "zsjos:sales-order:create", "zsjos:sales-order:student-repurchase"));
            // Event-time attribution survives later ownership changes; never infer daily activity from current ownership.
            var history = assignments.stream().filter(item -> Objects.equals(item.getCandidateUserId(), row.getUserId())).toList();
            row.setTodayAssignedCount(history.stream().filter(item -> ACTION_DISPATCH.equals(item.getActionType()))
                    .map(item -> item.getLeadId()).distinct().count());
            row.setTodayMissedCount(history.stream().filter(item -> ACTION_TIMEOUT.equals(item.getActionType()))
                    .map(item -> item.getLeadId()).distinct().count());
            row.setTodayReceivedCount(history.stream().filter(item -> ACTION_ACCEPT.equals(item.getActionType())
                    || ACTION_CLAIM.equals(item.getActionType())).map(item -> item.getLeadId()).distinct().count());
            row.setTodayQualifiedCount(qualifications.stream().filter(item -> Objects.equals(item.getOperatorUserId(), row.getUserId()))
                    .map(item -> item.getAggregateId()).distinct().count());
            row.setTodayFollowUpRecordCount(followUps.stream().filter(item -> Objects.equals(item.getOperatorUserId(), row.getUserId())).count()
                    + opportunityFollowUps.stream().filter(item -> Objects.equals(item.getOperatorUserId(), row.getUserId())).count());
            return row;
        }).toList();
    }

    private EducationTeamRespVO buildRow(AdminUserRespDTO user, List<LeadDO> leads,
                                              List<BusinessTaskDO> tasks, List<SalesOrderDO> orders,
                                              List<DictDataRespDTO> categories, boolean eligible,
                                              LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        tasks = tasks.stream().filter(task -> !(Set.of(TASK_TYPE_FIRST_FOLLOW_UP, TASK_TYPE_QUALIFICATION).contains(task.getTaskType())
                && LeadAutomaticGeneration.isAutomatic(task.getPayload()))).toList();
        EducationTeamRespVO row = new EducationTeamRespVO();
        row.setUserId(user.getId()); row.setName(user.getNickname()); row.setAvatar(user.getAvatar());
        row.setUsername(user.getUsername()); row.setMobile(user.getMobile()); row.setAccountStatus(user.getStatus());
        // Count leads, not tasks: first-follow and reminder tasks can coexist for one lead.
        var todayTasks = tasks.stream().filter(task -> BIZ_TYPE_LEAD.equals(task.getBizType()) && isFollowUpTask(task.getTaskType()))
                .filter(task -> TASK_STATUS_PENDING.equals(task.getStatus()) || "completed".equals(task.getStatus()))
                .filter(task -> task.getDueAt() != null && !task.getDueAt().isBefore(start) && task.getDueAt().isBefore(end)).toList();
        long todayPending = todayTasks.stream().filter(task -> TASK_STATUS_PENDING.equals(task.getStatus()))
                .map(BusinessTaskDO::getBizId).filter(Objects::nonNull).distinct().count();
        row.setTodayFollowUpTotalCount(todayTasks.stream().map(BusinessTaskDO::getBizId).filter(Objects::nonNull).distinct().count());
        row.setTodayFollowUpRemainingCount(todayPending);
        // Preserve the existing task-count field for other consumers.
        row.setTodayPendingCount(tasks.stream().filter(task -> isFollowUpTask(task.getTaskType()))
                .filter(task -> TASK_STATUS_PENDING.equals(task.getStatus()) && task.getDueAt() != null
                        && !task.getDueAt().isBefore(start) && task.getDueAt().isBefore(end)).count()); row.setTodayFollowUpStatus(todayPending > 0 ? "incomplete" : "completed");
        row.setTodayOrderAmount(orders.stream().filter(order -> order.getEffectiveAt() != null
                && !order.getEffectiveAt().isBefore(start) && order.getEffectiveAt().isBefore(end))
                .map(SalesOrderDO::getTotalAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        row.setPendingQualificationCount(leads.stream().filter(lead -> QUALIFICATION_PENDING.equals(LeadStateProjection.qualification(lead))).count());
        row.setFirstFollowTimeoutCount(tasks.stream().filter(task -> TASK_TYPE_FIRST_FOLLOW_UP.equals(task.getTaskType())
                && !"cancelled".equals(task.getStatus())
                && task.getDueAt() != null && task.getDueAt().isBefore(now)).count());
        row.setSuspendedLeadCount(leads.stream().filter(lead -> STATUS_SUSPENDED.equals(lead.getStatus())).count());
        Map<String, Long> categoryCounts = leads.stream().collect(Collectors.groupingBy(
                lead -> lead.getLeadCategory() == null || lead.getLeadCategory().isBlank() ? "__unconfigured__" : lead.getLeadCategory(),
                Collectors.counting()));
        List<EducationTeamRespVO.CategoryCountVO> countRows = new ArrayList<>();
        Set<String> configured = categories.stream().map(DictDataRespDTO::getValue).collect(Collectors.toSet());
        categories.forEach(category -> countRows.add(categoryCount(category.getValue(), category.getLabel(),
                categoryCounts.getOrDefault(category.getValue(), 0L), true)));
        long missing = categoryCounts.entrySet().stream().filter(entry -> !configured.contains(entry.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        if (missing > 0) countRows.add(categoryCount("__unconfigured__", "未配置", missing, false));
        row.setCategoryCounts(countRows);
        row.setValidLeadCount(leads.stream().filter(lead -> STATUS_VALID.equals(lead.getStatus())
                || STATUS_WON.equals(lead.getStatus())).count());
        row.setEffectiveOrderCount((long) orders.size());
        row.setConvertedLeadCount(orders.stream().map(SalesOrderDO::getLeadId).filter(Objects::nonNull).distinct().count());
        row.setEffectiveOrderAmount(orders.stream().map(SalesOrderDO::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return row;
    }

    private AdminUserRespDTO requireReadableSales(Long userId, Long readerId) {
        return scope.readable(readerId).stream().filter(user -> Objects.equals(user.getId(), userId))
                .findFirst().orElseThrow(() -> exception(EducationTeamErrors.NOT_MANAGED));
    }

    private EducationTeamBatchResultVO executeBatch(List<Long> ids, BatchAction action) {
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        Map<Long, LeadDO> leads = leadMapper.selectBatchIds(uniqueIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        List<EducationTeamBatchResultVO.ItemVO> items = new ArrayList<>();
        for (Long id : uniqueIds) {
            LeadDO lead = leads.get(id);
            String leadNo = lead == null ? null : lead.getLeadNo();
            try {
                action.execute(id);
                items.add(new EducationTeamBatchResultVO.ItemVO(id, leadNo, true, "SUCCESS", "操作成功"));
            } catch (ServiceException ex) {
                items.add(new EducationTeamBatchResultVO.ItemVO(id, leadNo, false, String.valueOf(ex.getCode()), ex.getMessage()));
            } catch (RuntimeException ex) {
                items.add(new EducationTeamBatchResultVO.ItemVO(id, leadNo, false, "INTERNAL_ERROR", "操作失败，请刷新后重试"));
            }
        }
        EducationTeamBatchResultVO result = new EducationTeamBatchResultVO();
        result.setItems(items); result.setSuccessCount((int) items.stream().filter(item -> item.getSuccess()).count());
        result.setFailureCount(items.size() - result.getSuccessCount());
        return result;
    }

    private static boolean matchesBucket(LocalDateTime dueAt, String bucket, LocalDateTime start, LocalDateTime end) {
        if (bucket == null) return true;
        return switch (bucket) {
            case "overdue" -> dueAt != null && dueAt.isBefore(start);
            case "today" -> dueAt != null && !dueAt.isBefore(start) && dueAt.isBefore(end);
            case "future" -> dueAt != null && !dueAt.isBefore(end);
            case "unscheduled" -> dueAt == null;
            default -> false;
        };
    }
    private static boolean isFollowUpTask(String taskType) {
        return TASK_TYPE_FIRST_FOLLOW_UP.equals(taskType) || TASK_TYPE_FOLLOW_UP_REMINDER.equals(taskType);
    }

    private static EducationTeamRespVO.CategoryCountVO categoryCount(String value, String label, long count, boolean configured) {
        EducationTeamRespVO.CategoryCountVO row = new EducationTeamRespVO.CategoryCountVO();
        row.setValue(value); row.setLabel(label); row.setCount(count); row.setConfigured(configured); return row;
    }
    private static String requireReason(String reason) {
        String value = trimToNull(reason); if (value == null || value.length() > 500) throw exception(SUBORDINATE_SALES_REASON_REQUIRED); return value;
    }
    private static String trimToNull(String value) { if (value == null) return null; String trimmed = value.trim(); return trimmed.isEmpty() ? null : trimmed; }
    private static boolean contains(String value, String keyword) { return value != null && value.toLowerCase().contains(keyword.toLowerCase()); }
    @FunctionalInterface private interface BatchAction { void execute(Long leadId); }

    @Override public List<LeadAssignmentUserRespVO> getTransferCandidates(Long manager) {
        var users=scope.managed(manager);var eligible=teamTransferPolicy.eligibleTargets(users);
        return users.stream().filter(u->eligible.contains(u.getId())).map(u->{var row=new LeadAssignmentUserRespVO();row.setId(u.getId());row.setNickname(u.getNickname());return row;}).toList();
    }
    @Override public List<LeadAssignmentUserRespVO> getTransferCandidates(Long manager,Long leadId) {
        if(leadId==null)return getTransferCandidates(manager);
        commandService.validateManagedLeads(List.of(leadId),manager);
        var lead=leadMapper.selectById(leadId);teamTransferPolicy.requireSupportedSource(lead);
        Long owner=lead.getOwnerUserId()==null?lead.getRecycleSourceOwnerUserId():lead.getOwnerUserId();
        return getTransferCandidates(manager).stream().filter(u->!Objects.equals(owner,u.getId())).toList();
    }
    @Override public List<LeadAssignmentUserRespVO> getBatchTransferCandidates(Long manager,List<Long> ids) {
        if(ids==null||ids.isEmpty()||ids.size()>100)throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        commandService.validateManagedLeads(ids,manager);
        var allowed=getTransferCandidates(manager).stream().map(LeadAssignmentUserRespVO::getId).collect(Collectors.toSet());
        for(Long id:ids)allowed.retainAll(getTransferCandidates(manager,id).stream().map(LeadAssignmentUserRespVO::getId).collect(Collectors.toSet()));
        return getTransferCandidates(manager).stream().filter(u->allowed.contains(u.getId())).toList();
    }
    private void requireTransferTarget(Long id,Long target,Long manager) {
        if(getTransferCandidates(manager,id).stream().noneMatch(u->Objects.equals(target,u.getId())))throw exception(EducationTeamErrors.TARGET_INVALID);
    }
}
