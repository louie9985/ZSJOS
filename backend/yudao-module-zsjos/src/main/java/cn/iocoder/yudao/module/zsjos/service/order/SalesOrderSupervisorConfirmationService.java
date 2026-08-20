package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskDecisionReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskSignReqDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;

import java.time.LocalDateTime;
import java.util.*;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSupervisorCursorReqVO;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class SalesOrderSupervisorConfirmationService {
    @Resource private SalesOrderMapper orderMapper;
    @Resource private SalesOrderApprovalRoundMapper roundMapper;
    @Resource private SalesOrderSupervisorConfirmationMapper confirmationMapper;
    @Resource private SalesOrderCommandService commandService;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;
    @Resource private PermissionApi permissionApi;
    @Resource private AdvancedFilterService advancedFilterService;
    @Resource private SalesOrderObjectPermissionService objectPermissionService;

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "review")
    public void request(Long orderId, Long userId, SalesOrderSupervisorRequestReqVO reqVO) {
        SalesOrderDO order = requireOrder(orderId);
        SalesOrderApprovalRoundDO round = requireRound(order, reqVO.getApprovalRoundId());
        if (!Boolean.TRUE.equals(round.getSupervisorConfirmationEnabled())) throw exception(SALES_ORDER_SUPERVISOR_LEGACY_ROUND);
        String fingerprint = commandService.fingerprint(reqVO.getReason().trim(), reqVO.getOrderVersion(), reqVO.getRoundVersion());
        SalesOrderCommandService.Command replay = new SalesOrderCommandService.Command(orderId, round.getId(),
                round.getProcessInstanceId(), "request_supervisor", null, reqVO.getTaskId(), userId, fingerprint);
        if (commandService.replayDecision(reqVO.getIdempotencyKey(), replay)) return;
        requireVersions(order, round, reqVO.getOrderVersion(), reqVO.getRoundVersion());
        BpmTaskRespDTO task = requireTodo(userId, reqVO.getTaskId(), round, orderId, false);
        if (!confirmationMapper.selectByRoundId(round.getId()).isEmpty()) {
            throw exception(SALES_ORDER_SUPERVISOR_ALREADY_REQUESTED);
        }
        AdminUserRespDTO formalSales = order.getFormalSalesUserId() == null
                ? null : adminUserApi.getUser(order.getFormalSalesUserId());
        DeptRespDTO salesDept = formalSales == null || formalSales.getDeptId() == null
                ? null : deptApi.getDept(formalSales.getDeptId());
        if (salesDept == null || salesDept.getLeaderUserId() == null) {
            throw exception(SALES_ORDER_SUPERVISOR_NOT_CONFIGURED);
        }
        if (Objects.equals(salesDept.getLeaderUserId(), order.getFormalSalesUserId())) {
            throw exception(SALES_ORDER_SUPERVISOR_SELF);
        }
        AdminUserRespDTO supervisor = adminUserApi.getUser(salesDept.getLeaderUserId());
        if (supervisor == null || !CommonStatusEnum.ENABLE.getStatus().equals(supervisor.getStatus())) {
            throw exception(SALES_ORDER_SUPERVISOR_DISABLED);
        }
        if (!permissionApi.hasAnyPermissions(supervisor.getId(), "zsjos:sales-order:supervisor-confirm")) {
            throw exception(SALES_ORDER_SUPERVISOR_PERMISSION_NOT_GRANTED);
        }
        commandService.register(reqVO.getIdempotencyKey(), new SalesOrderCommandService.Command(orderId, round.getId(),
                round.getProcessInstanceId(), "request_supervisor", task.getTaskDefinitionKey(), task.getId(), userId, fingerprint));
        String supervisorTaskId = processTaskApi.createParallelSignTask(userId, new BpmTaskSignReqDTO()
                .setTaskId(task.getId()).setAssigneeUserId(supervisor.getId()).setReason(reqVO.getReason().trim()));
        SalesOrderSupervisorConfirmationDO row = new SalesOrderSupervisorConfirmationDO();
        row.setOrderId(orderId); row.setApprovalRoundId(round.getId()); row.setTaskDefinitionKey(task.getTaskDefinitionKey());
        row.setRequesterUserId(userId); row.setSupervisorUserId(supervisor.getId()); row.setParentTaskId(task.getId());
        row.setSupervisorTaskId(supervisorTaskId); row.setRequestReason(reqVO.getReason().trim()); row.setStatus(SUPERVISOR_PENDING);
        row.setRequestedAt(LocalDateTime.now()); row.setVersion(0); confirmationMapper.insert(row);
        bumpVersions(order, round);
        publishNotification(cn.iocoder.yudao.module.zsjos.enums.SalesOrderNotifySceneConstants.SUPERVISOR_REQUESTED,
                orderId, row, userId, supervisor.getId(), userId, reqVO.getReason().trim(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "read")
    public void decide(Long orderId, Long userId, SalesOrderSupervisorDecisionReqVO reqVO, boolean confirm) {
        SalesOrderDO order = requireOrder(orderId);
        SalesOrderApprovalRoundDO round = requireRound(order, reqVO.getApprovalRoundId());
        SalesOrderSupervisorConfirmationDO row = confirmationMapper.selectByIdForUpdate(reqVO.getConfirmationId(),
                TenantContextHolder.getRequiredTenantId());
        if (row == null || !Objects.equals(row.getOrderId(), orderId) || !Objects.equals(row.getApprovalRoundId(), round.getId())) {
            throw exception(SALES_ORDER_SUPERVISOR_CONFIRMATION_NOT_EXISTS);
        }
        String commandType = confirm ? "confirm_supervisor" : "reject_supervisor";
        String fingerprint = commandService.fingerprint(reqVO.getReason().trim(), reqVO.getOrderVersion(),
                reqVO.getRoundVersion(), reqVO.getConfirmationVersion());
        SalesOrderCommandService.Command command = new SalesOrderCommandService.Command(orderId, round.getId(),
                round.getProcessInstanceId(), commandType, row.getTaskDefinitionKey(), reqVO.getTaskId(), userId, fingerprint);
        if (commandService.replay(reqVO.getIdempotencyKey(), command)) return;
        if (!Objects.equals(row.getSupervisorUserId(), userId)) throw exception(SALES_ORDER_SUPERVISOR_PERMISSION_DENIED);
        if (!SUPERVISOR_PENDING.equals(row.getStatus()) || !Objects.equals(row.getVersion(), reqVO.getConfirmationVersion())
                || !Objects.equals(row.getSupervisorTaskId(), reqVO.getTaskId())) throw exception(SALES_ORDER_SUPERVISOR_TASK_EXPIRED);
        requireVersions(order, round, reqVO.getOrderVersion(), reqVO.getRoundVersion());
        requireTodo(userId, reqVO.getTaskId(), round, orderId, true);
        commandService.register(reqVO.getIdempotencyKey(), command);
        row.setStatus(confirm ? SUPERVISOR_CONFIRMED : SUPERVISOR_REJECTED);
        row.setDecisionReason(reqVO.getReason().trim()); row.setDecidedAt(LocalDateTime.now()); row.setVersion(row.getVersion() + 1);
        confirmationMapper.updateById(row); bumpVersions(order, round);
        publishNotification(cn.iocoder.yudao.module.zsjos.enums.SalesOrderNotifySceneConstants.SUPERVISOR_DECIDED,
                orderId, row, userId, row.getSupervisorUserId(), row.getRequesterUserId(), reqVO.getReason().trim(),
                confirm ? SUPERVISOR_CONFIRMED : SUPERVISOR_REJECTED);
        BpmTaskDecisionReqDTO decision = new BpmTaskDecisionReqDTO().setTaskId(reqVO.getTaskId()).setReason(reqVO.getReason().trim());
        if (confirm) processTaskApi.approveTask(userId, decision); else processTaskApi.rejectTask(userId, decision);
    }

    public PageResult<SalesOrderSupervisorConfirmationRespVO> getInboxPage(SalesOrderSupervisorPageReqVO reqVO, Long userId) {
        List<Long> orderIds = intersectOrderIds(orderMapper.selectIdsByKeyword(reqVO.getKeyword()),
                advancedFilterService.matchOrderIds(reqVO.getAdvancedFilter()));
        PageResult<SalesOrderSupervisorConfirmationDO> page = confirmationMapper.selectPageBySupervisor(userId, reqVO, orderIds);
        Set<Long> requesterIds = new HashSet<>(); page.getList().forEach(item -> requesterIds.add(item.getRequesterUserId()));
        Map<Long, AdminUserRespDTO> users = requesterIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(requesterIds);
        List<SalesOrderSupervisorConfirmationRespVO> list = page.getList().stream().map(row -> convert(row, users)).toList();
        return new PageResult<>(list, page.getTotal());
    }

    public SalesOrderSupervisorConfirmationRespVO getConfirmation(Long confirmationId, Long userId) {
        SalesOrderSupervisorConfirmationDO row = confirmationMapper.selectById(confirmationId);
        if (row == null) throw exception(SALES_ORDER_SUPERVISOR_CONFIRMATION_NOT_EXISTS);
        if (!Objects.equals(row.getSupervisorUserId(), userId)) throw exception(SALES_ORDER_SUPERVISOR_PERMISSION_DENIED);
        Map<Long, AdminUserRespDTO> users = row.getRequesterUserId() == null ? Map.of()
                : adminUserApi.getUserMap(Set.of(row.getRequesterUserId()));
        return convert(row, users);
    }

    public CursorPageResult<SalesOrderSupervisorConfirmationRespVO> getInboxCursor(SalesOrderSupervisorCursorReqVO reqVO, Long userId) {
        List<Long> orderIds = intersectOrderIds(orderMapper.selectIdsByKeyword(reqVO.getKeyword()),
                advancedFilterService.matchOrderIds(reqVO.getAdvancedFilter()));
        int fingerprint = Objects.hash(reqVO.getKeyword(), reqVO.getAdvancedFilter());
        Cursor cursor = decodeCursor(reqVO.getCursor(), userId, reqVO.getHandled(), fingerprint);
        int limit = reqVO.getLimit() == null ? 20 : reqVO.getLimit();
        List<SalesOrderSupervisorConfirmationDO> rows = confirmationMapper.selectCursorBySupervisor(userId, reqVO.getHandled(), orderIds,
                cursor == null ? null : cursor.time(), cursor == null ? null : cursor.id(), limit + 1);
        boolean more = rows.size() > limit;
        List<SalesOrderSupervisorConfirmationDO> page = more ? rows.subList(0, limit) : rows;
        Set<Long> requesterIds = page.stream().map(SalesOrderSupervisorConfirmationDO::getRequesterUserId).collect(java.util.stream.Collectors.toSet());
        Map<Long, AdminUserRespDTO> users = requesterIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(requesterIds);
        List<SalesOrderSupervisorConfirmationRespVO> list = page.stream().map(row -> convert(row, users)).toList();
        SalesOrderSupervisorConfirmationDO last = page.isEmpty() ? null : page.get(page.size() - 1);
        String next = more ? encodeCursor(last, userId, reqVO.getHandled(), fingerprint) : null;
        return new CursorPageResult<>(list, next, more);
    }

    public SalesOrderApprovalTaskTargetRespVO getTaskTarget(String taskId, Long userId) {
        SalesOrderSupervisorConfirmationDO confirmation = confirmationMapper.selectBySupervisorTaskId(taskId);
        if (confirmation != null) {
            if (!Objects.equals(confirmation.getSupervisorUserId(), userId)) throw exception(SALES_ORDER_PERMISSION_DENIED);
            if (SUPERVISOR_PENDING.equals(confirmation.getStatus())) {
                BpmTaskRespDTO task = processTaskApi.getTodoTask(userId, taskId);
                if (task == null || !Boolean.TRUE.equals(task.getSignTask())) throw exception(SALES_ORDER_SUPERVISOR_TASK_EXPIRED);
            }
            objectPermissionService.check(confirmation.getOrderId(), "read");
            return taskTarget("supervisor", confirmation.getOrderId(), taskId, confirmation.getTaskDefinitionKey(),
                    confirmation.getId(), confirmation.getStatus());
        }
        BpmTaskRespDTO task = processTaskApi.getTodoTask(userId, taskId);
        if (task == null || Boolean.TRUE.equals(task.getSignTask())
                || !Set.of(TASK_REGISTRATION, TASK_FINANCE).contains(task.getTaskDefinitionKey())) {
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        }
        Long orderId = parseOrderId(task.getBusinessKey());
        if (orderId == null) throw exception(SALES_ORDER_PERMISSION_DENIED);
        objectPermissionService.check(orderId, "review");
        return taskTarget("approval", orderId, taskId, task.getTaskDefinitionKey(), null, "pending");
    }

    public SalesOrderApprovalTaskTargetRespVO getNotificationTarget(Long orderId, String sceneCode,
                                                                     String sourceEventKey, Long userId) {
        Long confirmationId = parseConfirmationId(sceneCode, sourceEventKey);
        SalesOrderSupervisorConfirmationDO confirmation = confirmationId == null
                ? confirmationMapper.selectLatestByOrderId(orderId) : confirmationMapper.selectById(confirmationId);
        if (confirmation == null) throw exception(SALES_ORDER_SUPERVISOR_CONFIRMATION_NOT_EXISTS);
        if (!Objects.equals(confirmation.getOrderId(), orderId)) throw exception(SALES_ORDER_PERMISSION_DENIED);
        if (cn.iocoder.yudao.module.zsjos.enums.SalesOrderNotifySceneConstants.SUPERVISOR_REQUESTED.equals(sceneCode)) {
            if (!Objects.equals(confirmation.getSupervisorUserId(), userId)) throw exception(SALES_ORDER_PERMISSION_DENIED);
            objectPermissionService.check(orderId, "read");
            return taskTarget("supervisor", orderId, confirmation.getSupervisorTaskId(),
                    confirmation.getTaskDefinitionKey(), confirmation.getId(), confirmation.getStatus());
        }
        if (cn.iocoder.yudao.module.zsjos.enums.SalesOrderNotifySceneConstants.SUPERVISOR_DECIDED.equals(sceneCode)) {
            if (!Objects.equals(confirmation.getRequesterUserId(), userId)) throw exception(SALES_ORDER_PERMISSION_DENIED);
            objectPermissionService.check(orderId, "review");
            return taskTarget("approval", orderId, confirmation.getParentTaskId(),
                    confirmation.getTaskDefinitionKey(), confirmation.getId(), confirmation.getStatus());
        }
        throw exception(SALES_ORDER_PERMISSION_DENIED);
    }

    private Long parseConfirmationId(String sceneCode, String sourceEventKey) {
        String prefix = sceneCode + ":";
        if (sourceEventKey == null || sourceEventKey.isBlank()) return null;
        if (!sourceEventKey.startsWith(prefix)) throw exception(SALES_ORDER_PERMISSION_DENIED);
        try { return Long.valueOf(sourceEventKey.substring(prefix.length())); }
        catch (NumberFormatException ignored) { throw exception(SALES_ORDER_PERMISSION_DENIED); }
    }

    private SalesOrderApprovalTaskTargetRespVO taskTarget(String workType, Long orderId, String taskId,
                                                           String taskDefinitionKey, Long confirmationId, String status) {
        SalesOrderApprovalTaskTargetRespVO result = new SalesOrderApprovalTaskTargetRespVO();
        result.setWorkType(workType); result.setOrderId(orderId); result.setTaskId(taskId);
        result.setTaskDefinitionKey(taskDefinitionKey); result.setConfirmationId(confirmationId); result.setStatus(status);
        result.setCenter(TASK_REGISTRATION.equals(taskDefinitionKey) ? CENTER_REGISTRATION : CENTER_FINANCE);
        return result;
    }

    private Long parseOrderId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith(BUSINESS_KEY_PREFIX)) return null;
        try { return Long.valueOf(businessKey.substring(BUSINESS_KEY_PREFIX.length())); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String encodeCursor(SalesOrderSupervisorConfirmationDO row, Long userId, Boolean handled, int fingerprint) {
        String raw = row.getRequestedAt() + "|" + row.getId() + "|" + userId + "|" + handled + "|" + fingerprint;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
    private Cursor decodeCursor(String value, Long userId, Boolean handled, int fingerprint) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] p = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (p.length != 5 || !p[2].equals(String.valueOf(userId)) || !p[3].equals(String.valueOf(handled))
                    || !p[4].equals(String.valueOf(fingerprint))) throw new IllegalArgumentException();
            return new Cursor(java.time.LocalDateTime.parse(p[0]), Long.valueOf(p[1]));
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid supervisor cursor", ex); }
    }
    private record Cursor(java.time.LocalDateTime time, Long id) {}

    private List<Long> intersectOrderIds(List<Long> keywordIds, List<Long> advancedIds) {
        if (keywordIds == null) return advancedIds;
        if (advancedIds == null) return keywordIds;
        Set<Long> allowed = new HashSet<>(advancedIds);
        return keywordIds.stream().filter(allowed::contains).toList();
    }

    public SalesOrderSupervisorConfirmationDO getPending(Long roundId, String taskKey) {
        SalesOrderSupervisorConfirmationDO row = confirmationMapper.selectByRoundAndTaskKey(roundId, taskKey);
        return row != null && SUPERVISOR_PENDING.equals(row.getStatus()) ? row : null;
    }

    public List<SalesOrderSupervisorConfirmationDO> getByRound(Long roundId) {
        return roundId == null ? List.of() : confirmationMapper.selectByRoundId(roundId);
    }

    public void cancelPending(Long roundId, LocalDateTime now) {
        for (SalesOrderSupervisorConfirmationDO row : confirmationMapper.selectByRoundId(roundId)) {
            if (!SUPERVISOR_PENDING.equals(row.getStatus())) continue;
            row.setStatus(SUPERVISOR_CANCELLED); row.setDecidedAt(now); row.setVersion(row.getVersion() + 1);
            confirmationMapper.updateById(row);
        }
    }

    public void cancelPending(Long roundId, String taskDefinitionKey, LocalDateTime now) {
        SalesOrderSupervisorConfirmationDO row = confirmationMapper.selectByRoundAndTaskKey(roundId, taskDefinitionKey);
        if (row == null || !SUPERVISOR_PENDING.equals(row.getStatus())) return;
        row.setStatus(SUPERVISOR_CANCELLED); row.setDecidedAt(now); row.setVersion(row.getVersion() + 1);
        confirmationMapper.updateById(row);
    }

    private SalesOrderSupervisorConfirmationRespVO convert(SalesOrderSupervisorConfirmationDO row,
                                                            Map<Long, AdminUserRespDTO> users) {
        SalesOrderSupervisorConfirmationRespVO result = new SalesOrderSupervisorConfirmationRespVO();
        SalesOrderDO order = orderMapper.selectById(row.getOrderId());
        SalesOrderApprovalRoundDO round = roundMapper.selectById(row.getApprovalRoundId());
        result.setId(row.getId()); result.setOrderId(row.getOrderId()); result.setApprovalRoundId(row.getApprovalRoundId());
        result.setOrderNo(order == null ? null : order.getOrderNo()); result.setStudentName(order == null ? null : order.getStudentName());
        result.setTaskDefinitionKey(row.getTaskDefinitionKey()); result.setTaskId(row.getSupervisorTaskId());
        result.setRequesterUserId(row.getRequesterUserId()); AdminUserRespDTO requester = users.get(row.getRequesterUserId());
        result.setRequesterUserName(requester == null ? null : requester.getNickname()); result.setSupervisorUserId(row.getSupervisorUserId());
        result.setRequestReason(row.getRequestReason()); result.setDecisionReason(row.getDecisionReason()); result.setStatus(row.getStatus());
        result.setRequestedAt(row.getRequestedAt()); result.setDecidedAt(row.getDecidedAt()); result.setVersion(row.getVersion());
        result.setOrderVersion(order == null ? null : order.getVersion()); result.setRoundVersion(round == null ? null : round.getVersion());
        return result;
    }

    private SalesOrderDO requireOrder(Long orderId) {
        SalesOrderDO order = orderMapper.selectByIdForUpdate(orderId, TenantContextHolder.getRequiredTenantId());
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        if (!STATUS_PENDING_APPROVAL.equals(order.getStatus())) throw exception(SALES_ORDER_STATE_INVALID);
        return order;
    }

    private SalesOrderApprovalRoundDO requireRound(SalesOrderDO order, Long roundId) {
        SalesOrderApprovalRoundDO round = roundMapper.selectByIdForUpdate(roundId, TenantContextHolder.getRequiredTenantId());
        if (round == null || !Objects.equals(round.getOrderId(), order.getId())
                || !Objects.equals(order.getCurrentApprovalRoundId(), roundId) || !ROUND_PENDING.equals(round.getStatus())) {
            throw exception(SALES_ORDER_VERSION_CONFLICT);
        }
        return round;
    }

    private void requireVersions(SalesOrderDO order, SalesOrderApprovalRoundDO round, Integer orderVersion, Integer roundVersion) {
        if (!Objects.equals(order.getVersion(), orderVersion) || !Objects.equals(round.getVersion(), roundVersion)) {
            throw exception(SALES_ORDER_VERSION_CONFLICT);
        }
    }

    private BpmTaskRespDTO requireTodo(Long userId, String taskId, SalesOrderApprovalRoundDO round, Long orderId, boolean sign) {
        BpmTaskRespDTO task;
        try { task = processTaskApi.getTodoTask(userId, taskId); }
        catch (RuntimeException ex) { throw exception(sign ? SALES_ORDER_SUPERVISOR_TASK_EXPIRED : SALES_ORDER_ALREADY_HANDLED); }
        if (!Objects.equals(task.getProcessInstanceId(), round.getProcessInstanceId())
                || !Objects.equals(task.getBusinessKey(), BUSINESS_KEY_PREFIX + orderId)
                || !Set.of(TASK_REGISTRATION, TASK_FINANCE).contains(task.getTaskDefinitionKey())
                || !Objects.equals(Boolean.TRUE.equals(task.getSignTask()), sign)) throw exception(SALES_ORDER_PERMISSION_DENIED);
        return task;
    }

    private void bumpVersions(SalesOrderDO order, SalesOrderApprovalRoundDO round) {
        order.setVersion(order.getVersion() + 1); round.setVersion(round.getVersion() + 1);
        orderMapper.updateById(order); roundMapper.updateById(round);
    }

    private void publishNotification(String scene, Long orderId, SalesOrderSupervisorConfirmationDO confirmation,
                                     Long operatorUserId, Long supervisorUserId, Long requesterUserId,
                                     String reason, String decision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supervisorUserId", supervisorUserId); payload.put("requesterUserId", requesterUserId);
        payload.put("supervisorReason", confirmation.getRequestReason()); payload.put("decisionReason", reason);
        payload.put("supervisorDecision", decision);
        payload.put("confirmationId", confirmation.getId());
        payload.put("taskDefinitionKey", confirmation.getTaskDefinitionKey());
        payload.put("supervisorTaskId", confirmation.getSupervisorTaskId());
        payload.put("requesterTaskId", confirmation.getParentTaskId());
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(scene)
                .sourceEventKey(scene + ":" + confirmation.getId()).bizType("sales_order").bizId(orderId)
                .operatorUserId(operatorUserId).occurredAt(LocalDateTime.now()).payload(payload).build());
    }
}
