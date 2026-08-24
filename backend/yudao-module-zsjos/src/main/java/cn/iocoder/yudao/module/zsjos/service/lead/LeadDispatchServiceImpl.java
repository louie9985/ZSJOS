package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.DesensitizedUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.LeadClaimPoolPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.LeadPendingRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.rule.LeadAssignmentRuleRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.rule.LeadAssignmentRuleUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
@Slf4j
public class LeadDispatchServiceImpl implements LeadDispatchService {

    private static final String QUERY_ALL_PERMISSION = "zsjos:lead:query-all";
    private static final int MAX_POOL_SCAN_ROUNDS = 3;

    @Resource private LeadMapper leadMapper;
    @Resource private LeadIntendedProductMapper productMapper;
    @Resource private LeadAttachmentMapper attachmentMapper;
    @Resource private LeadAssignmentHistoryMapper historyMapper;
    @Resource private LeadClaimDailyCounterMapper claimDailyCounterMapper;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private LeadAssignmentRuleMapper ruleMapper;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private LeadDispatchRedisRepository dispatchRedisRepository;
    @Resource private DictDataApi dictDataApi;
    @Resource private SecurityFrameworkService securityFrameworkService;
    @Resource private ApplicationEventPublisher applicationEventPublisher;
    @Resource private FileApi fileApi;
    @Resource private LeadLifecycleTaskService lifecycleTaskService;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;
    @Resource private LeadAgingPoolService agingPoolService;
    @Resource private AdvancedFilterService advancedFilterService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(LeadDO lead, Long specifiedSalesUserId, Long submitterUserId) {
        if (DISPATCH_SELF.equals(lead.getDispatchMode())) {
            LocalDateTime now = LocalDateTime.now();
            lead.setAssignmentStatus(ASSIGNMENT_OWNED); lead.setOwnerUserId(submitterUserId);
            lead.setOwnershipStartedAt(now); leadMapper.updateById(lead);
            LeadAssignmentHistoryDO history = addHistory(lead, ACTION_ACCEPT, submitterUserId,
                    submitterUserId, null, 1, null, "销售自拓直接归属", now);
            lead.setCurrentAssignmentHistoryId(history.getId());
            lead.setCurrentAssignmentFirstFollowUpDeadlineAt(lifecycleTaskService.createFirstFollowUpTask(
                    lead.getId(), submitterUserId, history.getId(), now, EVENT_LEAD_ACCEPTED, ASSIGNMENT_UNASSIGNED));
            leadMapper.updateById(lead); return;
        }
        if (DISPATCH_SPECIFIED.equals(lead.getDispatchMode())) {
            boolean allowed = assignmentService.getAssignableSalesUsers(submitterUserId).stream()
                    .anyMatch(user -> Objects.equals(user.getId(), specifiedSalesUserId));
            if (!allowed) throw exception(LEAD_SPECIFIED_SALES_REQUIRED);
            lead.setAssignmentStatus(ASSIGNMENT_PENDING);
            lead.setPendingAssigneeUserId(specifiedSalesUserId);
            lead.setPendingExpiresAt(null);
            leadMapper.updateById(lead);
            LeadAssignmentHistoryDO history = addHistory(lead, ACTION_DISPATCH, specifiedSalesUserId,
                    submitterUserId, null, null, null);
            lifecycleTaskService.createAssignmentTask(lead.getId(), specifiedSalesUserId,
                    history.getId(), null, lead.getDispatchMode());
            notifySales(specifiedSalesUserId, lead.getId(), "assigned");
            publishDispatchEvent(ASSIGNED, lead, specifiedSalesUserId, submitterUserId,
                    history, null);
            return;
        }
        LeadAssignmentRuleDO rule = requireRule();
        RuleConfig config = readConfig(rule.getConfigJson());
        lead.setAssignmentRuleSnapshot(JsonUtils.toJsonString(config));
        leadMapper.updateById(lead);
        dispatchNext(lead, rule, config, submitterUserId, null);
    }

    @Override public List<LeadAssignmentUserRespVO> getEligibleSalesUsers() { return assignmentService.getEligibleSalesUsers(); }
    @Override public List<LeadAssignmentUserRespVO> getAssignableSalesUsers(Long sourceUserId) { return assignmentService.getAssignableSalesUsers(sourceUserId); }

    @Override
    public void notifyActivation(LeadDO lead) {
        if (lead.getOwnerUserId() != null) {
            notifySales(lead.getOwnerUserId(), lead.getId(), "activated");
        }
    }

    private void dispatchNext(LeadDO lead, LeadAssignmentRuleDO rule, RuleConfig config,
                              Long operatorUserId, String priorAction) {
        Set<Long> tried = new HashSet<>(historyMapper.selectTriedSalesUserIds(lead.getId()));
        if (tried.size() >= config.maxAttempts()) {
            moveToPool(lead, operatorUserId, "无可继续尝试的启用销售");
            return;
        }
        Set<Long> eligible = assignmentService.getEligibleSalesUsers().stream()
                .map(LeadAssignmentUserRespVO::getId).collect(Collectors.toSet());
        try {
            long poolSize = dispatchRedisRepository.poolSize();
            if (poolSize == 0) {
                moveToPool(lead, operatorUserId, "当前没有页面在线的销售专员");
                return;
            }
            long scanBudget = poolSize * MAX_POOL_SCAN_ROUNDS;
            for (long scanned = 0; scanned < scanBudget; scanned++) {
                Long chosen = dispatchRedisRepository.rotateNext();
                if (chosen == null) {
                    continue;
                }
                if (!eligible.contains(chosen)) {
                    dispatchRedisRepository.removeFromPool(chosen);
                    continue;
                }
                if (!dispatchRedisRepository.isOnline(chosen)) {
                    dispatchRedisRepository.removeFromPool(chosen);
                    continue;
                }
                if (!dispatchRedisRepository.isAccepting(chosen) || tried.contains(chosen)
                        || leadMapper.existsPendingByUserId(chosen)) {
                    continue;
                }
                if (!dispatchRedisRepository.tryReserve(lead.getId(), chosen, config.acceptTimeoutSeconds())) {
                    continue;
                }
                try {
                    int attempt = tried.size() + 1;
                    LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(config.acceptTimeoutSeconds());
                    if (leadMapper.updateUnassignedToPending(lead.getId(), chosen, expiresAt, attempt) == 0) {
                        releaseReservation(lead.getId(), chosen);
                        return;
                    }
                    lead.setAssignmentStatus(ASSIGNMENT_PENDING);
                    lead.setPendingAssigneeUserId(chosen);
                    lead.setPendingExpiresAt(expiresAt);
                    lead.setAssignmentAttemptCount(attempt);
                    LeadAssignmentHistoryDO history = addHistory(lead, ACTION_DISPATCH, chosen, operatorUserId,
                            rule.getId(), attempt, expiresAt);
                    lifecycleTaskService.createAssignmentTask(lead.getId(), chosen, history.getId(),
                            expiresAt, lead.getDispatchMode());
                    notifySales(chosen, lead.getId(), priorAction == null ? "assigned" : "reassigned");
                    publishDispatchEvent(priorAction == null ? ASSIGNED : REASSIGNED, lead, chosen,
                            operatorUserId, history, priorAction);
                    return;
                } catch (RuntimeException ex) {
                    releaseReservation(lead.getId(), chosen);
                    throw ex;
                }
            }
            moveToPool(lead, operatorUserId, "轮询三圈后没有可接单销售");
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            log.error("[dispatchNext][leadId({}) Redis 轮询失败，客资保持未分配等待重试]", lead.getId(), ex);
        }
    }

    @Override
    public List<LeadPendingRespVO> getMyPending(Long userId) {
        return toPendingRespList(leadMapper.selectPendingByUserId(userId));
    }

    @Override
    public PageResult<LeadPendingRespVO> getClaimPoolPage(LeadClaimPoolPageReqVO reqVO, Long userId) {
        if (!securityFrameworkService.hasPermission(QUERY_ALL_PERMISSION)) {
            requireSalesUser(userId);
        }
        PageResult<LeadDO> page = leadMapper.selectPublicPoolPage(reqVO, reqVO.getKeyword(),
                advancedFilterService.matchLeadIds(reqVO.getAdvancedFilter()));
        return new PageResult<>(toPendingRespList(page.getList()), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "accept")
    public void accept(Long leadId, Long userId) {
        LeadDO lead = requireLead(leadId);
        if (leadMapper.updatePendingResult(leadId, userId, ASSIGNMENT_OWNED, userId) == 0) {
            throw exception(LEAD_ASSIGNMENT_ALREADY_HANDLED);
        }
        LocalDateTime acceptedAt = LocalDateTime.now();
        LeadAssignmentHistoryDO history = addHistory(lead, ACTION_ACCEPT, userId, userId,
                null, lead.getAssignmentAttemptCount(), null, null, acceptedAt);
        lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setOwnerUserId(userId);
        lead.setOwnershipStartedAt(acceptedAt);
        lead.setLastActivityAt(acceptedAt);
        lead.setRecycleSourceOwnerUserId(null);
        lead.setPendingAssigneeUserId(null);
        lead.setPendingExpiresAt(null);
        lead.setCurrentAssignmentHistoryId(history.getId());
        lead.setCurrentAssignmentFirstFollowUpAt(null);
        lead.setNextFollowUpAt(null);
        leadMapper.updateById(lead);
        lifecycleTaskService.completeAssignmentTask(leadId, userId, acceptedAt);
        lead.setCurrentAssignmentFirstFollowUpDeadlineAt(lifecycleTaskService.createFirstFollowUpTask(
                leadId, userId, history.getId(), acceptedAt, EVENT_LEAD_ACCEPTED, ASSIGNMENT_PENDING));
        leadMapper.updateById(lead);
        notifySales(userId, leadId, "accepted");
        releaseReservation(leadId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "reject")
    public void reject(Long leadId, Long userId) {
        LeadDO lead = requireLead(leadId);
        if (DISPATCH_SPECIFIED.equals(lead.getDispatchMode())) throw exception(LEAD_ASSIGNMENT_REJECT_FORBIDDEN);
        if (leadMapper.updatePendingResult(leadId, userId, ASSIGNMENT_UNASSIGNED, null) == 0) {
            throw exception(LEAD_ASSIGNMENT_ALREADY_HANDLED);
        }
        LocalDateTime rejectedAt = LocalDateTime.now();
        LeadAssignmentHistoryDO history = addHistory(lead, ACTION_REJECT, userId, userId, null,
                lead.getAssignmentAttemptCount(), null, null, rejectedAt);
        lifecycleTaskService.cancelAssignmentTask(leadId, userId, rejectedAt, "销售拒绝自动派单");
        notifySales(userId, leadId, "rejected");
        releaseReservation(leadId, userId);
        lead.setAssignmentStatus(ASSIGNMENT_UNASSIGNED);
        lead.setPendingAssigneeUserId(null);
        lead.setPendingExpiresAt(null);
        notifyEventPublisher.publish(REJECTED, leadId, "lead-rejected:" + history.getId(), userId,
                rejectedAt, eventContext(lead, userId, lead.getOwnerUserId(), null));
        dispatchNext(lead, requireRule(), snapshotConfig(lead), userId, ACTION_REJECT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "claim")
    public void claim(Long leadId, Long userId) {
        requireSalesUser(userId);
        RuleConfig config = readConfig(requireRule().getConfigJson());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LeadDO lead = requireLead(leadId);
        if (leadMapper.updatePublicPoolToOwned(leadId, userId) == 0) throw exception(LEAD_CLAIM_ALREADY_TAKEN);
        if (claimDailyCounterMapper.reserve(TenantContextHolder.getRequiredTenantId(), userId, today,
                config.dailyClaimLimit()) == 0) {
            throw exception(LEAD_CLAIM_DAILY_LIMIT_REACHED);
        }
        LocalDateTime claimedAt = LocalDateTime.now();
        LeadAssignmentHistoryDO history = addHistory(lead, ACTION_CLAIM, userId, userId,
                null, null, null, null, claimedAt);
        lifecycleTaskService.cancelFirstFollowUpTasks(leadId, claimedAt, "客资重新归属");
        lifecycleTaskService.cancelFollowUpReminders(leadId, claimedAt, "客资重新归属");
        lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setOwnerUserId(userId);
        lead.setOwnershipStartedAt(claimedAt);
        lead.setRecycleSourceOwnerUserId(null);
        lead.setCurrentAssignmentHistoryId(history.getId());
        lead.setCurrentAssignmentFirstFollowUpAt(null);
        lead.setNextFollowUpAt(null);
        leadMapper.updateById(lead);
        lead.setCurrentAssignmentFirstFollowUpDeadlineAt(lifecycleTaskService.createFirstFollowUpTask(
                leadId, userId, history.getId(), claimedAt, EVENT_LEAD_CLAIMED, ASSIGNMENT_PUBLIC_POOL));
        leadMapper.updateById(lead);
    }

    @Override
    public LeadAssignmentRuleRespVO getRule() {
        LeadAssignmentRuleDO rule = requireRule();
        RuleConfig config = readConfig(rule.getConfigJson());
        LeadAssignmentRuleRespVO result = new LeadAssignmentRuleRespVO();
        result.setId(rule.getId()); result.setCode(rule.getCode()); result.setName(rule.getName());
        result.setStrategyType(rule.getStrategyType()); result.setStatus(rule.getStatus());
        result.setAcceptTimeoutSeconds(config.acceptTimeoutSeconds()); result.setMaxAttempts(config.maxAttempts());
        result.setDailyClaimLimit(config.dailyClaimLimit());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(LeadAssignmentRuleUpdateReqVO reqVO) {
        LeadAssignmentRuleDO rule = requireRule();
        rule.setConfigJson(JsonUtils.toJsonString(new RuleConfig(reqVO.getAcceptTimeoutSeconds(),
                reqVO.getMaxAttempts(), reqVO.getDailyClaimLimit())));
        ruleMapper.updateById(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "admin-transfer")
    public void adminTransfer(Long leadId, Long salesUserId, Long operatorUserId) {
        adminTransfer(leadId, salesUserId, operatorUserId, "管理员转派");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "admin-transfer")
    public void adminTransfer(Long leadId, Long salesUserId, Long operatorUserId, String reason) {
        requireSalesUser(salesUserId);
        LeadDO lead = requireLead(leadId);
        if (STATUS_SUSPENDED.equals(lead.getStatus())
                || ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus())) {
            throw exception(LEAD_QUALIFICATION_DISPOSITION_INVALID);
        }
        doAdminTransfer(lead, salesUserId, operatorUserId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferAttemptResult tryAdminTransfer(Long leadId, Long expectedOwnerUserId, Long salesUserId,
                                                  Long operatorUserId, String reason) {
        if (assignmentService.getEligibleSalesUsers().stream().noneMatch(user -> salesUserId.equals(user.getId()))) {
            return TransferAttemptResult.invalidated("目标销售已不再有效");
        }
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) {
            return TransferAttemptResult.invalidated("客资已不存在");
        }
        if (STATUS_SUSPENDED.equals(lead.getStatus())
                || !ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                || !Objects.equals(lead.getOwnerUserId(), expectedOwnerUserId)) {
            return TransferAttemptResult.invalidated("客资状态或归属已变化");
        }
        doAdminTransfer(lead, salesUserId, operatorUserId, reason);
        return TransferAttemptResult.success();
    }

    private void doAdminTransfer(LeadDO lead, Long salesUserId, Long operatorUserId, String reason) {
        Long leadId = lead.getId();
        LocalDateTime transferredAt = LocalDateTime.now();
        agingPoolService.terminateForOwnerTransfer(leadId, salesUserId, operatorUserId, transferredAt);
        Long from = lead.getOwnerUserId() != null ? lead.getOwnerUserId() : lead.getPendingAssigneeUserId();
        Long pendingAssigneeUserId = lead.getPendingAssigneeUserId();
        String fromAssignmentStatus = lead.getAssignmentStatus();
        lead.setAssignmentStatus(ASSIGNMENT_OWNED); lead.setOwnerUserId(salesUserId);
        lead.setPendingAssigneeUserId(null); lead.setPendingExpiresAt(null);
        lead.setOwnershipStartedAt(transferredAt);
        lifecycleTaskService.cancelAssignmentTask(leadId, pendingAssigneeUserId,
                transferredAt, reason);
        lifecycleTaskService.cancelFirstFollowUpTasks(leadId, transferredAt, reason);
        lifecycleTaskService.cancelFollowUpReminders(leadId, transferredAt, reason);
        LeadAssignmentHistoryDO history = new LeadAssignmentHistoryDO();
        history.setLeadId(leadId); history.setActionType(ACTION_TRANSFER); history.setFromOwnerUserId(from);
        history.setToOwnerUserId(salesUserId); history.setOperatorUserId(operatorUserId);
        history.setReason(reason);
        history.setOccurredAt(transferredAt); historyMapper.insert(history);
        lead.setCurrentAssignmentHistoryId(history.getId());
        lead.setCurrentAssignmentFirstFollowUpAt(null);
        lead.setNextFollowUpAt(null);
        leadMapper.updateById(lead);
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(leadId);
        if (opportunity != null) {
            opportunity.setOwnerUserId(salesUserId);
            opportunityMapper.updateById(opportunity);
        }
        lead.setCurrentAssignmentFirstFollowUpDeadlineAt(lifecycleTaskService.createFirstFollowUpTask(
                leadId, salesUserId, history.getId(), transferredAt, EVENT_LEAD_TRANSFERRED, fromAssignmentStatus));
        leadMapper.updateById(lead);
        releaseReservation(leadId, pendingAssigneeUserId);
        if (pendingAssigneeUserId != null) {
            notifySales(pendingAssigneeUserId, leadId, "cancelled");
        }
        notifySales(salesUserId, leadId, "transferred");
        Map<String, Object> transferContext = eventContext(lead, null, salesUserId, null);
        transferContext.put("previousOwnerUserId", from);
        transferContext.put("newOwnerUserId", salesUserId);
        notifyEventPublisher.publish(TRANSFERRED, leadId, "lead-transferred:" + history.getId(),
                operatorUserId, transferredAt, transferContext);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int processExpired() {
        int count = 0;
        for (LeadDO lead : leadMapper.selectExpiredPending(LocalDateTime.now())) {
            Long candidate = lead.getPendingAssigneeUserId();
            if (candidate == null || DISPATCH_SPECIFIED.equals(lead.getDispatchMode())) continue;
            if (leadMapper.updatePendingResult(lead.getId(), candidate, ASSIGNMENT_UNASSIGNED, null) == 0) continue;
            LocalDateTime expiredAt = LocalDateTime.now();
            LeadAssignmentHistoryDO history = addHistory(lead, ACTION_TIMEOUT, candidate, 0L, null,
                    lead.getAssignmentAttemptCount(), null, null, expiredAt);
            lifecycleTaskService.cancelAssignmentTask(lead.getId(), candidate, expiredAt, "自动派单超时");
            notifySales(candidate, lead.getId(), "expired");
            releaseReservation(lead.getId(), candidate);
            lead.setAssignmentStatus(ASSIGNMENT_UNASSIGNED);
            lead.setPendingAssigneeUserId(null); lead.setPendingExpiresAt(null);
            notifyEventPublisher.publish(EXPIRED, lead.getId(), "lead-expired:" + history.getId(), 0L,
                    expiredAt, eventContext(lead, candidate, lead.getOwnerUserId(), "接单超时"));
            dispatchNext(lead, requireRule(), snapshotConfig(lead), 0L, ACTION_TIMEOUT);
            count++;
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int processUnassignedRetries() {
        int count = 0;
        for (LeadDO lead : leadMapper.selectRetryableUnassignedAuto()) {
            dispatchNext(lead, requireRule(), snapshotConfig(lead), 0L, null);
            if (!ASSIGNMENT_UNASSIGNED.equals(lead.getAssignmentStatus())) {
                count++;
            }
        }
        return count;
    }

    private void moveToPool(LeadDO lead, Long operatorUserId, String reason) {
        lead.setAssignmentStatus(ASSIGNMENT_PUBLIC_POOL);
        lead.setPendingAssigneeUserId(null); lead.setPendingExpiresAt(null);
        lead.setPublicPoolAt(LocalDateTime.now());
        leadMapper.updateById(lead);
        lifecycleTaskService.cancelAssignmentTask(lead.getId(), null, LocalDateTime.now(), reason);
        lifecycleTaskService.cancelFirstFollowUpTasks(lead.getId(), LocalDateTime.now(), reason);
        lifecycleTaskService.cancelFollowUpReminders(lead.getId(), LocalDateTime.now(), reason);
        LeadAssignmentHistoryDO history = addHistory(lead, ACTION_PUBLIC_POOL, null, operatorUserId,
                null, null, null, reason);
        notifyEventPublisher.publish(PUBLIC_POOL, lead.getId(), "lead-public-pool:" + history.getId(),
                operatorUserId, history.getOccurredAt(), eventContext(lead, null, lead.getOwnerUserId(), reason));
    }

    private List<LeadPendingRespVO> toPendingRespList(List<LeadDO> leads) {
        if (leads.isEmpty()) {
            return List.of();
        }
        List<Long> leadIds = leads.stream().map(LeadDO::getId).toList();
        Map<Long, List<LeadIntendedProductDO>> products = productMapper.selectListByLeadIds(leadIds).stream()
                .collect(Collectors.groupingBy(LeadIntendedProductDO::getLeadId));
        List<LeadAttachmentDO> attachmentList = attachmentMapper.selectListByLeadIds(leadIds);
        Map<Long, List<LeadAttachmentDO>> attachments = attachmentList.stream()
                .collect(Collectors.groupingBy(LeadAttachmentDO::getLeadId));
        Map<Long, LeadAssignmentHistoryDO> dispatchHistories = Optional.ofNullable(
                historyMapper.selectLatestDispatchByLeadIds(leadIds)).orElse(Map.of());
        Map<Long, String> attachmentUrls = resolveAttachmentUrls(attachmentList);
        Map<String, String> channelLabels = dictLabels(DICT_SOURCE_CHANNEL);
        Map<String, String> categoryLabels = dictLabels(DICT_CATEGORY);
        return leads.stream().map(lead -> toPendingResp(lead,
                products.getOrDefault(lead.getId(), List.of()),
                attachments.getOrDefault(lead.getId(), List.of()), attachmentUrls,
                channelLabels, categoryLabels, dispatchHistories.get(lead.getId()))).toList();
    }

    private LeadPendingRespVO toPendingResp(LeadDO lead, List<LeadIntendedProductDO> products,
                                             List<LeadAttachmentDO> attachments,
                                             Map<Long, String> attachmentUrls,
                                             Map<String, String> channelLabels,
                                             Map<String, String> categoryLabels,
                                             LeadAssignmentHistoryDO dispatchHistory) {
        LeadPendingRespVO result = new LeadPendingRespVO();
        result.setId(lead.getId()); result.setLeadNo(lead.getLeadNo()); result.setDispatchMode(lead.getDispatchMode());
        result.setMaskedName(DesensitizedUtil.chineseName(lead.getSubmittedName()));
        result.setMaskedMobile(DesensitizedUtil.mobilePhone(lead.getSubmittedMobile()));
        result.setMaskedWechatId(maskWechat(lead.getSubmittedWechatId()));
        result.setProvinceName(lead.getProvinceName()); result.setCityName(lead.getCityName());
        result.setIntendedProducts(products.stream().sorted(Comparator.comparing(LeadIntendedProductDO::getSort))
                .map(LeadIntendedProductDO::getProductNameSnapshot).toList());
        result.setPrimaryIntendedProduct(products.stream().filter(item -> Boolean.TRUE.equals(item.getIsPrimary()))
                .map(LeadIntendedProductDO::getProductNameSnapshot).findFirst().orElse(null));
        result.setSourceChannel(lead.getSourceChannelId());
        result.setSourceChannelLabel(channelLabels.get(lead.getSourceChannelId()));
        result.setLeadCategory(lead.getLeadCategory());
        result.setLeadCategoryLabel(lead.getLeadCategoryLabelSnapshot() != null
                ? lead.getLeadCategoryLabelSnapshot() : categoryLabels.get(lead.getLeadCategory()));
        result.setRemark(lead.getRemark());
        result.setAttachmentUrls(attachments.stream()
                .sorted(Comparator.comparing(LeadAttachmentDO::getSort))
                .map(attachment -> {
                    String signedUrl = attachment.getId() == null ? null : attachmentUrls.get(attachment.getId());
                    return signedUrl != null ? signedUrl : attachment.getFileUrl();
                }).toList());
        result.setSubmittedAt(lead.getSubmittedAt()); result.setExpiresAt(lead.getPendingExpiresAt());
        result.setRemainingSeconds(lead.getPendingExpiresAt() == null ? null
                : Math.max(0L, Duration.between(LocalDateTime.now(), lead.getPendingExpiresAt()).getSeconds()));
        result.setRejectable(DISPATCH_AUTO.equals(lead.getDispatchMode()));
        result.setDeferrable(DISPATCH_SPECIFIED.equals(lead.getDispatchMode()));
        result.setAssignmentHistoryId(dispatchHistory == null ? null : dispatchHistory.getId());
        return result;
    }

    private Map<Long, String> resolveAttachmentUrls(List<LeadAttachmentDO> attachments) {
        List<Long> fileIds = attachments.stream().map(LeadAttachmentDO::getInfraFileId)
                .filter(Objects::nonNull).distinct().toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> urlsByFileId = fileApi.presignGetUrls(fileIds, ATTACHMENT_URL_EXPIRATION_SECONDS);
        return attachments.stream().filter(attachment -> attachment.getInfraFileId() != null)
                .collect(Collectors.toMap(LeadAttachmentDO::getId,
                        attachment -> urlsByFileId.get(attachment.getInfraFileId())));
    }

    private void requireSalesUser(Long userId) {
        if (assignmentService.getEligibleSalesUsers().stream().noneMatch(user -> userId.equals(user.getId()))) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
    }

    private LeadDO requireLead(Long id) {
        LeadDO lead = leadMapper.selectById(id);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        return lead;
    }

    private LeadAssignmentRuleDO requireRule() {
        LeadAssignmentRuleDO rule = ruleMapper.selectByCode(RULE_DEFAULT);
        if (rule == null) throw exception(LEAD_RULE_NOT_EXISTS);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(rule.getStatus())
                || !STRATEGY_GLOBAL_ROUND_ROBIN.equals(rule.getStrategyType())) throw exception(LEAD_RULE_INVALID);
        return rule;
    }

    private RuleConfig snapshotConfig(LeadDO lead) {
        RuleConfig config = readConfig(lead.getAssignmentRuleSnapshot());
        return config != null ? config : readConfig(requireRule().getConfigJson());
    }

    private RuleConfig readConfig(String json) {
        RuleConfig config = JsonUtils.parseObject(json, RuleConfig.class);
        if (config == null || config.acceptTimeoutSeconds() == null || config.acceptTimeoutSeconds() < 10
                || config.acceptTimeoutSeconds() > 3600 || config.maxAttempts() == null
                || config.maxAttempts() < 1 || config.maxAttempts() > 20) throw exception(LEAD_RULE_INVALID);
        return new RuleConfig(config.acceptTimeoutSeconds(), config.maxAttempts(),
                config.dailyClaimLimit() == null ? 5 : config.dailyClaimLimit());
    }

    private LeadAssignmentHistoryDO addHistory(LeadDO lead, String action, Long candidate, Long operator,
                                               Long ruleId, Integer attempt, LocalDateTime expiresAt) {
        return addHistory(lead, action, candidate, operator, ruleId, attempt, expiresAt, null);
    }

    private LeadAssignmentHistoryDO addHistory(LeadDO lead, String action, Long candidate, Long operator,
                                               Long ruleId, Integer attempt, LocalDateTime expiresAt, String reason) {
        return addHistory(lead, action, candidate, operator, ruleId, attempt, expiresAt, reason, LocalDateTime.now());
    }

    private LeadAssignmentHistoryDO addHistory(LeadDO lead, String action, Long candidate, Long operator,
                                               Long ruleId, Integer attempt, LocalDateTime expiresAt,
                                               String reason, LocalDateTime occurredAt) {
        LeadAssignmentHistoryDO history = new LeadAssignmentHistoryDO();
        history.setLeadId(lead.getId()); history.setActionType(action); history.setCandidateUserId(candidate);
        history.setToOwnerUserId(ACTION_ACCEPT.equals(action) || ACTION_CLAIM.equals(action) ? candidate : null);
        history.setOperatorUserId(operator == null ? 0L : operator); history.setReason(reason);
        history.setOccurredAt(occurredAt); history.setAssignmentRuleId(ruleId);
        history.setAttemptNo(attempt); history.setExpiresAt(expiresAt);
        if (!ACTION_DISPATCH.equals(action)) history.setResponseAt(occurredAt);
        historyMapper.insert(history);
        return history;
    }

    private void notifySales(Long userId, Long leadId, String eventType) {
        if (userId != null) {
            applicationEventPublisher.publishEvent(new LeadAssignmentRealtimeEvent(userId, leadId, eventType));
        }
    }

    private void publishDispatchEvent(String scene, LeadDO lead, Long salesUserId, Long operatorUserId,
                                      LeadAssignmentHistoryDO history, String reason) {
        if (ASSIGNED.equals(scene) || REASSIGNED.equals(scene)) return;
        Map<String, Object> context = eventContext(lead, salesUserId, lead.getOwnerUserId(), reason);
        context.put("assignment.attempt", history.getAttemptNo());
        notifyEventPublisher.publish(scene, lead.getId(), "lead-dispatch:" + history.getId(), operatorUserId,
                history.getOccurredAt(), context);
    }

    private Map<String, Object> eventContext(LeadDO lead, Long pendingSalesUserId, Long ownerUserId, String reason) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("submitterUserId", lead.getSourceUserId());
        context.put("pendingSalesUserId", pendingSalesUserId);
        context.put("ownerUserId", ownerUserId);
        context.put("assignment.reason", reason);
        return context;
    }

    private void releaseReservation(Long leadId, Long saleId) {
        try {
            dispatchRedisRepository.release(leadId, saleId);
        } catch (DataAccessException ex) {
            log.warn("[releaseReservation][leadId({}) saleId({}) Redis 清理失败，等待 TTL 回收]",
                    leadId, saleId, ex);
        }
    }

    private Map<String, String> dictLabels(String type) {
        return dictDataApi.getDictDataList(type).stream().collect(Collectors.toMap(
                DictDataRespDTO::getValue, DictDataRespDTO::getLabel, (first, ignored) -> first, LinkedHashMap::new));
    }

    private static String maskWechat(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.length() <= 2) return "*".repeat(value.length());
        return value.charAt(0) + "*".repeat(Math.min(6, value.length() - 2)) + value.substring(value.length() - 1);
    }

    public record RuleConfig(Integer acceptTimeoutSeconds, Integer maxAttempts, Integer dailyClaimLimit) {}
}
