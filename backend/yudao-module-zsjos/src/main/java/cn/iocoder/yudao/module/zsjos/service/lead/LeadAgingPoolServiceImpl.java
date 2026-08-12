package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyTimingRuleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadAgingPoolServiceImpl implements LeadAgingPoolService {
    private static final String NOTIFY_PENDING = "pending";
    private static final String NOTIFY_SENT = "sent";
    private static final String NOTIFY_FAILED = "failed";
    @Resource private LeadAgingPoolCycleMapper cycleMapper;
    @Resource private LeadAgingPoolEventMapper eventMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private LeadFollowUpRuleService ruleService;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private SecurityFrameworkService securityFrameworkService;
    @Resource private LeadNotifyEventPublisher notifyPublisher;
    @Resource private NotifyRuleApi notifyRuleApi;
    @Resource private LeadAgingPoolNotifyStageMapper notifyStageMapper;
    @Resource private TransactionTemplate transactionTemplate;
    @Resource private LeadInboxFilterConfigService inboxFilterConfigService;

    @Override
    public PageResult<LeadAgingPoolRespVO> getPage(LeadAgingPoolPageReqVO reqVO, Long userId) {
        List<Long> deptIds = visibleDeptIds(userId);
        boolean manageAll = hasManageAll();
        LeadInboxFilterQuery filter = reqVO.getInboxGroup() == null && reqVO.getInboxStage() == null
                ? new LeadInboxFilterQuery(Set.of(), Set.of(), false)
                : inboxFilterConfigService.resolveQuery(
                        inboxFilterConfigService.getPublishedConfig(INBOX_AUDIENCE_AGING_POOL),
                        reqVO.getInboxGroup(), reqVO.getInboxStage());
        PageResult<LeadAgingPoolCycleDO> page = cycleMapper.selectPage(reqVO, manageAll ? null : deptIds,
                manageAll ? null : userId,
                List.copyOf(filter.values(INBOX_FILTER_FIELD_POOL_STATUS)), filter.matchNone());
        List<LeadAgingPoolRespVO> rows = page.getList().stream().map(cycle -> convert(cycle, userId)).toList();
        return new PageResult<>(rows, page.getTotal());
    }

    @Override public LeadAgingPoolRespVO get(Long cycleId, Long userId) { return convert(requireVisible(cycleId, userId), userId); }

    @Override
    public Map<String, Long> getCounts(Long userId) {
        List<Long> deptIds = hasManageAll() ? null : visibleDeptIds(userId);
        boolean manageAll = hasManageAll();
        Long participant = manageAll ? null : userId;
        long waiting = cycleMapper.selectCountByStatus(deptIds, participant, AGING_POOL_WAITING_ASSIGNMENT);
        long assigned = cycleMapper.selectCountByStatus(deptIds, participant, AGING_POOL_ASSIGNED);
        long pending = cycleMapper.selectCountByStatus(deptIds, participant, AGING_POOL_DEAL_PENDING);
        return Map.of("all", waiting + assigned + pending, AGING_POOL_WAITING_ASSIGNMENT, waiting,
                AGING_POOL_ASSIGNED, assigned, AGING_POOL_DEAL_PENDING, pending);
    }

    @Override
    public LeadInboxFilterProfileRespVO getFilterProfile(Long userId) {
        LeadInboxFilterConfigVO config = inboxFilterConfigService.getPublishedConfig(INBOX_AUDIENCE_AGING_POOL);
        Map<String, Long> counts = getCounts(userId);
        List<LeadInboxFilterProfileRespVO.GroupVO> groups = config.getGroups().stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .map(group -> {
                    LeadInboxFilterQuery groupQuery = inboxFilterConfigService.resolveQuery(config, group.getKey(), "all");
                    long groupCount = countPoolStatuses(counts, groupQuery.values(INBOX_FILTER_FIELD_POOL_STATUS));
                    List<LeadInboxFilterProfileRespVO.OptionVO> options = group.getOptions().stream()
                            .filter(option -> Boolean.TRUE.equals(option.getEnabled()))
                            .map(option -> {
                                LeadInboxFilterQuery query = inboxFilterConfigService.resolveQuery(
                                        config, group.getKey(), option.getKey());
                                return new LeadInboxFilterProfileRespVO.OptionVO(option.getKey(), option.getLabel(),
                                        countPoolStatuses(counts, query.values(INBOX_FILTER_FIELD_POOL_STATUS)));
                            }).toList();
                    List<LeadInboxFilterProfileRespVO.SectionVO> sections = options.isEmpty() ? List.of() : List.of(
                            new LeadInboxFilterProfileRespVO.SectionVO("pool_status",
                                    group.getSectionLabel() == null ? "公海状态" : group.getSectionLabel(), options));
                    return new LeadInboxFilterProfileRespVO.GroupVO(group.getKey(), group.getLabel(), groupCount, sections);
                }).toList();
        return new LeadInboxFilterProfileRespVO(groups);
    }

    private static long countPoolStatuses(Map<String, Long> counts, Set<String> statuses) {
        if (statuses.isEmpty()) return counts.getOrDefault("all", 0L);
        return statuses.stream().mapToLong(status -> counts.getOrDefault(status, 0L)).sum();
    }

    @Override
    public List<LeadAgingPoolCandidateRespVO> getCandidates(Long cycleId, Long userId) {
        LeadAgingPoolCycleDO cycle = requireManageable(cycleId, userId);
        return eligibleSales(cycle).stream().map(user -> new LeadAgingPoolCandidateRespVO(user.getId(), user.getNickname())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long cycleId, Long userId, LeadAgingPoolAssignReqVO reqVO) {
        LeadAgingPoolEventDO replay = eventMapper.selectByIdempotencyKey(reqVO.getIdempotencyKey());
        if (replay != null) {
            if (Objects.equals(replay.getCycleId(), cycleId) && Objects.equals(replay.getCollaboratorUserId(), reqVO.getSalesUserId())) return;
            throw exception(LEAD_AGING_POOL_IDEMPOTENCY_CONFLICT);
        }
        LeadAgingPoolCycleDO cycle = cycleMapper.selectByIdForUpdate(cycleId, TenantContextHolder.getRequiredTenantId());
        requireManageable(cycle, userId);
        if (!Set.of(AGING_POOL_WAITING_ASSIGNMENT, AGING_POOL_ASSIGNED).contains(cycle.getStatus())) {
            throw exception(LEAD_AGING_POOL_STATE_INVALID);
        }
        AdminUserRespDTO target = requireEligibleSales(cycle, reqVO.getSalesUserId());
        Long previous = cycle.getCollaboratorUserId();
        if (Objects.equals(previous, target.getId())) return;
        LocalDateTime now = LocalDateTime.now();
        cycle.setCollaboratorUserId(target.getId()); cycle.setAssignedAt(now); cycle.setStatus(AGING_POOL_ASSIGNED);
        updateCycle(cycle);
        String eventType = previous == null ? AGING_POOL_EVENT_ASSIGNED : AGING_POOL_EVENT_REASSIGNED;
        addEvent(cycle, eventType, userId, previous, target.getId(), null, reqVO.getIdempotencyKey(), now);
        publish(previous == null ? AGING_POOL_ASSIGNED_NOTICE : AGING_POOL_REASSIGNED_NOTICE, cycle,
                "aging-pool-" + eventType + ":" + reqVO.getIdempotencyKey(), userId, previous, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exit(Long cycleId, Long userId, LeadAgingPoolExitReqVO reqVO) {
        LeadAgingPoolEventDO replay = eventMapper.selectByIdempotencyKey(reqVO.getIdempotencyKey());
        if (replay != null) {
            if (Objects.equals(replay.getCycleId(), cycleId) && AGING_POOL_EVENT_EXITED.equals(replay.getEventType())) return;
            throw exception(LEAD_AGING_POOL_IDEMPOTENCY_CONFLICT);
        }
        LeadAgingPoolCycleDO cycle = cycleMapper.selectByIdForUpdate(cycleId, TenantContextHolder.getRequiredTenantId());
        requireManageable(cycle, userId);
        if (!Set.of(AGING_POOL_WAITING_ASSIGNMENT, AGING_POOL_ASSIGNED).contains(cycle.getStatus())) {
            throw exception(LEAD_AGING_POOL_STATE_INVALID);
        }
        AdminUserRespDTO owner = adminUserApi.getUser(cycle.getOriginalOwnerUserId());
        if (owner == null || !CommonStatusEnum.ENABLE.getStatus().equals(owner.getStatus())) throw exception(LEAD_AGING_POOL_OWNER_INVALID);
        LocalDateTime now = LocalDateTime.now();
        LeadDO lead = leadMapper.selectByIdForUpdate(cycle.getLeadId(), TenantContextHolder.getRequiredTenantId());
        lead.setOwnershipStartedAt(now); leadMapper.updateById(lead);
        cycle.setStatus(AGING_POOL_EXITED); cycle.setExitedAt(now); cycle.setExitReason(reqVO.getReason().trim());
        updateCycle(cycle);
        addEvent(cycle, AGING_POOL_EVENT_EXITED, userId, cycle.getCollaboratorUserId(), null,
                cycle.getExitReason(), reqVO.getIdempotencyKey(), now);
        publish(AGING_POOL_EXITED_NOTICE, cycle, "aging-pool-exited:" + reqVO.getIdempotencyKey(), userId,
                cycle.getCollaboratorUserId(), now);
    }

    @Override
    public int scanDue(LocalDateTime now) {
        LeadFollowUpRuleDO rule = ruleService.requireEnabledRule();
        LocalDateTime cutoff = now.minusDays(rule.getAgingPoolTimeoutDays());
        int count = 0;
        for (LeadDO candidate : leadMapper.selectAgingPoolCandidates(TenantContextHolder.getRequiredTenantId(), cutoff)) {
            Boolean entered = transactionTemplate.execute(status -> enterDueLead(candidate.getId(), now));
            if (Boolean.TRUE.equals(entered)) count++;
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryEnterDueLead(Long leadId, LocalDateTime now) {
        return enterDueLead(leadId, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearInvalidCollaborators(LocalDateTime now) {
        int changed = 0;
        for (LeadAgingPoolCycleDO snapshot : cycleMapper.selectList(LeadAgingPoolCycleDO::getStatus, AGING_POOL_ASSIGNED)) {
            LeadAgingPoolCycleDO cycle = cycleMapper.selectByIdForUpdate(snapshot.getId(), TenantContextHolder.getRequiredTenantId());
            if (cycle == null || !AGING_POOL_ASSIGNED.equals(cycle.getStatus())) continue;
            if (cycle.getCollaboratorUserId() == null) continue;
            AdminUserRespDTO user = adminUserApi.getUser(cycle.getCollaboratorUserId());
            if (user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                    && Objects.equals(user.getDeptId(), cycle.getFrozenDeptId()) && isEligibleSales(user.getId())) continue;
            Long previous = cycle.getCollaboratorUserId(); cycle.setCollaboratorUserId(null);
            cycle.setAssignedAt(null); cycle.setStatus(AGING_POOL_WAITING_ASSIGNMENT); updateCycle(cycle);
            String key = "aging-pool-collaborator-cleared:" + cycle.getId() + ":" + now;
            addEvent(cycle, AGING_POOL_EVENT_COLLABORATOR_CLEARED, 0L, previous, null,
                    "协同销售已停用、调离冻结部门或不再具备销售资格", key, now);
            publish(AGING_POOL_REASSIGN_REQUIRED_NOTICE, cycle, key, 0L, previous, now); changed++;
        }
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int emitAdvanceReminders(LocalDateTime now) {
        List<NotifyTimingRuleRespDTO> rules = notifyRuleApi.getEnabledTimingRules(List.of(AGING_POOL_REMINDER)).stream()
                .filter(rule -> "advance".equals(rule.getTimingStage())).toList();
        if (rules.isEmpty()) return 0;
        int maxAdvance = rules.stream().map(NotifyTimingRuleRespDTO::getTimingOffsetMinutes)
                .max(Integer::compareTo).orElse(0);
        LeadFollowUpRuleDO config = ruleService.requireEnabledRule();
        LocalDateTime latestStart = now.plusMinutes(maxAdvance).minusDays(config.getAgingPoolTimeoutDays());
        int emitted = 0;
        for (LeadDO lead : leadMapper.selectAgingPoolReminderCandidates(TenantContextHolder.getRequiredTenantId(), latestStart)) {
            int nextCycleNo = cycleMapper.selectNextCycleNo(lead.getId());
            LocalDateTime dueAt = lead.getOwnershipStartedAt().plusDays(config.getAgingPoolTimeoutDays());
            AdminUserRespDTO owner = adminUserApi.getUser(lead.getOwnerUserId());
            if (owner == null || owner.getDeptId() == null) continue;
            for (NotifyTimingRuleRespDTO rule : rules) {
                if (!now.isBefore(dueAt) || dueAt.minusMinutes(rule.getTimingOffsetMinutes()).isAfter(now)) continue;
                LeadAgingPoolNotifyStageDO stage = notifyStageMapper.selectByRule(lead.getId(), nextCycleNo, rule.getId());
                if (stage != null && (NOTIFY_SENT.equals(stage.getStatus())
                        || stage.getNextRetryAt() != null && stage.getNextRetryAt().isAfter(now))) continue;
                if (stage == null) {
                    stage = new LeadAgingPoolNotifyStageDO();
                    stage.setLeadId(lead.getId()); stage.setCycleNo(nextCycleNo); stage.setNotifyRuleId(rule.getId());
                    stage.setStage("advance"); stage.setStatus(NOTIFY_PENDING); stage.setAttemptCount(0);
                    stage.setEmittedAt(now);
                    try {
                        notifyStageMapper.insert(stage);
                    } catch (DuplicateKeyException ignored) {
                        stage = notifyStageMapper.selectByRule(lead.getId(), nextCycleNo, rule.getId());
                        if (stage == null || NOTIFY_SENT.equals(stage.getStatus())
                                || stage.getNextRetryAt() != null && stage.getNextRetryAt().isAfter(now)) continue;
                    }
                }
                Map<String,Object> context = new LinkedHashMap<>(); context.put("ownerUserId", lead.getOwnerUserId());
                context.put("frozenDeptId", owner.getDeptId()); context.put("agingPool.cycleId", null);
                context.put("agingPool.dueAt", dueAt); context.put("reminder.stage", "advance"); context.put("reminder.dueAt", dueAt);
                NotifySendResult result = notifyPublisher.publishConfirmed(AGING_POOL_REMINDER, lead.getId(),
                        "aging-pool-reminder:" + lead.getId() + ":" + nextCycleNo + ":" + rule.getId(),
                        rule.getId(), null, now, context);
                LeadAgingPoolNotifyStageDO update = new LeadAgingPoolNotifyStageDO();
                update.setId(stage.getId());
                int attempts = Optional.ofNullable(stage.getAttemptCount()).orElse(0) + 1;
                update.setAttemptCount(attempts);
                if (result.isSuccess()) {
                    update.setStatus(NOTIFY_SENT); update.setSentAt(now); update.setNextRetryAt(null);
                    update.setLastErrorCode(null); emitted++;
                } else {
                    update.setStatus(NOTIFY_FAILED);
                    update.setLastErrorCode(result.getErrorCode() == null ? "NOTIFY_DELIVERY_FAILED" : result.getErrorCode());
                    update.setNextRetryAt(now.plusMinutes(Math.min(60, 5L * attempts)));
                }
                notifyStageMapper.updateById(update);
            }
        }
        return emitted;
    }

    @Override public Long resolveEffectiveSalesUserId(Long leadId, Long originalOwnerUserId) {
        LeadAgingPoolCycleDO cycle = cycleMapper.selectActiveByLeadId(leadId);
        if (cycle == null) return originalOwnerUserId;
        return AGING_POOL_ASSIGNED.equals(cycle.getStatus()) || AGING_POOL_DEAL_PENDING.equals(cycle.getStatus())
                ? cycle.getCollaboratorUserId() : null;
    }
    @Override public LeadAgingPoolCycleDO getActiveCycle(Long leadId) { return cycleMapper.selectActiveByLeadId(leadId); }

    @Override @Transactional(rollbackFor = Exception.class)
    public void markDealPending(Long leadId, Long salesUserId, LocalDateTime now) {
        LeadAgingPoolCycleDO cycle = cycleMapper.selectActiveByLeadIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (cycle == null) return;
        if (!AGING_POOL_ASSIGNED.equals(cycle.getStatus()) || !Objects.equals(cycle.getCollaboratorUserId(), salesUserId)) {
            throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        }
        cycle.setStatus(AGING_POOL_DEAL_PENDING); updateCycle(cycle);
        addEvent(cycle, AGING_POOL_EVENT_DEAL_PENDING, salesUserId, salesUserId, salesUserId, null,
                "aging-pool-deal-pending:" + leadId + ":" + now, now);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void handleOrderRejected(Long leadId, LocalDateTime now) {
        LeadAgingPoolCycleDO cycle = cycleMapper.selectActiveByLeadIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (cycle != null && AGING_POOL_DEAL_PENDING.equals(cycle.getStatus())) {
            cycle.setStatus(AGING_POOL_ASSIGNED); updateCycle(cycle);
        } else if (cycle == null) {
            tryEnterDueLead(leadId, now);
        }
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void completeConversion(Long leadId, Long salesUserId, LocalDateTime now) {
        LeadAgingPoolCycleDO cycle = cycleMapper.selectActiveByLeadIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (cycle == null) return;
        if (!Objects.equals(cycle.getCollaboratorUserId(), salesUserId)) throw exception(LEAD_AGING_POOL_STATE_INVALID);
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        OpportunityDO opportunity = opportunityMapper.selectByLeadIdForUpdate(
                leadId, TenantContextHolder.getRequiredTenantId());
        lead.setOwnerUserId(salesUserId); lead.setOwnershipStartedAt(now); leadMapper.updateById(lead);
        if (opportunity != null) { opportunity.setOwnerUserId(salesUserId); opportunityMapper.updateById(opportunity); }
        cycle.setStatus(AGING_POOL_CONVERTED); cycle.setConvertedAt(now); updateCycle(cycle);
        addEvent(cycle, AGING_POOL_EVENT_CONVERTED, salesUserId, salesUserId, salesUserId, null,
                "aging-pool-converted:" + leadId + ":" + now, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateForOwnerTransfer(Long leadId, Long newOwnerUserId, Long operatorUserId, LocalDateTime now) {
        LeadAgingPoolCycleDO cycle = cycleMapper.selectActiveByLeadIdForUpdate(
                leadId, TenantContextHolder.getRequiredTenantId());
        if (cycle == null) return;
        if (AGING_POOL_DEAL_PENDING.equals(cycle.getStatus())) {
            throw exception(LEAD_AGING_POOL_STATE_INVALID);
        }
        cycle.setStatus(AGING_POOL_EXITED); cycle.setExitedAt(now);
        cycle.setExitReason("管理员正式转派，原超期公海周期终止");
        updateCycle(cycle);
        addEvent(cycle, AGING_POOL_EVENT_EXITED, operatorUserId, cycle.getCollaboratorUserId(), null,
                cycle.getExitReason(), "aging-pool-transfer-exit:" + cycle.getId() + ":" + now, now);
        publish(AGING_POOL_EXITED_NOTICE, cycle, "aging-pool-transfer-exit:" + cycle.getId() + ":" + now,
                operatorUserId, cycle.getCollaboratorUserId(), now);
    }

    @Override public boolean canRead(Long leadId, Long userId) {
        LeadAgingPoolCycleDO cycle = cycleMapper.selectActiveByLeadId(leadId);
        if (cycle == null) return false;
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return hasManageAll() || user != null && Objects.equals(user.getDeptId(), cycle.getFrozenDeptId()) && isEligibleSales(userId)
                || canManage(cycle, userId) || Objects.equals(userId, cycle.getOriginalOwnerUserId())
                || Objects.equals(userId, cycle.getCollaboratorUserId());
    }

    private LeadAgingPoolRespVO convert(LeadAgingPoolCycleDO cycle, Long userId) {
        LeadDO lead = leadMapper.selectById(cycle.getLeadId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        LeadAgingPoolRespVO result = new LeadAgingPoolRespVO();
        result.setCycleId(cycle.getId()); result.setLeadId(lead.getId()); result.setCycleNo(cycle.getCycleNo()); result.setStatus(cycle.getStatus());
        result.setOriginalOwnerUserId(cycle.getOriginalOwnerUserId()); result.setOriginalOwnerUserName(userName(cycle.getOriginalOwnerUserId()));
        result.setCollaboratorUserId(cycle.getCollaboratorUserId()); result.setCollaboratorUserName(userName(cycle.getCollaboratorUserId()));
        result.setFrozenDeptId(cycle.getFrozenDeptId()); DeptRespDTO dept = deptApi.getDept(cycle.getFrozenDeptId()); result.setFrozenDeptName(dept == null ? null : dept.getName());
        result.setSubmittedName(lead.getSubmittedName()); result.setSubmittedMobile(lead.getSubmittedMobile()); result.setSubmittedWechatId(lead.getSubmittedWechatId());
        result.setLeadCategory(lead.getLeadCategory()); result.setSourceChannel(lead.getSourceChannelId()); result.setOwnershipStartedAt(cycle.getOwnershipStartedAt());
        result.setDueAt(cycle.getDueAt()); result.setEnteredAt(cycle.getEnteredAt()); result.setAssignedAt(cycle.getAssignedAt());
        result.setLastFollowUpAt(lead.getLastFollowUpAt()); result.setNextFollowUpAt(lead.getNextFollowUpAt());
        SalesOrderDO order = orderMapper.selectActiveByLeadId(lead.getId(), ACTIVE_ORDER_STATUSES);
        if (order != null) { result.setActiveSalesOrderId(order.getId()); result.setActiveSalesOrderStatus(order.getStatus()); }
        List<String> actions = new ArrayList<>();
        if (canManage(cycle, userId) && !AGING_POOL_DEAL_PENDING.equals(cycle.getStatus())) { actions.add("ASSIGN"); actions.add("EXIT"); }
        if (Objects.equals(userId, cycle.getCollaboratorUserId()) && AGING_POOL_ASSIGNED.equals(cycle.getStatus())) {
            actions.add(ACTION_ADD_FOLLOW_UP);
            if (order != null && STATUS_REVISION_REQUIRED.equals(order.getStatus())) {
                actions.add(Objects.equals(order.getSubmitterUserId(), userId) ? ACTION_REVISE_DEAL : ACTION_CONTINUE_DEAL);
            } else {
                actions.add(ACTION_ENTER_DEAL);
            }
        }
        result.setAvailableActions(actions); return result;
    }

    private LeadAgingPoolCycleDO requireVisible(Long id, Long userId) { LeadAgingPoolCycleDO cycle = requireCycle(id); if (!canRead(cycle.getLeadId(), userId)) throw exception(LEAD_PERMISSION_DENIED); return cycle; }
    private LeadAgingPoolCycleDO requireManageable(Long id, Long userId) { return requireManageable(requireCycle(id), userId); }
    private LeadAgingPoolCycleDO requireManageable(LeadAgingPoolCycleDO cycle, Long userId) { if (!canManage(cycle, userId)) throw exception(LEAD_AGING_POOL_MANAGER_DENIED); return cycle; }
    private LeadAgingPoolCycleDO requireCycle(Long id) { LeadAgingPoolCycleDO cycle = cycleMapper.selectById(id); if (cycle == null) throw exception(LEAD_AGING_POOL_NOT_EXISTS); return cycle; }
    private boolean canManage(LeadAgingPoolCycleDO cycle, Long userId) { if (hasManageAll()) return true; DeptRespDTO dept = deptApi.getDept(cycle.getFrozenDeptId()); return securityFrameworkService.hasPermission(PERMISSION_AGING_POOL_MANAGE) && dept != null && Objects.equals(dept.getLeaderUserId(), userId); }
    private boolean hasManageAll() { return securityFrameworkService.hasPermission(PERMISSION_AGING_POOL_MANAGE_ALL); }
    private List<Long> visibleDeptIds(Long userId) { AdminUserRespDTO user = adminUserApi.getUser(userId); Set<Long> ids = new LinkedHashSet<>(); if (user != null && user.getDeptId() != null && isEligibleSales(userId)) ids.add(user.getDeptId()); deptApi.getDeptListByLeaderUserId(userId).forEach(dept -> ids.add(dept.getId())); return List.copyOf(ids); }
    private List<AdminUserRespDTO> eligibleSales(LeadAgingPoolCycleDO cycle) { Set<Long> eligible = new HashSet<>(assignmentService.getEligibleSalesUsers().stream().map(LeadAssignmentUserRespVO::getId).toList()); return adminUserApi.getUserListByDeptIds(List.of(cycle.getFrozenDeptId())).stream().filter(user -> eligible.contains(user.getId()) && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()) && !Objects.equals(user.getId(), cycle.getOriginalOwnerUserId())).toList(); }
    private AdminUserRespDTO requireEligibleSales(LeadAgingPoolCycleDO cycle, Long id) { return eligibleSales(cycle).stream().filter(user -> Objects.equals(user.getId(), id)).findFirst().orElseThrow(() -> exception(LEAD_AGING_POOL_SALES_INVALID)); }
    private boolean isEligibleSales(Long id) { return assignmentService.getEligibleSalesUsers().stream().anyMatch(user -> Objects.equals(user.getId(), id)); }
    private void updateCycle(LeadAgingPoolCycleDO cycle) {
        int expectedVersion = Optional.ofNullable(cycle.getVersion()).orElse(0);
        cycle.setVersion(expectedVersion + 1);
        if (cycleMapper.updateWithVersion(cycle, expectedVersion) != 1) {
            throw exception(LEAD_AGING_POOL_STATE_INVALID);
        }
    }
    private String userName(Long id) { AdminUserRespDTO user = id == null ? null : adminUserApi.getUser(id); return user == null ? null : user.getNickname(); }
    private boolean enterDueLead(Long leadId, LocalDateTime now) {
        LeadFollowUpRuleDO rule = ruleService.requireEnabledRule();
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null || lead.getOwnershipStartedAt() == null
                || lead.getOwnershipStartedAt().plusDays(rule.getAgingPoolTimeoutDays()).isAfter(now)
                || cycleMapper.selectActiveByLeadId(lead.getId()) != null) return false;
        if (!ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                || !STATUS_VALID.equals(lead.getStatus())) return false;
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(lead.getId());
        if (opportunity == null || !Set.of(OPPORTUNITY_STATUS_OPEN, OPPORTUNITY_STATUS_FOLLOWING)
                .contains(opportunity.getStatus())
                || orderMapper.selectActiveByLeadId(lead.getId(), Set.of(STATUS_PENDING_APPROVAL)) != null) return false;
        AdminUserRespDTO owner = adminUserApi.getUser(lead.getOwnerUserId());
        if (owner == null || owner.getDeptId() == null) return false;
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setLeadId(lead.getId()); cycle.setCycleNo(cycleMapper.selectNextCycleNo(lead.getId()));
        cycle.setOriginalOwnerUserId(lead.getOwnerUserId()); cycle.setFrozenDeptId(owner.getDeptId());
        cycle.setStatus(AGING_POOL_WAITING_ASSIGNMENT); cycle.setOwnershipStartedAt(lead.getOwnershipStartedAt());
        cycle.setDueAt(lead.getOwnershipStartedAt().plusDays(rule.getAgingPoolTimeoutDays())); cycle.setEnteredAt(now);
        cycle.setIdempotencyKey("aging-pool-enter:" + lead.getId() + ":" + cycle.getCycleNo()); cycle.setVersion(0);
        try { cycleMapper.insert(cycle); } catch (DuplicateKeyException ignored) {
            return cycleMapper.selectActiveByLeadId(lead.getId()) != null;
        }
        addEvent(cycle, AGING_POOL_EVENT_ENTERED, 0L, null, null, null, cycle.getIdempotencyKey(), now);
        publish(AGING_POOL_DUE, cycle, cycle.getIdempotencyKey(), 0L, null, now);
        return true;
    }
    private void addEvent(LeadAgingPoolCycleDO cycle, String type, Long operator, Long previous, Long collaborator, String reason, String key, LocalDateTime now) { LeadAgingPoolEventDO event = new LeadAgingPoolEventDO(); event.setCycleId(cycle.getId()); event.setLeadId(cycle.getLeadId()); event.setEventType(type); event.setOperatorUserId(operator); event.setPreviousCollaboratorUserId(previous); event.setCollaboratorUserId(collaborator); event.setReason(reason); event.setIdempotencyKey(key); event.setOccurredAt(now); eventMapper.insert(event); }
    private void publish(String scene, LeadAgingPoolCycleDO cycle, String key, Long operator, Long previous, LocalDateTime now) { Map<String,Object> context = new LinkedHashMap<>(); context.put("ownerUserId", cycle.getOriginalOwnerUserId()); context.put("previousCollaboratorUserId", previous); context.put("collaboratorUserId", cycle.getCollaboratorUserId()); context.put("frozenDeptId", cycle.getFrozenDeptId()); context.put("agingPool.cycleId", cycle.getId()); context.put("agingPool.dueAt", cycle.getDueAt()); notifyPublisher.publish(scene, cycle.getLeadId(), key, operator, now, context); }
}
