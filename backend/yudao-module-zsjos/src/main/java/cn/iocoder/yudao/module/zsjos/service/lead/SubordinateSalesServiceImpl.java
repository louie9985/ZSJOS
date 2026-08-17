package cn.iocoder.yudao.module.zsjos.service.lead;

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
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.SalesDispatchStatusRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
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
public class SubordinateSalesServiceImpl implements SubordinateSalesService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @Resource private AdminUserApi adminUserApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private PostApi postApi;
    @Resource private LeadObjectPermissionService permissionService;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private SalesDispatchStatusService dispatchStatusService;
    @Resource private LeadManagementService leadManagementService;
    @Resource private LeadMapper leadMapper;
    @Resource private BusinessTaskMapper taskMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private SubordinateSalesCommandService commandService;

    @Override
    public PageResult<SubordinateSalesRespVO> getPage(SubordinateSalesPageReqVO reqVO, Long managerUserId) {
        List<AdminUserRespDTO> users = salesSubordinates(managerUserId);
        String keyword = trimToNull(reqVO.getKeyword());
        List<SubordinateSalesRespVO> rows = buildRows(users).stream()
                .filter(row -> keyword == null || contains(row.getName(), keyword)
                        || contains(row.getUsername(), keyword) || contains(row.getMobile(), keyword))
                .filter(row -> reqVO.getAccountStatus() == null || Objects.equals(row.getAccountStatus(), reqVO.getAccountStatus()))
                .filter(row -> reqVO.getPresence() == null || Objects.equals(row.getPresence(), reqVO.getPresence()))
                .filter(row -> reqVO.getAccepting() == null || Objects.equals(row.getAccepting(), reqVO.getAccepting()))
                .sorted(Comparator.comparing(SubordinateSalesRespVO::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(SubordinateSalesRespVO::getUserId))
                .toList();
        int from = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), rows.size());
        int to = Math.min(from + reqVO.getPageSize(), rows.size());
        return new PageResult<>(rows.subList(from, to), (long) rows.size());
    }

    @Override
    public SubordinateSalesRespVO getOverview(Long salesUserId, Long managerUserId) {
        AdminUserRespDTO user = requireSalesSubordinate(salesUserId, managerUserId);
        return buildRows(List.of(user)).get(0);
    }

    @Override
    public PageResult<LeadManagementRespVO> getLeadPage(Long salesUserId, LeadManagementPageReqVO reqVO,
                                                         Long managerUserId) {
        requireSalesSubordinate(salesUserId, managerUserId);
        return leadManagementService.getManagedOwnerLeadPage(reqVO, managerUserId, salesUserId);
    }

    @Override
    public PageResult<SubordinateTaskRespVO> getTaskPage(Long salesUserId, SubordinateTaskPageReqVO reqVO,
                                                          Long managerUserId) {
        requireSalesSubordinate(salesUserId, managerUserId);
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
        List<SubordinateTaskRespVO> rows = tasks.stream().map(task -> {
            SubordinateTaskRespVO row = new SubordinateTaskRespVO();
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
    public List<LeadAssignmentUserRespVO> getTransferCandidates(Long managerUserId) {
        Set<Long> managed = permissionService.getManagedUserIds(managerUserId);
        return assignmentService.getEligibleSalesUsers().stream().filter(user -> managed.contains(user.getId())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccountStatus(Long salesUserId, SubordinateAccountStatusReqVO reqVO, Long managerUserId) {
        AdminUserRespDTO user = requireSalesSubordinate(salesUserId, managerUserId);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(reqVO.getStatus())
                && !CommonStatusEnum.DISABLE.getStatus().equals(reqVO.getStatus())) throw exception(SUBORDINATE_SALES_STATUS_INVALID);
        String reason = requireReason(reqVO.getReason());
        adminUserApi.updateUserStatus(salesUserId, reqVO.getStatus(), reason);
        commandService.addAudit("account_status", managerUserId, salesUserId, null,
                String.valueOf(user.getStatus()), String.valueOf(reqVO.getStatus()), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDispatchMode(Long salesUserId, SubordinateDispatchModeReqVO reqVO, Long managerUserId) {
        requireSalesSubordinate(salesUserId, managerUserId);
        String reason = requireReason(reqVO.getReason());
        SalesDispatchStatusRespVO before = dispatchStatusService.getStatus(salesUserId);
        SalesDispatchStatusRespVO after = dispatchStatusService.updateModeByManager(salesUserId, reqVO.getAccepting());
        commandService.addAudit("dispatch_mode", managerUserId, salesUserId, null,
                before.getMode(), after.getMode(), reason);
    }

    @Override
    public SubordinateBatchResultVO batchTransfer(SubordinateBatchTransferReqVO reqVO, Long managerUserId) {
        String reason = requireReason(reqVO.getReason());
        requireEligibleTarget(reqVO.getTargetUserId(), managerUserId);
        return executeBatch(reqVO.getLeadIds(), leadId ->
                commandService.transferOne(leadId, reqVO.getTargetUserId(), managerUserId, reason));
    }

    @Override
    public SubordinateBatchResultVO batchReleasePublicSea(SubordinateBatchPublicSeaReqVO reqVO, Long managerUserId) {
        String reason = requireReason(reqVO.getReason());
        if (reqVO.getCollaboratorUserId() != null) requireEligibleTarget(reqVO.getCollaboratorUserId(), managerUserId);
        return executeBatch(reqVO.getLeadIds(), leadId ->
                commandService.releasePublicSeaOne(leadId, reqVO.getCollaboratorUserId(), managerUserId, reason));
    }

    private List<SubordinateSalesRespVO> buildRows(List<AdminUserRespDTO> users) {
        if (users.isEmpty()) return List.of();
        List<Long> ids = users.stream().map(AdminUserRespDTO::getId).toList();
        Map<Long, List<LeadDO>> leads = leadMapper.selectByOwnerUserIds(ids).stream()
                .collect(Collectors.groupingBy(LeadDO::getOwnerUserId));
        Map<Long, List<BusinessTaskDO>> tasks = taskMapper.selectByAssigneeIds(ids).stream()
                .collect(Collectors.groupingBy(BusinessTaskDO::getAssigneeId));
        Map<Long, List<SalesOrderDO>> orders = orderMapper.selectEffectiveBySubmitterIds(ids).stream()
                .collect(Collectors.groupingBy(SalesOrderDO::getSubmitterUserId));
        Set<Long> eligible = assignmentService.getEligibleSalesUsers().stream()
                .map(LeadAssignmentUserRespVO::getId).collect(Collectors.toSet());
        List<DictDataRespDTO> categories = dictDataApi.getDictDataList(DICT_CATEGORY).stream()
                .filter(data -> CommonStatusEnum.ENABLE.getStatus().equals(data.getStatus())).toList();
        LocalDateTime start = LocalDate.now(BEIJING).atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        LocalDateTime now = LocalDateTime.now(BEIJING);
        return users.stream().map(user -> buildRow(user, leads.getOrDefault(user.getId(), List.of()),
                tasks.getOrDefault(user.getId(), List.of()), orders.getOrDefault(user.getId(), List.of()),
                categories, eligible.contains(user.getId()), start, end, now)).toList();
    }

    private SubordinateSalesRespVO buildRow(AdminUserRespDTO user, List<LeadDO> leads,
                                              List<BusinessTaskDO> tasks, List<SalesOrderDO> orders,
                                              List<DictDataRespDTO> categories, boolean eligible,
                                              LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        SalesDispatchStatusRespVO dispatch = dispatchStatusService.getStatus(user.getId());
        SubordinateSalesRespVO row = new SubordinateSalesRespVO();
        row.setUserId(user.getId()); row.setName(user.getNickname()); row.setAvatar(user.getAvatar());
        row.setUsername(user.getUsername());
        row.setMobile(user.getMobile()); row.setAccountStatus(user.getStatus()); row.setPresence(dispatch.getPresence());
        row.setAccepting("accepting".equals(dispatch.getMode())); row.setEligible(eligible);
        row.setCanReceiveNewLeads(CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()) && eligible
                && "online".equals(dispatch.getPresence()) && Boolean.TRUE.equals(row.getAccepting()));
        row.setNewcomerPoolStatus("not_available");
        long todayPending = tasks.stream().filter(task -> isFollowUpTask(task.getTaskType()))
                .filter(task -> TASK_STATUS_PENDING.equals(task.getStatus())
                && task.getDueAt() != null && !task.getDueAt().isBefore(start) && task.getDueAt().isBefore(end)).count();
        row.setTodayPendingCount(todayPending); row.setTodayFollowUpStatus(todayPending > 0 ? "incomplete" : "completed");
        row.setFirstFollowTimeoutCount(tasks.stream().filter(task -> TASK_TYPE_FIRST_FOLLOW_UP.equals(task.getTaskType())
                && !"cancelled".equals(task.getStatus())
                && task.getDueAt() != null && task.getDueAt().isBefore(now)).count());
        row.setSuspendedLeadCount(leads.stream().filter(lead -> STATUS_SUSPENDED.equals(lead.getStatus())).count());
        Map<String, Long> categoryCounts = leads.stream().collect(Collectors.groupingBy(
                lead -> lead.getLeadCategory() == null || lead.getLeadCategory().isBlank() ? "__unconfigured__" : lead.getLeadCategory(),
                Collectors.counting()));
        List<SubordinateSalesRespVO.CategoryCountVO> countRows = new ArrayList<>();
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

    private List<AdminUserRespDTO> salesSubordinates(Long managerUserId) {
        Set<Long> managed = permissionService.getManagedUserIds(managerUserId);
        if (managed.isEmpty()) return List.of();
        PostRespDTO salesPost = postApi.getPostByCode(SALES_POST_CODE);
        if (salesPost == null) return List.of();
        // Disabled sales must remain visible; use persisted post membership from System, not eligibility status.
        return adminUserApi.getUserList(managed).stream()
                .filter(user -> user.getPostIds() != null && user.getPostIds().contains(salesPost.getId())).toList();
    }

    private AdminUserRespDTO requireSalesSubordinate(Long userId, Long managerUserId) {
        return salesSubordinates(managerUserId).stream().filter(user -> Objects.equals(user.getId(), userId))
                .findFirst().orElseThrow(() -> exception(SUBORDINATE_SALES_NOT_MANAGED));
    }

    private void requireEligibleTarget(Long userId, Long managerUserId) {
        boolean valid = getTransferCandidates(managerUserId).stream().anyMatch(user -> Objects.equals(user.getId(), userId));
        if (!valid) throw exception(SUBORDINATE_SALES_TARGET_INVALID);
    }

    private SubordinateBatchResultVO executeBatch(List<Long> ids, BatchAction action) {
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        Map<Long, LeadDO> leads = leadMapper.selectBatchIds(uniqueIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        List<SubordinateBatchResultVO.ItemVO> items = new ArrayList<>();
        for (Long id : uniqueIds) {
            LeadDO lead = leads.get(id);
            String leadNo = lead == null ? null : lead.getLeadNo();
            try {
                action.execute(id);
                items.add(new SubordinateBatchResultVO.ItemVO(id, leadNo, true, "SUCCESS", "操作成功"));
            } catch (ServiceException ex) {
                items.add(new SubordinateBatchResultVO.ItemVO(id, leadNo, false, String.valueOf(ex.getCode()), ex.getMessage()));
            } catch (RuntimeException ex) {
                items.add(new SubordinateBatchResultVO.ItemVO(id, leadNo, false, "INTERNAL_ERROR", "操作失败，请刷新后重试"));
            }
        }
        SubordinateBatchResultVO result = new SubordinateBatchResultVO();
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

    private static SubordinateSalesRespVO.CategoryCountVO categoryCount(String value, String label, long count, boolean configured) {
        SubordinateSalesRespVO.CategoryCountVO row = new SubordinateSalesRespVO.CategoryCountVO();
        row.setValue(value); row.setLabel(label); row.setCount(count); row.setConfigured(configured); return row;
    }
    private static String requireReason(String reason) {
        String value = trimToNull(reason); if (value == null || value.length() > 500) throw exception(SUBORDINATE_SALES_REASON_REQUIRED); return value;
    }
    private static String trimToNull(String value) { if (value == null) return null; String trimmed = value.trim(); return trimmed.isEmpty() ? null : trimmed; }
    private static boolean contains(String value, String keyword) { return value != null && value.toLowerCase().contains(keyword.toLowerCase()); }
    @FunctionalInterface private interface BatchAction { void execute(Long leadId); }
}
