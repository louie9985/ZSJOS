package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.*;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterConfigService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterQuery;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadLifecycleTaskService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class SalesOrderServiceImpl implements SalesOrderService {
    private static final Set<String> VOUCHER_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private static final long MAX_VOUCHER_SIZE = 10L * 1024 * 1024;

    @Resource private SalesOrderMapper orderMapper;
    @Resource private SalesOrderItemMapper itemMapper;
    @Resource private SalesOrderApprovalRoundMapper roundMapper;
    @Resource private SalesOrderApprovalConfigMapper salesOrderApprovalConfigMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadAppealMapper leadAppealMapper;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private ZsjosProductSkuService skuService;
    @Resource private SalesOrderObjectPermissionService permissionService;
    @Resource private FileApi fileApi;
    @Resource private AreaApi areaApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private LeadInboxFilterConfigService inboxFilterConfigService;
    @Resource private LeadLifecycleTaskService lifecycleTaskService;
    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;
    @Resource private DeptApi deptApi;
    @Resource private LeadAgingPoolService agingPoolService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "enter-deal")
    public Long createAndSubmit(Long leadId, Long userId, SalesOrderSubmitReqVO reqVO) {
        SalesOrderDO duplicate = orderMapper.selectByIdempotencyKey(reqVO.getIdempotencyKey());
        if (duplicate != null) {
            if (Objects.equals(duplicate.getLeadId(), leadId) && Objects.equals(duplicate.getSubmitterUserId(), userId)) return duplicate.getId();
            throw exception(SALES_ORDER_IDEMPOTENCY_CONFLICT);
        }
        LeadDO lead = requireEligibleLead(leadId, userId);
        if (orderMapper.selectActiveByLeadId(leadId, ACTIVE_ORDER_STATUSES) != null) throw exception(SALES_ORDER_ACTIVE_DUPLICATE);
        OpportunityDO opportunity = requireEligibleOpportunity(leadId);
        ValidatedSubmission validated = validateSubmission(reqVO, userId);
        LocalDateTime now = LocalDateTime.now();
        SalesOrderDO order = new SalesOrderDO();
        order.setOrderNo(generateOrderNo());
        order.setLeadId(leadId); order.setOpportunityId(opportunity.getId()); order.setPersonId(lead.getPersonId());
        order.setOrderType(ORDER_TYPE_DIRECT_SALE); order.setStatus(STATUS_PENDING_APPROVAL);
        order.setSubmitterUserId(userId); order.setSubmitterCenterType(SUBMITTER_CENTER_SALES);
        applySubmission(order, reqVO, validated, now);
        order.setSubmissionIdempotencyKey(reqVO.getIdempotencyKey()); order.setVersion(0);
        orderMapper.insert(order);
        insertItems(order.getId(), validated.items());
        startRound(order, opportunity, userId, reqVO.getIdempotencyKey(), validated, 1, now);
        agingPoolService.markDealPending(leadId, userId, now);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "revise")
    public void reviseAndResubmit(Long orderId, Long userId, SalesOrderSubmitReqVO reqVO) {
        SalesOrderApprovalRoundDO duplicate = roundMapper.selectByIdempotencyKey(reqVO.getIdempotencyKey());
        if (duplicate != null) {
            if (Objects.equals(duplicate.getOrderId(), orderId)) return;
            throw exception(SALES_ORDER_IDEMPOTENCY_CONFLICT);
        }
        SalesOrderDO order = requireOrderForUpdate(orderId);
        if (!STATUS_REVISION_REQUIRED.equals(order.getStatus())) throw exception(SALES_ORDER_STATE_INVALID);
        LeadDO lead = requireEligibleLead(order.getLeadId(), userId);
        OpportunityDO opportunity = requireEligibleOpportunity(lead.getId());
        ValidatedSubmission validated = validateSubmission(reqVO, userId);
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(STATUS_PENDING_APPROVAL);
        applySubmission(order, reqVO, validated, now);
        order.setEffectiveAt(null);
        orderMapper.updateById(order);
        itemMapper.deleteByOrderId(orderId);
        insertItems(orderId, validated.items());
        SalesOrderApprovalRoundDO latest = roundMapper.selectLatestByOrderId(orderId);
        startRound(order, opportunity, userId, reqVO.getIdempotencyKey(), validated,
                latest == null ? 1 : latest.getRoundNo() + 1, now);
        agingPoolService.markDealPending(lead.getId(), userId, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "continue-revise")
    public Long continueAndSubmit(Long orderId, Long userId, SalesOrderSubmitReqVO reqVO) {
        SalesOrderDO duplicate = orderMapper.selectByIdempotencyKey(reqVO.getIdempotencyKey());
        if (duplicate != null) {
            if (Objects.equals(duplicate.getSupersedesOrderId(), orderId)
                    && Objects.equals(duplicate.getSubmitterUserId(), userId)) return duplicate.getId();
            throw exception(SALES_ORDER_IDEMPOTENCY_CONFLICT);
        }
        SalesOrderDO original = requireOrderForUpdate(orderId);
        if (!STATUS_REVISION_REQUIRED.equals(original.getStatus()) || original.getSupersededByOrderId() != null
                || orderMapper.selectBySupersedesOrderId(orderId) != null) {
            throw exception(SALES_ORDER_CONTINUATION_CONFLICT);
        }
        LeadDO lead = requireEligibleLead(original.getLeadId(), userId);
        OpportunityDO opportunity = requireEligibleOpportunity(lead.getId());
        ValidatedSubmission validated = validateSubmission(reqVO, userId);
        LocalDateTime now = LocalDateTime.now();
        SalesOrderDO continuation = new SalesOrderDO();
        continuation.setOrderNo(generateOrderNo()); continuation.setLeadId(lead.getId());
        continuation.setOpportunityId(opportunity.getId()); continuation.setPersonId(lead.getPersonId());
        continuation.setOrderType(ORDER_TYPE_CONTINUATION); continuation.setStatus(STATUS_PENDING_APPROVAL);
        continuation.setSubmitterUserId(userId); continuation.setSubmitterCenterType(SUBMITTER_CENTER_SALES);
        continuation.setSupersedesOrderId(original.getId());
        applySubmission(continuation, reqVO, validated, now);
        continuation.setSubmissionIdempotencyKey(reqVO.getIdempotencyKey()); continuation.setVersion(0);
        original.setStatus(STATUS_SUPERSEDED);
        orderMapper.updateById(original);
        orderMapper.insert(continuation);
        original.setSupersededByOrderId(continuation.getId()); orderMapper.updateById(original);
        insertItems(continuation.getId(), validated.items());
        startRound(continuation, opportunity, userId, reqVO.getIdempotencyKey(), validated, 1, now);
        agingPoolService.markDealPending(lead.getId(), userId, now);
        return continuation.getId();
    }

    @Override
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "read")
    public SalesOrderRespVO get(Long orderId, Long userId) {
        SalesOrderDO order = orderMapper.selectById(orderId);
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        return convert(order, roundMapper.selectLatestByOrderId(orderId), null, userId);
    }

    @Override
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "read-own")
    public SalesOrderRespVO getOwn(Long orderId, Long userId) {
        SalesOrderDO order = orderMapper.selectById(orderId);
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        return convert(order, roundMapper.selectLatestByOrderId(orderId), null, userId);
    }

    @Override
    public PageResult<SalesOrderListItemRespVO> getMyPage(SalesOrderMyPageReqVO reqVO, Long userId) {
        PageResult<SalesOrderDO> page = orderMapper.selectMyPage(userId, reqVO);
        Map<Long, SalesOrderApprovalRoundDO> rounds = getCurrentRounds(page.getList());
        return new PageResult<>(page.getList().stream()
                .map(order -> convertListItem(order, rounds.get(order.getCurrentApprovalRoundId()), null)).toList(), page.getTotal());
    }

    @Override
    public SalesOrderStatusCountsRespVO getMyStatusCounts(Long userId) {
        return new SalesOrderStatusCountsRespVO(orderMapper.selectMyCount(userId, null),
                orderMapper.selectMyCount(userId, STATUS_PENDING_APPROVAL),
                orderMapper.selectMyCount(userId, STATUS_REVISION_REQUIRED),
                orderMapper.selectMyCount(userId, STATUS_EFFECTIVE));
    }

    @Override
    public PageResult<SalesOrderListItemRespVO> getInboxPage(SalesOrderPageReqVO reqVO, Long userId) {
        if (!permissionService.isApprovalPoolMember(userId)) throw exception(SALES_ORDER_PERMISSION_DENIED);
        LeadInboxFilterConfigVO config = inboxFilterConfigService.getPublishedConfig(INBOX_AUDIENCE_REVIEWER);
        LeadInboxFilterQuery filter;
        if (reqVO.getGroupKey() == null && reqVO.getHandled() != null) {
            filter = new LeadInboxFilterQuery(Set.of(), Set.of(), false, Map.of(
                    INBOX_FILTER_FIELD_HANDLED, Set.of(Boolean.TRUE.equals(reqVO.getHandled()) ? "done" : "todo")));
        } else {
            String groupKey = reqVO.getGroupKey() != null ? reqVO.getGroupKey() : config.getGroups().stream()
                    .filter(group -> Boolean.TRUE.equals(group.getEnabled())).findFirst()
                    .orElseThrow(() -> exception(LEAD_INBOX_FILTER_INVALID)).getKey();
            filter = inboxFilterConfigService.resolveQuery(config, groupKey, reqVO.getOptionKey());
        }
        List<String> processIds = searchProcessIds(reqVO.getKeyword());
        if (processIds != null && processIds.isEmpty()) return PageResult.empty();
        List<BpmTaskRespDTO> tasks = loadApprovalTasks(userId, reqVO, filter, processIds);
        List<SalesOrderListItemRespVO> result = new ArrayList<>();
        for (BpmTaskRespDTO task : tasks) {
            Long orderId = parseOrderId(task.getBusinessKey());
            SalesOrderDO order = orderId == null ? null : orderMapper.selectById(orderId);
            if (order == null || !permissionService.canRead(order, userId)) continue;
            result.add(convertListItem(order, roundMapper.selectByProcessInstanceId(task.getProcessInstanceId()), task));
        }
        long total = countApprovalTasks(userId, filter, processIds);
        return new PageResult<>(result, total);
    }

    @Override
    public SalesOrderApprovalFilterProfileRespVO getApprovalFilterProfile(Long userId) {
        if (!permissionService.isApprovalPoolMember(userId)) throw exception(SALES_ORDER_PERMISSION_DENIED);
        LeadInboxFilterConfigVO config = inboxFilterConfigService.getPublishedConfig(INBOX_AUDIENCE_REVIEWER);
        List<SalesOrderApprovalFilterProfileRespVO.GroupVO> groups = config.getGroups().stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .map(group -> {
                    LeadInboxFilterQuery groupQuery = inboxFilterConfigService.resolveQuery(config, group.getKey(), "all");
                    List<SalesOrderApprovalFilterProfileRespVO.OptionVO> options = group.getOptions().stream()
                            .filter(option -> Boolean.TRUE.equals(option.getEnabled()))
                            .map(option -> new SalesOrderApprovalFilterProfileRespVO.OptionVO(option.getKey(), option.getLabel(),
                                    countApprovalTasks(userId, inboxFilterConfigService.resolveQuery(config, group.getKey(), option.getKey()), null)))
                            .toList();
                    List<SalesOrderApprovalFilterProfileRespVO.SectionVO> sections = options.isEmpty() ? List.of()
                            : List.of(new SalesOrderApprovalFilterProfileRespVO.SectionVO(
                                    "approval_stage", group.getSectionLabel() == null ? "审批环节" : group.getSectionLabel(), options));
                    return new SalesOrderApprovalFilterProfileRespVO.GroupVO(group.getKey(), group.getLabel(),
                            countApprovalTasks(userId, groupQuery, null), sections);
                }).toList();
        return new SalesOrderApprovalFilterProfileRespVO(groups);
    }

    private List<BpmTaskRespDTO> loadApprovalTasks(Long userId, SalesOrderPageReqVO reqVO,
                                                   LeadInboxFilterQuery filter, List<String> processIds) {
        Set<String> handled = filter.values(INBOX_FILTER_FIELD_HANDLED);
        Set<String> taskKeys = filter.values(INBOX_FILTER_FIELD_TASK_DEFINITION_KEY);
        List<String> handledValues = handled.isEmpty() ? List.of("todo", "done") : new ArrayList<>(handled);
        List<String> taskValues = taskKeys.isEmpty() ? Collections.singletonList(null) : new ArrayList<>(taskKeys);
        List<BpmTaskRespDTO> tasks = new ArrayList<>();
        for (String handledValue : handledValues) {
            for (String taskKey : taskValues) {
                BpmTaskPageReqDTO taskReq = new BpmTaskPageReqDTO();
                taskReq.setPageNo(1);
                taskReq.setPageSize(Math.max(reqVO.getPageNo() * reqVO.getPageSize(), reqVO.getPageSize()));
                taskReq.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
                taskReq.setTaskDefinitionKey(taskKey);
                taskReq.setProcessInstanceIds(processIds);
                PageResult<BpmTaskRespDTO> page = "done".equals(handledValue)
                        ? processTaskApi.getDoneTaskPage(userId, taskReq) : processTaskApi.getTodoTaskPage(userId, taskReq);
                tasks.addAll(page.getList());
            }
        }
        tasks.sort(Comparator.comparing(BpmTaskRespDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), tasks.size());
        int to = Math.min(from + reqVO.getPageSize(), tasks.size());
        return tasks.subList(from, to);
    }

    private long countApprovalTasks(Long userId, LeadInboxFilterQuery filter, List<String> processIds) {
        SalesOrderPageReqVO countReq = new SalesOrderPageReqVO();
        countReq.setPageNo(1); countReq.setPageSize(1);
        countReq.setGroupKey("all"); countReq.setOptionKey("all");
        Set<String> handled = filter.values(INBOX_FILTER_FIELD_HANDLED);
        Set<String> taskKeys = filter.values(INBOX_FILTER_FIELD_TASK_DEFINITION_KEY);
        List<String> handledValues = handled.isEmpty() ? List.of("todo", "done") : new ArrayList<>(handled);
        List<String> taskValues = taskKeys.isEmpty() ? Collections.singletonList(null) : new ArrayList<>(taskKeys);
        long total = 0;
        for (String handledValue : handledValues) for (String taskKey : taskValues) {
            BpmTaskPageReqDTO taskReq = new BpmTaskPageReqDTO(); taskReq.setPageNo(1); taskReq.setPageSize(1);
            taskReq.setProcessDefinitionKey(PROCESS_DEFINITION_KEY); taskReq.setTaskDefinitionKey(taskKey);
            taskReq.setProcessInstanceIds(processIds);
            total += ("done".equals(handledValue) ? processTaskApi.getDoneTaskPage(userId, taskReq)
                    : processTaskApi.getTodoTaskPage(userId, taskReq)).getTotal();
        }
        return total;
    }

    private List<String> searchProcessIds(String keyword) {
        if (StrUtil.isBlank(keyword)) return null;
        return roundMapper.selectProcessInstanceIdsByKeyword(TenantContextHolder.getTenantId(), keyword.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "review")
    public void approve(Long orderId, Long userId, SalesOrderDecisionReqVO reqVO) {
        decide(orderId, userId, reqVO, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "review")
    public void reject(Long orderId, Long userId, SalesOrderDecisionReqVO reqVO) {
        decide(orderId, userId, reqVO, false);
    }

    private void decide(Long orderId, Long userId, SalesOrderDecisionReqVO reqVO, boolean approve) {
        SalesOrderDO order = requireOrderForUpdate(orderId);
        if (!STATUS_PENDING_APPROVAL.equals(order.getStatus())) throw exception(SALES_ORDER_ALREADY_HANDLED);
        SalesOrderApprovalRoundDO round = roundMapper.selectLatestByOrderId(orderId);
        BpmTaskRespDTO task;
        try {
            task = processTaskApi.getTodoTask(userId, reqVO.getTaskId());
        } catch (RuntimeException ex) {
            throw exception(SALES_ORDER_ALREADY_HANDLED);
        }
        if (round == null || !Objects.equals(task.getProcessInstanceId(), round.getProcessInstanceId())
                || !Objects.equals(task.getBusinessKey(), BUSINESS_KEY_PREFIX + orderId)
                || !Set.of(TASK_REGISTRATION, TASK_FINANCE).contains(task.getTaskDefinitionKey())) {
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        }
        BpmTaskDecisionReqDTO decision = new BpmTaskDecisionReqDTO();
        decision.setTaskId(reqVO.getTaskId()); decision.setReason(reqVO.getReason().trim());
        if (approve) processTaskApi.approveTask(userId, decision); else processTaskApi.rejectTask(userId, decision);
    }

    @Override
    public LeadAttachmentUploadRespVO uploadVoucher(Long userId, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_VOUCHER_SIZE) throw exception(SALES_ORDER_ATTACHMENT_INVALID);
        byte[] content = file.getBytes();
        String type = FileTypeUtils.getMineType(content, file.getOriginalFilename());
        if (!VOUCHER_TYPES.contains(type)) throw exception(SALES_ORDER_ATTACHMENT_INVALID);
        FileInfoRespDTO saved = fileApi.createFileInfo(content, file.getOriginalFilename(), "zsjos/sales-order-voucher", type);
        return new LeadAttachmentUploadRespVO(saved.getId(), saved.getUrl(), saved.getName(), saved.getType(), saved.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(String processInstanceId, Integer processStatus, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) return;
        SalesOrderApprovalRoundDO round = roundMapper.selectByProcessInstanceId(processInstanceId);
        if (round == null || !ROUND_PENDING.equals(round.getStatus())) return;
        SalesOrderDO order = requireOrderForUpdate(round.getOrderId());
        if (!STATUS_PENDING_APPROVAL.equals(order.getStatus()) || !Objects.equals(order.getCurrentApprovalRoundId(), round.getId())) return;
        LocalDateTime now = LocalDateTime.now();
        OpportunityDO opportunity = opportunityMapper.selectById(order.getOpportunityId());
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            round.setStatus(ROUND_APPROVED); order.setStatus(STATUS_EFFECTIVE); order.setEffectiveAt(now);
            if (opportunity != null) {
                opportunity.setStatus(OPPORTUNITY_STATUS_WON); opportunity.setWonAt(now);
                opportunity.setNextFollowUpAt(null); opportunityMapper.updateById(opportunity);
            }
            LeadDO lead = leadMapper.selectById(order.getLeadId());
            if (lead != null) {
                lead.setNextFollowUpAt(null);
                leadMapper.updateById(lead);
            }
            agingPoolService.completeConversion(order.getLeadId(), order.getSubmitterUserId(), now);
            lifecycleTaskService.cancelFirstFollowUpTasks(order.getLeadId(), now, "成交订单已生效");
            lifecycleTaskService.cancelFollowUpReminders(order.getLeadId(), now, "成交订单已生效");
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)) {
            round.setStatus(ROUND_REJECTED); round.setDecisionReason(StrUtil.trim(reason)); order.setStatus(STATUS_REVISION_REQUIRED);
            if (opportunity != null) { opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING); opportunityMapper.updateById(opportunity); }
            agingPoolService.handleOrderRejected(order.getLeadId(), now);
        } else {
            round.setStatus(ROUND_REJECTED); round.setDecisionReason(StrUtil.trim(reason)); order.setStatus(STATUS_REVISION_REQUIRED);
            if (opportunity != null) { opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING); opportunityMapper.updateById(opportunity); }
            agingPoolService.handleOrderRejected(order.getLeadId(), now);
        }
        round.setCompletedAt(now); roundMapper.updateById(round); orderMapper.updateById(order);
        publishOrderNotification(BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)
                        ? EFFECTIVE : BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus) ? REJECTED : CANCELLED,
                order, "sales-order-result:" + round.getId(), List.of(), reason, now);
    }

    private void startRound(SalesOrderDO order, OpportunityDO opportunity, Long userId, String idempotencyKey,
                            ValidatedSubmission validated, int roundNo, LocalDateTime now) {
        SalesOrderApprovalConfigDO config = salesOrderApprovalConfigMapper.selectCurrent();
        if (config == null) throw exception(SALES_ORDER_APPROVAL_CONFIG_INVALID);
        List<Long> registrationUsers = new ArrayList<>(permissionService.enabledUsers(config.getRegistrationDeptId()));
        List<Long> financeUsers = new ArrayList<>(permissionService.enabledUsers(config.getFinanceDeptId()));
        if (registrationUsers.isEmpty() || financeUsers.isEmpty()) throw exception(SALES_ORDER_APPROVAL_CONFIG_INVALID);
        BpmProcessInstanceCreateReqDTO processReq = new BpmProcessInstanceCreateReqDTO();
        processReq.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
        processReq.setBusinessKey(BUSINESS_KEY_PREFIX + order.getId());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("orderId", order.getId()); variables.put("leadId", order.getLeadId()); variables.put("roundNo", roundNo);
        variables.put("registrationUsers", registrationUsers); variables.put("financeUsers", financeUsers);
        processReq.setVariables(variables);
        processReq.setStartUserSelectAssignees(Map.of(TASK_REGISTRATION, registrationUsers, TASK_FINANCE, financeUsers));
        String processInstanceId;
        try {
            processInstanceId = processInstanceApi.createProcessInstance(userId, processReq);
        } catch (RuntimeException ex) {
            throw exception(SALES_ORDER_PROCESS_UNAVAILABLE);
        }
        SalesOrderApprovalRoundDO round = new SalesOrderApprovalRoundDO();
        round.setOrderId(order.getId()); round.setRoundNo(roundNo); round.setStatus(ROUND_PENDING);
        round.setOrderSnapshot(buildOrderSnapshot(order, validated)); round.setProcessInstanceId(processInstanceId);
        round.setProcessDefinitionKey(PROCESS_DEFINITION_KEY); round.setSubmittedByUserId(userId);
        round.setSubmittedAt(now); round.setSubmissionIdempotencyKey(idempotencyKey); roundMapper.insert(round);
        order.setCurrentApprovalRoundId(round.getId()); order.setSubmittedAt(now); orderMapper.updateById(order);
        opportunity.setStatus(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL); opportunityMapper.updateById(opportunity);
        publishOrderNotification(SUBMITTED, order, "sales-order-submitted:" + round.getId(),
                java.util.stream.Stream.concat(registrationUsers.stream(), financeUsers.stream()).distinct().toList(),
                null, now);
    }

    private void publishOrderNotification(String sceneCode, SalesOrderDO order, String sourceEventKey,
                                          List<Long> reviewers, String reason, LocalDateTime occurredAt) {
        SalesOrderApprovalConfigDO config = salesOrderApprovalConfigMapper.selectCurrent();
        List<String> departments = config == null ? List.of() : java.util.stream.Stream.of(
                        config.getRegistrationDeptId(), config.getFinanceDeptId())
                .filter(Objects::nonNull).map(deptApi::getDept)
                .filter(Objects::nonNull).map(item -> item.getName()).filter(Objects::nonNull).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewerUserIds", reviewers); payload.put("submitterUserId", order.getSubmitterUserId());
        payload.put("approvalDepartments", String.join("、", departments));
        payload.put("decisionReason", StrUtil.blankToDefault(reason, ""));
        notifyBusinessEventApi.publish(NotifyBusinessEvent.builder()
                .tenantId(TenantContextHolder.getRequiredTenantId()).sceneCode(sceneCode).sourceEventKey(sourceEventKey)
                .bizType("sales_order").bizId(order.getId()).operatorUserId(null).occurredAt(occurredAt)
                .payload(payload).build());
    }

    private LeadDO requireEligibleLead(Long leadId, Long userId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!Objects.equals(agingPoolService.resolveEffectiveSalesUserId(leadId, lead.getOwnerUserId()), userId) || lead.getSuspendedAt() != null
                || !Set.of(STATUS_VALID, "converted").contains(lead.getStatus())) throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        LeadAppealDO latestAppeal = leadAppealMapper.selectLatestByLeadId(leadId);
        if (latestAppeal != null && Set.of(APPEAL_STATUS_SALES_MANAGER_REVIEWING, APPEAL_STATUS_QUALITY_REVIEWING,
                APPEAL_STATUS_CHAIRMAN_REVIEWING).contains(latestAppeal.getStatus())) throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        return lead;
    }

    private OpportunityDO requireEligibleOpportunity(Long leadId) {
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(leadId);
        if (opportunity == null || !Set.of(OPPORTUNITY_STATUS_OPEN, OPPORTUNITY_STATUS_FOLLOWING).contains(opportunity.getStatus())) {
            throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        }
        return opportunity;
    }

    private SalesOrderDO requireOrderForUpdate(Long orderId) {
        SalesOrderDO order = orderMapper.selectByIdForUpdate(orderId, TenantContextHolder.getRequiredTenantId());
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        return order;
    }

    private ValidatedSubmission validateSubmission(SalesOrderSubmitReqVO req, Long userId) {
        if (StrUtil.isBlank(req.getStudentMobile()) && StrUtil.isBlank(req.getStudentWechatId())) throw exception(SALES_ORDER_CONTACT_REQUIRED);
        RegionSnapshot region = validateRegion(req.getProvinceCode(), req.getCityCode());
        req.setProvinceName(region.provinceName()); req.setCityName(region.cityName());
        dictDataApi.validateDictDataList(DICT_STUDENT_NATURE, List.of(req.getStudentNature()));
        dictDataApi.validateDictDataList(DICT_SERVICE_PERIOD, List.of(req.getServicePeriod()));
        dictDataApi.validateDictDataList(DICT_STUDENT_SOURCE, List.of(req.getStudentSource()));
        dictDataApi.validateDictDataList(DICT_FEE_MODE, List.of(req.getFeeMode()));
        dictDataApi.validateDictDataList(DICT_PAYMENT_METHOD, List.of(req.getPaymentMethod()));
        Set<String> skuRefs = new HashSet<>();
        List<ValidatedItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderSubmitReqVO.Item item : req.getItems()) {
            if (!skuRefs.add(item.getSkuRef())) throw exception(PRODUCT_SKU_DUPLICATE);
            LeadProductSnapshot snapshot = skuService.validateLeadProduct(item.getSpuRef(), false, item.getSkuRef(), false);
            BigDecimal amount = item.getActualAmount().setScale(2);
            total = total.add(amount); items.add(new ValidatedItem(snapshot, amount));
        }
        List<VoucherRef> vouchers = validateVouchers(req.getPaymentVouchers(), userId);
        if (total.signum() > 0 && vouchers.isEmpty()) throw exception(SALES_ORDER_VOUCHER_REQUIRED);
        return new ValidatedSubmission(items, vouchers, total.setScale(2));
    }

    private RegionSnapshot validateRegion(String provinceCode, String cityCode) {
        if (REGION_OTHER.equals(provinceCode)) {
            if (!REGION_OTHER.equals(cityCode)) throw exception(LEAD_REGION_INVALID);
            AreaRespDTO province = areaApi.getAreaByParentIdAndSelectionCode(Area.ID_CHINA, REGION_OTHER);
            if (!isEnabledArea(province, 2)) throw exception(LEAD_REGION_INVALID);
            AreaRespDTO city = areaApi.getAreaByParentIdAndSelectionCode(province.getId(), REGION_OTHER);
            if (!isEnabledArea(city, 3)) throw exception(LEAD_REGION_INVALID);
            return new RegionSnapshot(REGION_OTHER, province.getName(), REGION_OTHER, city.getName());
        }
        AreaRespDTO province;
        try { province = areaApi.getArea(Integer.valueOf(provinceCode)); }
        catch (NumberFormatException ex) { throw exception(LEAD_REGION_INVALID); }
        if (!isEnabledArea(province, 2)) throw exception(LEAD_REGION_INVALID);
        if (REGION_OTHER.equals(cityCode)) {
            AreaRespDTO city = areaApi.getAreaByParentIdAndSelectionCode(province.getId(), REGION_OTHER);
            if (city != null) {
                if (!isEnabledArea(city, 3)) throw exception(LEAD_REGION_INVALID);
                return new RegionSnapshot(provinceCode, province.getName(), REGION_OTHER, city.getName());
            }
            if (Boolean.TRUE.equals(province.getLeafSelectable())) {
                return new RegionSnapshot(provinceCode, province.getName(), REGION_OTHER, null);
            }
            throw exception(LEAD_REGION_INVALID);
        }
        AreaRespDTO city;
        try { city = areaApi.getArea(Integer.valueOf(cityCode)); }
        catch (NumberFormatException ex) { throw exception(LEAD_REGION_INVALID); }
        if (city == null || !Integer.valueOf(3).equals(city.getType())
                || !CommonStatusEnum.ENABLE.getStatus().equals(city.getStatus())
                || !Objects.equals(city.getParentId(), province.getId())) throw exception(LEAD_REGION_INVALID);
        return new RegionSnapshot(provinceCode, province.getName(), cityCode, city.getName());
    }

    private static boolean isEnabledArea(AreaRespDTO area, int type) {
        return area != null && Integer.valueOf(type).equals(area.getType())
                && CommonStatusEnum.ENABLE.getStatus().equals(area.getStatus());
    }

    private List<VoucherRef> validateVouchers(List<SalesOrderSubmitReqVO.Attachment> attachments, Long userId) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        List<VoucherRef> result = new ArrayList<>(); Set<Long> ids = new HashSet<>(); int sort = 0;
        for (SalesOrderSubmitReqVO.Attachment attachment : attachments) {
            if (!ids.add(attachment.getInfraFileId())) throw exception(SALES_ORDER_ATTACHMENT_INVALID);
            FileInfoRespDTO file;
            try { file = fileApi.getFileInfo(attachment.getInfraFileId()); }
            catch (ServiceException ex) { throw exception(SALES_ORDER_ATTACHMENT_INVALID); }
            if (file == null || !VOUCHER_TYPES.contains(file.getType()) || file.getSize() == null || file.getSize() > MAX_VOUCHER_SIZE
                    || !String.valueOf(userId).equals(file.getCreator()) || StrUtil.isBlank(file.getPath())
                    || !file.getPath().startsWith("zsjos/sales-order-voucher/")) throw exception(SALES_ORDER_ATTACHMENT_INVALID);
            result.add(new VoucherRef(file.getId(), file.getUrl(), file.getName(), file.getType(), file.getSize(), sort++));
        }
        return result;
    }

    private void applySubmission(SalesOrderDO order, SalesOrderSubmitReqVO req, ValidatedSubmission validated, LocalDateTime now) {
        String studentName = req.getStudentName().trim();
        order.setBuyerName(StrUtil.blankToDefault(StrUtil.trim(req.getBuyerName()), studentName));
        order.setStudentName(studentName); order.setStudentNature(req.getStudentNature());
        order.setStudentMobile(StrUtil.trim(req.getStudentMobile())); order.setStudentWechatId(StrUtil.trim(req.getStudentWechatId()));
        order.setProvinceCode(req.getProvinceCode()); order.setProvinceName(req.getProvinceName());
        order.setCityCode(req.getCityCode()); order.setCityName(req.getCityName());
        order.setAgreedExamTime(StrUtil.trim(req.getAgreedExamTime())); order.setClassType(StrUtil.trim(req.getClassType()));
        order.setServicePeriod(req.getServicePeriod()); order.setStudentSource(req.getStudentSource());
        order.setTotalAmount(validated.total()); order.setDiscountAmount(BigDecimal.ZERO); order.setPayableAmount(validated.total());
        order.setCustomerPaidAt(req.getCustomerPaidAt()); order.setFeeMode(req.getFeeMode()); order.setPaymentMethod(req.getPaymentMethod());
        order.setRemark(StrUtil.trim(req.getRemark())); order.setStudentSpecialRequirements(StrUtil.trim(req.getStudentSpecialRequirements()));
        order.setMaterialDeliveryContact(StrUtil.trim(req.getMaterialDeliveryContact()));
        order.setPaymentVoucherRefs(JsonUtils.toJsonString(validated.vouchers())); order.setSubmittedAt(now);
    }

    private void insertItems(Long orderId, List<ValidatedItem> items) {
        for (ValidatedItem validated : items) {
            SalesOrderItemDO item = new SalesOrderItemDO(); item.setOrderId(orderId);
            item.setProductRef(validated.snapshot().productRef()); item.setSkuRef(validated.snapshot().skuRef());
            item.setQuantity(BigDecimal.ONE); item.setUnitPrice(validated.snapshot().price());
            item.setDiscountAmount(BigDecimal.ZERO); item.setPayableAmount(validated.actualAmount());
            item.setProductSnapshot(JsonUtils.toJsonString(validated.snapshot())); itemMapper.insert(item);
        }
    }

    private String buildOrderSnapshot(SalesOrderDO order, ValidatedSubmission validated) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("order", order); snapshot.put("items", validated.items().stream().map(item -> Map.of(
                "product", item.snapshot(), "actualAmount", item.actualAmount())).toList());
        snapshot.put("paymentVouchers", validated.vouchers());
        return JsonUtils.toJsonString(snapshot);
    }

    private SalesOrderRespVO convert(SalesOrderDO order, SalesOrderApprovalRoundDO round, BpmTaskRespDTO task, Long userId) {
        SalesOrderRespVO result = new SalesOrderRespVO();
        result.setId(order.getId()); result.setOrderNo(order.getOrderNo()); result.setLeadId(order.getLeadId());
        result.setOpportunityId(order.getOpportunityId()); result.setStatus(order.getStatus()); result.setSubmitterUserId(order.getSubmitterUserId());
        result.setSupersedesOrderId(order.getSupersedesOrderId()); result.setSupersededByOrderId(order.getSupersededByOrderId());
        result.setBuyerName(order.getBuyerName()); result.setStudentName(order.getStudentName()); result.setStudentNature(order.getStudentNature());
        result.setStudentMobile(order.getStudentMobile()); result.setStudentWechatId(order.getStudentWechatId());
        result.setProvinceCode(order.getProvinceCode()); result.setProvinceName(order.getProvinceName()); result.setCityCode(order.getCityCode()); result.setCityName(order.getCityName());
        result.setAgreedExamTime(order.getAgreedExamTime()); result.setClassType(order.getClassType()); result.setServicePeriod(order.getServicePeriod());
        result.setStudentSource(order.getStudentSource()); result.setTotalAmount(order.getTotalAmount()); result.setCustomerPaidAt(order.getCustomerPaidAt());
        result.setFeeMode(order.getFeeMode()); result.setPaymentMethod(order.getPaymentMethod()); result.setRemark(order.getRemark());
        result.setStudentSpecialRequirements(order.getStudentSpecialRequirements()); result.setMaterialDeliveryContact(order.getMaterialDeliveryContact());
        result.setSubmittedAt(order.getSubmittedAt()); result.setEffectiveAt(order.getEffectiveAt());
        result.setItems(itemMapper.selectListByOrderId(order.getId()).stream().map(this::convertItem).toList());
        result.setPaymentVouchers(convertVouchers(order.getPaymentVoucherRefs()));
        if (round != null) { result.setApprovalRoundNo(round.getRoundNo()); result.setApprovalRoundStatus(round.getStatus()); result.setProcessInstanceId(round.getProcessInstanceId()); result.setDecisionReason(round.getDecisionReason()); }
        if (task != null) { result.setTaskId(task.getId()); result.setTaskDefinitionKey(task.getTaskDefinitionKey()); result.setTaskStatus(task.getStatus()); result.setTaskReason(task.getReason()); result.setTaskCreateTime(task.getCreateTime()); result.setTaskEndTime(task.getEndTime()); }
        result.setCanRevise(STATUS_REVISION_REQUIRED.equals(order.getStatus()) && permissionService.canRevise(order, userId));
        return result;
    }

    private Map<Long, SalesOrderApprovalRoundDO> getCurrentRounds(List<SalesOrderDO> orders) {
        List<Long> roundIds = orders.stream().map(SalesOrderDO::getCurrentApprovalRoundId).filter(Objects::nonNull).distinct().toList();
        if (roundIds.isEmpty()) return Map.of();
        Map<Long, SalesOrderApprovalRoundDO> result = new HashMap<>();
        roundMapper.selectBatchIds(roundIds).forEach(round -> result.put(round.getId(), round));
        return result;
    }

    private SalesOrderListItemRespVO convertListItem(SalesOrderDO order, SalesOrderApprovalRoundDO round, BpmTaskRespDTO task) {
        SalesOrderListItemRespVO result = new SalesOrderListItemRespVO();
        result.setId(order.getId()); result.setOrderNo(order.getOrderNo()); result.setLeadId(order.getLeadId());
        result.setStatus(order.getStatus()); result.setStudentName(order.getStudentName()); result.setStudentMobile(order.getStudentMobile());
        result.setTotalAmount(order.getTotalAmount()); result.setSubmittedAt(order.getSubmittedAt()); result.setEffectiveAt(order.getEffectiveAt());
        if (round != null) result.setApprovalRoundNo(round.getRoundNo());
        if (task != null) {
            result.setTaskId(task.getId()); result.setTaskDefinitionKey(task.getTaskDefinitionKey()); result.setTaskStatus(task.getStatus());
            result.setTaskReason(task.getReason()); result.setTaskCreateTime(task.getCreateTime()); result.setTaskEndTime(task.getEndTime());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private SalesOrderRespVO.ItemVO convertItem(SalesOrderItemDO item) {
        LeadProductSnapshot snapshot = JsonUtils.parseObject(item.getProductSnapshot(), LeadProductSnapshot.class);
        SalesOrderRespVO.ItemVO result = new SalesOrderRespVO.ItemVO(); result.setId(item.getId());
        result.setProductRef(item.getProductRef()); result.setSkuRef(item.getSkuRef()); result.setActualAmount(item.getPayableAmount());
        if (snapshot != null) {
            result.setProductName(snapshot.name()); result.setSkuName(snapshot.skuName());
            result.setCategoryPath(snapshot.categoryPath().stream().map(node -> node.name()).toList());
            result.setAttrValues(snapshot.selectedAttrValuesJson() == null ? Map.of() : JsonUtils.parseObject(snapshot.selectedAttrValuesJson(), Map.class));
        }
        return result;
    }

    private List<SalesOrderRespVO.AttachmentVO> convertVouchers(String json) {
        List<VoucherRef> refs = json == null ? List.of() : JsonUtils.parseArray(json, VoucherRef.class);
        Map<Long, String> urls = refs.isEmpty() ? Map.of() : fileApi.presignGetUrls(refs.stream().map(VoucherRef::infraFileId).toList(), 600);
        return refs.stream().map(ref -> { SalesOrderRespVO.AttachmentVO vo = new SalesOrderRespVO.AttachmentVO();
            vo.setInfraFileId(ref.infraFileId()); vo.setFileUrl(urls.getOrDefault(ref.infraFileId(), ref.fileUrl()));
            vo.setOriginalName(ref.originalName()); vo.setContentType(ref.contentType()); vo.setFileSize(ref.fileSize()); return vo; }).toList();
    }

    private String generateOrderNo() {
        return "SO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private Long parseOrderId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith(BUSINESS_KEY_PREFIX)) return null;
        try { return Long.valueOf(businessKey.substring(BUSINESS_KEY_PREFIX.length())); }
        catch (NumberFormatException ex) { return null; }
    }

    private record ValidatedItem(LeadProductSnapshot snapshot, BigDecimal actualAmount) {}
    private record ValidatedSubmission(List<ValidatedItem> items, List<VoucherRef> vouchers, BigDecimal total) {}
    private record RegionSnapshot(String provinceCode, String provinceName, String cityCode, String cityName) {}
    private record VoucherRef(Long infraFileId, String fileUrl, String originalName, String contentType, Long fileSize, Integer sort) {}
}
