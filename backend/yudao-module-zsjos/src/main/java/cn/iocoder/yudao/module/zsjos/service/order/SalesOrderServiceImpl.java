package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
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
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterConfigService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadInboxFilterQuery;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadLifecycleTaskService;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Resource private PersonMapper personMapper;
    @Resource private ZsjosProductSkuService skuService;
    @Resource private SalesOrderObjectPermissionService permissionService;
    @Resource private AdvancedFilterService advancedFilterService;
    @Resource private FileApi fileApi;
    @Resource private AreaApi areaApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private LeadInboxFilterConfigService inboxFilterConfigService;
    @Resource private LeadLifecycleTaskService lifecycleTaskService;
    @Resource private NotifyBusinessEventApi notifyBusinessEventApi;
    @Resource private DeptApi deptApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private LeadAgingPoolService agingPoolService;
    @Resource private PersonIdentityWriteService personIdentityWriteService;
    @Resource private SalesOrderCommandService commandService;
    @Resource private SalesOrderSupervisorConfirmationService supervisorConfirmationService;
    @Resource private SalesOrderNumberService orderNumberService;
    @Resource private CashbackService cashbackService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "enter-deal")
    public Long createAndSubmit(Long leadId, Long userId, SalesOrderSubmitReqVO reqVO) {
        Long duplicateId = findIdempotentOrder(leadId, userId, reqVO.getIdempotencyKey());
        if (duplicateId != null) return duplicateId;
        LeadDO lead = requireEligibleLead(leadId, userId);
        // The lead row serializes concurrent submissions. Recheck after acquiring it so a retry can replay the winner.
        duplicateId = findIdempotentOrder(leadId, userId, reqVO.getIdempotencyKey());
        if (duplicateId != null) return duplicateId;
        if (orderMapper.selectActiveByLeadId(leadId, ACTIVE_ORDER_STATUSES) != null) throw exception(SALES_ORDER_ACTIVE_DUPLICATE);
        if (orderMapper.hasEffectiveOrder(lead.getPersonId())) throw exception(SALES_ORDER_REPURCHASE_CUSTOMER_INVALID);
        OpportunityDO opportunity = requireEligibleOpportunity(lead);
        ValidatedSubmission validated = validateSubmission(reqVO, userId);
        LocalDateTime now = LocalDateTime.now();
        SalesOrderDO order = new SalesOrderDO();
        order.setLeadId(leadId); order.setOpportunityId(opportunity.getId()); order.setPersonId(lead.getPersonId());
        order.setOrderType(ORDER_TYPE_FIRST_PURCHASE); order.setStatus(STATUS_PENDING_APPROVAL);
        order.setSubmitterUserId(userId); order.setFormalSalesUserId(lead.getOwnerUserId());
        order.setSubmitterCenterType(SUBMITTER_CENTER_SALES);
        applySubmission(order, reqVO, validated, now);
        order.setSubmissionIdempotencyKey(reqVO.getIdempotencyKey()); order.setVersion(0);
        insertOrderWithNumber(order);
        List<SalesOrderItemDO> createdItems = insertItems(order.getId(), validated.items());
        createDealCashbacks(leadId, order.getId(), createdItems, validated.items());
        startRound(order, opportunity, userId, reqVO.getIdempotencyKey(), validated, 1, now);
        agingPoolService.markDealPending(leadId, userId, now);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "enter-deal")
    public Long createSystemRepurchase(Long leadId, Long userId, SalesOrderRepurchaseReqVO reqVO) {
        LeadDO lead = requireRepurchaseLead(leadId, userId);
        return createRepurchase(lead.getPersonId(), null, lead.getOwnerUserId(), userId,
                reqVO.getRepurchaseReason(), reqVO.getOrder(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createExternalRepurchase(Long userId, SalesOrderRepurchaseReqVO reqVO) {
        String name = StrUtil.trim(reqVO.getCustomerName());
        String mobile = StrUtil.trim(reqVO.getCustomerMobile());
        String wechatId = StrUtil.trim(reqVO.getCustomerWechatId());
        if (StrUtil.isBlank(name) || StrUtil.isAllBlank(mobile, wechatId)) {
            throw exception(SALES_ORDER_REPURCHASE_CUSTOMER_INVALID);
        }
        PersonDO person = personIdentityWriteService.resolveOrCreate(name, mobile, wechatId, "active");
        if (!Objects.equals(StrUtil.trim(person.getName()), name)
                || leadMapper.selectLatestByPersonId(person.getId()) != null) {
            throw exception(SALES_ORDER_REPURCHASE_CUSTOMER_INVALID);
        }
        return createRepurchase(person.getId(), null, userId, userId, reqVO.getRepurchaseReason(), reqVO.getOrder(), false);
    }

    private Long createRepurchase(Long personId, Long sourceLeadId, Long formalSalesUserId, Long userId, String reason,
                                  SalesOrderSubmitReqVO submission, boolean requireEffectiveOrder) {
        Long duplicateId = findIdempotentCustomerOrder(personId, userId, submission.getIdempotencyKey());
        if (duplicateId != null) return duplicateId;
        if (requireEffectiveOrder && !orderMapper.hasEffectiveOrder(personId)) {
            throw exception(SALES_ORDER_REPURCHASE_CUSTOMER_INVALID);
        }
        if (orderMapper.selectActiveRepurchaseByPersonId(personId, ACTIVE_ORDER_STATUSES) != null) {
            throw exception(SALES_ORDER_CUSTOMER_ACTIVE_REPURCHASE);
        }
        ValidatedSubmission validated = validateSubmission(submission, userId);
        LocalDateTime now = LocalDateTime.now();
        SalesOrderDO order = new SalesOrderDO();
        order.setPersonId(personId); order.setLeadId(null); order.setOpportunityId(null);
        order.setOrderType(ORDER_TYPE_REPURCHASE); order.setStatus(STATUS_PENDING_APPROVAL);
        order.setSubmitterUserId(userId); order.setFormalSalesUserId(formalSalesUserId);
        order.setSubmitterCenterType(SUBMITTER_CENTER_SALES); order.setRepurchaseReason(reason.trim());
        applySubmission(order, submission, validated, now);
        order.setSubmissionIdempotencyKey(submission.getIdempotencyKey()); order.setVersion(0);
        insertOrderWithNumber(order);
        List<SalesOrderItemDO> createdItems = insertItems(order.getId(), validated.items());
        createDealCashbacks(sourceLeadId, order.getId(), createdItems, validated.items());
        startRound(order, null, userId, submission.getIdempotencyKey(), validated, 1, now);
        return order.getId();
    }

    private Long findIdempotentCustomerOrder(Long personId, Long userId, String key) {
        SalesOrderDO duplicate = orderMapper.selectByIdempotencyKey(key);
        if (duplicate == null) return null;
        if (Objects.equals(duplicate.getPersonId(), personId)
                && Objects.equals(duplicate.getSubmitterUserId(), userId)) return duplicate.getId();
        throw exception(SALES_ORDER_IDEMPOTENCY_CONFLICT);
    }

    private Long findIdempotentOrder(Long leadId, Long userId, String idempotencyKey) {
        SalesOrderDO duplicate = orderMapper.selectByIdempotencyKey(idempotencyKey);
        if (duplicate == null) return null;
        if (Objects.equals(duplicate.getLeadId(), leadId)
                && Objects.equals(duplicate.getSubmitterUserId(), userId)) return duplicate.getId();
        throw exception(SALES_ORDER_IDEMPOTENCY_CONFLICT);
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
        if (!Set.of(STATUS_REVISION_REQUIRED, STATUS_TERMINATED).contains(order.getStatus())) {
            throw exception(SALES_ORDER_STATE_INVALID);
        }
        LeadDO lead = null;
        OpportunityDO opportunity = null;
        if (!ORDER_TYPE_REPURCHASE.equals(order.getOrderType())) {
            lead = requireRevisionLead(order.getLeadId());
            opportunity = requireEligibleOpportunity(lead);
        }
        ValidatedSubmission validated = validateSubmission(reqVO, userId);
        LocalDateTime now = LocalDateTime.now();
        cashbackService.cancelDealCashbacks(orderId, "订单修改重提");
        order.setStatus(STATUS_PENDING_APPROVAL);
        order.setTerminationReason(null); order.setTerminatedAt(null);
        applySubmission(order, reqVO, validated, now);
        order.setEffectiveAt(null);
        orderMapper.updateById(order);
        itemMapper.deleteByOrderId(orderId);
        List<SalesOrderItemDO> createdItems = insertItems(orderId, validated.items());
        createDealCashbacks(order.getLeadId(), orderId, createdItems, validated.items());
        SalesOrderApprovalRoundDO latest = roundMapper.selectLatestByOrderId(orderId);
        startRound(order, opportunity, userId, reqVO.getIdempotencyKey(), validated,
                latest == null ? 1 : latest.getRoundNo() + 1, now);
        if (lead != null) agingPoolService.markDealPending(lead.getId(), userId, now);
    }

    @Override
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "read")
    public SalesOrderRespVO get(Long orderId, Long userId) {
        SalesOrderDO order = orderMapper.selectById(orderId);
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        SalesOrderApprovalRoundDO round = roundMapper.selectLatestByOrderId(orderId);
        return convert(order, round, null, userId);
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
        List<Long> matchedOrderIds = advancedFilterService.matchOrderIds(reqVO.getAdvancedFilter());
        if (matchedOrderIds != null) reqVO.setStatus(null);
        PageResult<SalesOrderDO> page = orderMapper.selectMyPage(userId, reqVO, matchedOrderIds);
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
        Set<String> allowedTaskKeys = permissionService.approvalTaskKeys(userId);
        if (allowedTaskKeys.isEmpty()) throw exception(SALES_ORDER_PERMISSION_DENIED);
        if (StrUtil.isNotBlank(reqVO.getCenter())) {
            String centerTaskKey = centerTaskKey(reqVO.getCenter());
            if (!allowedTaskKeys.contains(centerTaskKey)) throw exception(SALES_ORDER_PERMISSION_DENIED);
            allowedTaskKeys = Set.of(centerTaskKey);
        }
        boolean advanced = advancedFilterService.hasConditions(reqVO.getAdvancedFilter());
        LeadInboxFilterConfigVO config = inboxFilterConfigService.getPublishedConfig(INBOX_AUDIENCE_REVIEWER);
        LeadInboxFilterQuery filter;
        if (advanced) {
            filter = new LeadInboxFilterQuery(Set.of(), Set.of(), false, Map.of());
        } else if (reqVO.getGroupKey() == null && reqVO.getHandled() != null) {
            filter = new LeadInboxFilterQuery(Set.of(), Set.of(), false, Map.of(
                    INBOX_FILTER_FIELD_HANDLED, Set.of(Boolean.TRUE.equals(reqVO.getHandled()) ? "done" : "todo")));
        } else {
            String groupKey = reqVO.getGroupKey() != null ? reqVO.getGroupKey() : config.getGroups().stream()
                    .filter(group -> Boolean.TRUE.equals(group.getEnabled())).findFirst()
                    .orElseThrow(() -> exception(LEAD_INBOX_FILTER_INVALID)).getKey();
            filter = inboxFilterConfigService.resolveQuery(config, groupKey, reqVO.getOptionKey());
        }
        filter = restrictTaskFilter(filter, allowedTaskKeys);
        if (filter.matchNone()) return PageResult.empty();
        List<String> processIds = advanced
                ? roundMapper.selectProcessInstanceIdsByOrderIdsAndKeyword(
                        TenantContextHolder.getTenantId(), advancedFilterService.matchOrderIds(reqVO.getAdvancedFilter()),
                        StrUtil.blankToDefault(reqVO.getKeyword(), null))
                : searchProcessIds(reqVO.getKeyword());
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
        Set<String> allowedTaskKeys = permissionService.approvalTaskKeys(userId);
        if (allowedTaskKeys.isEmpty()) throw exception(SALES_ORDER_PERMISSION_DENIED);
        LeadInboxFilterConfigVO config = inboxFilterConfigService.getPublishedConfig(INBOX_AUDIENCE_REVIEWER);
        List<SalesOrderApprovalFilterProfileRespVO.GroupVO> groups = config.getGroups().stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .map(group -> {
                    LeadInboxFilterQuery groupQuery = inboxFilterConfigService.resolveQuery(config, group.getKey(), "all");
                    List<SalesOrderApprovalFilterProfileRespVO.OptionVO> options = group.getOptions().stream()
                            .filter(option -> Boolean.TRUE.equals(option.getEnabled()))
                            .filter(option -> optionTaskKey(option.getKey()) == null || allowedTaskKeys.contains(optionTaskKey(option.getKey())))
                            .map(option -> new SalesOrderApprovalFilterProfileRespVO.OptionVO(option.getKey(), option.getLabel(),
                                    countApprovalTasks(userId, inboxFilterConfigService.resolveQuery(config, group.getKey(), option.getKey()), null)))
                            .toList();
                    List<SalesOrderApprovalFilterProfileRespVO.SectionVO> sections = options.isEmpty() ? List.of()
                            : List.of(new SalesOrderApprovalFilterProfileRespVO.SectionVO(
                                    "approval_stage", group.getSectionLabel() == null ? "审批环节" : group.getSectionLabel(), options));
                    return new SalesOrderApprovalFilterProfileRespVO.GroupVO(group.getKey(), group.getLabel(),
                            countApprovalTasks(userId, groupQuery, null), sections);
                }).toList();
        List<SalesOrderApprovalFilterProfileRespVO.CenterVO> centers = new ArrayList<>();
        if (allowedTaskKeys.contains(TASK_REGISTRATION)) centers.add(new SalesOrderApprovalFilterProfileRespVO.CenterVO(CENTER_REGISTRATION, "报名履约中心"));
        if (allowedTaskKeys.contains(TASK_FINANCE)) centers.add(new SalesOrderApprovalFilterProfileRespVO.CenterVO(CENTER_FINANCE, "财务中心"));
        return new SalesOrderApprovalFilterProfileRespVO(groups, centers);
    }

    private String centerTaskKey(String center) {
        return switch (center) {
            case CENTER_REGISTRATION -> TASK_REGISTRATION;
            case CENTER_FINANCE -> TASK_FINANCE;
            default -> throw exception(LEAD_INBOX_FILTER_INVALID);
        };
    }

    private String optionTaskKey(String optionKey) {
        if ("registrationReview".equals(optionKey) || "registration_review".equals(optionKey)) return TASK_REGISTRATION;
        if ("financeReview".equals(optionKey) || "finance_review".equals(optionKey)) return TASK_FINANCE;
        return null;
    }

    private LeadInboxFilterQuery restrictTaskFilter(LeadInboxFilterQuery filter, Set<String> allowedTaskKeys) {
        Set<String> requested = filter.values(INBOX_FILTER_FIELD_TASK_DEFINITION_KEY);
        if (requested.isEmpty()) {
            Map<String, Set<String>> values = new LinkedHashMap<>(filter.valuesByField());
            values.put(INBOX_FILTER_FIELD_TASK_DEFINITION_KEY, new LinkedHashSet<>(allowedTaskKeys));
            return new LeadInboxFilterQuery(filter.statuses(), filter.assignmentStatuses(), filter.matchNone(), values);
        }
        Set<String> restricted = requested.stream().filter(allowedTaskKeys::contains).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new LeadInboxFilterQuery(filter.statuses(), filter.assignmentStatuses(), restricted.isEmpty(),
                Map.of(INBOX_FILTER_FIELD_TASK_DEFINITION_KEY, restricted));
    }

    private List<BpmTaskRespDTO> loadApprovalTasks(Long userId, SalesOrderPageReqVO reqVO,
                                                   LeadInboxFilterQuery filter, List<String> processIds) {
        Set<String> handled = filter.values(INBOX_FILTER_FIELD_HANDLED);
        Set<String> taskKeys = filter.values(INBOX_FILTER_FIELD_TASK_DEFINITION_KEY);
        List<String> handledValues = handled.isEmpty() ? List.of("todo", "done") : new ArrayList<>(handled);
        List<String> taskValues = taskKeys.isEmpty() ? Collections.singletonList(null) : new ArrayList<>(taskKeys);
        List<BpmTaskRespDTO> tasks = new ArrayList<>();
        int requiredOrdinaryTasks = reqVO.getPageNo() * reqVO.getPageSize();
        for (String handledValue : handledValues) {
            for (String taskKey : taskValues) {
                tasks.addAll(loadOrdinaryApprovalTasks(userId, handledValue, taskKey, processIds,
                        requiredOrdinaryTasks));
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
            total += loadOrdinaryApprovalTasks(userId, handledValue, taskKey, processIds, null).size();
        }
        return total;
    }

    /**
     * BPM owns sign tasks and exposes their parent relationship. Iterate its pages so sign children never consume
     * ordinary-review pagination slots and totals stay exact even when a review pool exceeds one API page.
     */
    private List<BpmTaskRespDTO> loadOrdinaryApprovalTasks(Long userId, String handledValue, String taskKey,
                                                            List<String> processIds, Integer limit) {
        final int bpmPageSize = 100;
        List<BpmTaskRespDTO> result = new ArrayList<>();
        int pageNo = 1;
        long loaded = 0;
        do {
            BpmTaskPageReqDTO taskReq = new BpmTaskPageReqDTO();
            taskReq.setPageNo(pageNo); taskReq.setPageSize(bpmPageSize);
            taskReq.setProcessDefinitionKey(PROCESS_DEFINITION_KEY); taskReq.setTaskDefinitionKey(taskKey);
            taskReq.setProcessInstanceIds(processIds);
            PageResult<BpmTaskRespDTO> page = "done".equals(handledValue)
                    ? processTaskApi.getDoneTaskPage(userId, taskReq) : processTaskApi.getTodoTaskPage(userId, taskReq);
            result.addAll(page.getList().stream().filter(task -> !Boolean.TRUE.equals(task.getSignTask())).toList());
            loaded += page.getList().size();
            if (limit != null && result.size() >= limit) break;
            if (page.getList().isEmpty() || loaded >= page.getTotal()) break;
            pageNo++;
        } while (true);
        return limit == null || result.size() <= limit ? result : result.subList(0, limit);
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
        SalesOrderApprovalRoundDO round = roundMapper.selectByIdForUpdate(reqVO.getApprovalRoundId(),
                TenantContextHolder.getRequiredTenantId());
        if (round == null || !Objects.equals(round.getOrderId(), orderId)
                || !Objects.equals(order.getCurrentApprovalRoundId(), round.getId())) throw exception(SALES_ORDER_VERSION_CONFLICT);
        String commandType = approve ? "approve" : "reject";
        String fingerprint = commandService.fingerprint(reqVO.getReason().trim(), reqVO.getOrderVersion(),
                reqVO.getRoundVersion());
        SalesOrderCommandService.Command replay = new SalesOrderCommandService.Command(orderId, round.getId(),
                round.getProcessInstanceId(), commandType, null, reqVO.getTaskId(), userId, fingerprint);
        if (commandService.replayDecision(reqVO.getIdempotencyKey(), replay)) return;
        if (!STATUS_PENDING_APPROVAL.equals(order.getStatus())) throw exception(SALES_ORDER_ALREADY_HANDLED);
        if (!Objects.equals(order.getVersion(), reqVO.getOrderVersion())
                || !Objects.equals(round.getVersion(), reqVO.getRoundVersion())) throw exception(SALES_ORDER_VERSION_CONFLICT);
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
        if (Boolean.TRUE.equals(task.getSignTask())) throw exception(SALES_ORDER_PERMISSION_DENIED);
        if (supervisorConfirmationService.getPending(round.getId(), task.getTaskDefinitionKey()) != null) {
            throw exception(SALES_ORDER_SUPERVISOR_PENDING);
        }
        SalesOrderCommandService.Command command = new SalesOrderCommandService.Command(orderId, round.getId(),
                round.getProcessInstanceId(), commandType, task.getTaskDefinitionKey(), reqVO.getTaskId(), userId,
                fingerprint);
        if (!approve) cashbackService.assertOrderRejectable(orderId);
        commandService.register(reqVO.getIdempotencyKey(), command);
        if (TASK_REGISTRATION.equals(task.getTaskDefinitionKey())) {
            if (round.getRegistrationDecisionIdempotencyKey() != null) throw exception(SALES_ORDER_ALREADY_HANDLED);
            round.setRegistrationDecisionIdempotencyKey(reqVO.getIdempotencyKey());
        } else {
            if (round.getFinanceDecisionIdempotencyKey() != null) throw exception(SALES_ORDER_ALREADY_HANDLED);
            round.setFinanceDecisionIdempotencyKey(reqVO.getIdempotencyKey());
        }
        order.setVersion(order.getVersion() + 1); round.setVersion(round.getVersion() + 1);
        orderMapper.updateById(order); roundMapper.updateById(round);
        BpmTaskDecisionReqDTO decision = new BpmTaskDecisionReqDTO();
        decision.setTaskId(reqVO.getTaskId()); decision.setReason(reqVO.getReason().trim());
        if (approve) processTaskApi.approveTask(userId, decision); else processTaskApi.rejectTask(userId, decision);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long orderId, Long userId, SalesOrderTerminateReqVO reqVO) {
        SalesOrderDO order = requireOrderForUpdate(orderId);
        SalesOrderApprovalRoundDO round = roundMapper.selectByIdForUpdate(reqVO.getApprovalRoundId(),
                TenantContextHolder.getRequiredTenantId());
        if (!(Objects.equals(order.getSubmitterUserId(), userId) || Objects.equals(order.getFormalSalesUserId(), userId))
                || round == null || !Objects.equals(round.getOrderId(), orderId)
                || !Objects.equals(order.getCurrentApprovalRoundId(), round.getId())) {
            throw exception(SALES_ORDER_TERMINATE_FORBIDDEN);
        }
        String fingerprint = commandService.fingerprint(reqVO.getReason().trim(), reqVO.getOrderVersion(),
                reqVO.getRoundVersion());
        SalesOrderCommandService.Command command = new SalesOrderCommandService.Command(orderId, round.getId(),
                round.getProcessInstanceId(), "terminate", null, null, userId, fingerprint);
        if (commandService.replay(reqVO.getIdempotencyKey(), command)) return;
        if (!STATUS_PENDING_APPROVAL.equals(order.getStatus())) throw exception(SALES_ORDER_TERMINATE_FORBIDDEN);
        if (!Objects.equals(order.getVersion(), reqVO.getOrderVersion())
                || !Objects.equals(round.getVersion(), reqVO.getRoundVersion())) throw exception(SALES_ORDER_VERSION_CONFLICT);
        commandService.register(reqVO.getIdempotencyKey(), command);
        cashbackService.assertOrderRejectable(orderId);
        LocalDateTime now = LocalDateTime.now();
        supervisorConfirmationService.cancelPending(round.getId(), now);
        order.setStatus(STATUS_TERMINATED); order.setTerminationReason(reqVO.getReason().trim()); order.setTerminatedAt(now);
        order.setVersion(order.getVersion() + 1);
        round.setStatus(ROUND_TERMINATED); round.setDecisionReason(reqVO.getReason().trim()); round.setCompletedAt(now);
        round.setTerminationIdempotencyKey(reqVO.getIdempotencyKey()); round.setVersion(round.getVersion() + 1);
        orderMapper.updateById(order); roundMapper.updateById(round);
        cashbackService.cancelDealCashbacks(orderId, "订单主动终止");
        processInstanceApi.terminateProcessInstanceByBusiness(userId, round.getProcessInstanceId(),
                "zsjos.sales-order.terminate", reqVO.getReason().trim());
        if (!ORDER_TYPE_REPURCHASE.equals(order.getOrderType())) {
            OpportunityDO opportunity = opportunityMapper.selectById(order.getOpportunityId());
            if (opportunity != null) { opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING); opportunityMapper.updateById(opportunity); }
            agingPoolService.handleOrderRejected(order.getLeadId(), now);
        }
    }

    @Override
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "read")
    public List<SalesOrderListItemRespVO> getCustomerOrders(Long leadId, Long userId) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        List<SalesOrderDO> orders = orderMapper.selectByPersonId(lead.getPersonId());
        Map<Long, SalesOrderApprovalRoundDO> rounds = getCurrentRounds(orders);
        return orders.stream().map(order -> convertListItem(order, rounds.get(order.getCurrentApprovalRoundId()), null)).toList();
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
        SalesOrderApprovalRoundDO locatedRound = roundMapper.selectByProcessInstanceId(processInstanceId);
        if (locatedRound == null) return;
        SalesOrderDO order = requireOrderForUpdate(locatedRound.getOrderId());
        SalesOrderApprovalRoundDO round = roundMapper.selectByIdForUpdate(locatedRound.getId(),
                TenantContextHolder.getRequiredTenantId());
        if (round == null || !Objects.equals(processInstanceId, round.getProcessInstanceId())
                || !ROUND_PENDING.equals(round.getStatus()) || !STATUS_PENDING_APPROVAL.equals(order.getStatus())
                || !Objects.equals(order.getCurrentApprovalRoundId(), round.getId())) return;
        LocalDateTime now = LocalDateTime.now();
        supervisorConfirmationService.cancelPending(round.getId(), now);
        OpportunityDO opportunity = order.getOpportunityId() == null ? null : opportunityMapper.selectById(order.getOpportunityId());
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            round.setStatus(ROUND_APPROVED); order.setStatus(STATUS_EFFECTIVE); order.setEffectiveAt(now);
            if (opportunity != null) {
                opportunity.setStatus(OPPORTUNITY_STATUS_WON); opportunity.setWonAt(now);
                opportunity.setNextFollowUpAt(null); opportunityMapper.updateById(opportunity);
            }
            LeadDO lead = order.getLeadId() == null ? null : leadMapper.selectById(order.getLeadId());
            if (lead != null && !ORDER_TYPE_REPURCHASE.equals(order.getOrderType())) {
                lead.setStatus(STATUS_WON);
                lead.setNextFollowUpAt(null);
                leadMapper.updateById(lead);
            }
            if (!ORDER_TYPE_REPURCHASE.equals(order.getOrderType())) {
                agingPoolService.completeConversion(order.getLeadId(), order.getSubmitterUserId(), now);
                lifecycleTaskService.cancelFirstFollowUpTasks(order.getLeadId(), now, "成交订单已生效");
                lifecycleTaskService.cancelFollowUpReminders(order.getLeadId(), now, "成交订单已生效");
            }
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)) {
            round.setStatus(ROUND_REJECTED); round.setDecisionReason(StrUtil.trim(reason)); order.setStatus(STATUS_REVISION_REQUIRED);
            if (opportunity != null) { opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING); opportunityMapper.updateById(opportunity); }
            cashbackService.cancelDealCashbacks(order.getId(), "订单审批驳回");
            if (order.getLeadId() != null && !ORDER_TYPE_REPURCHASE.equals(order.getOrderType())) agingPoolService.handleOrderRejected(order.getLeadId(), now);
        } else {
            round.setStatus(ROUND_REJECTED); round.setDecisionReason(StrUtil.trim(reason)); order.setStatus(STATUS_REVISION_REQUIRED);
            if (opportunity != null) { opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING); opportunityMapper.updateById(opportunity); }
            cashbackService.cancelDealCashbacks(order.getId(), "订单审批异常终止");
            if (order.getLeadId() != null && !ORDER_TYPE_REPURCHASE.equals(order.getOrderType())) agingPoolService.handleOrderRejected(order.getLeadId(), now);
        }
        round.setCompletedAt(now); roundMapper.updateById(round); orderMapper.updateById(order);
        LeadDO notificationLead = order.getLeadId() == null ? null : leadMapper.selectById(order.getLeadId());
        Long leadSubmitterUserId = notificationLead == null ? null : notificationLead.getSourceUserId();
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            List<Long> resultUserIds = java.util.stream.Stream.of(
                            order.getFormalSalesUserId(), round.getSubmittedByUserId())
                    .filter(Objects::nonNull).distinct().toList();
            publishOrderNotification(EFFECTIVE, order, "sales-order-effective:" + round.getId(),
                    List.of(), resultUserIds, leadSubmitterUserId, reason, now);
            if (leadSubmitterUserId != null) {
                publishOrderNotification(SUBMITTER_EFFECTIVE, order,
                        "sales-order-submitter-effective:" + round.getId(), List.of(), List.of(),
                        leadSubmitterUserId, reason, now);
            }
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)) {
            List<Long> resultUserIds = java.util.stream.Stream.of(
                            order.getFormalSalesUserId(), round.getSubmittedByUserId())
                    .filter(Objects::nonNull).distinct().toList();
            publishOrderNotification(REJECTED, order, "sales-order-rejected:" + round.getId(),
                    List.of(), resultUserIds, leadSubmitterUserId, reason, now);
        }
    }

    private void startRound(SalesOrderDO order, OpportunityDO opportunity, Long userId, String idempotencyKey,
                            ValidatedSubmission validated, int roundNo, LocalDateTime now) {
        SalesOrderApprovalConfigDO config = salesOrderApprovalConfigMapper.selectCurrent();
        if (config == null) throw exception(SALES_ORDER_APPROVAL_CONFIG_INVALID);
        List<Long> registrationUsers = new ArrayList<>(permissionService.enabledReviewers(config.getRegistrationDeptId()));
        List<Long> financeUsers = new ArrayList<>(permissionService.enabledReviewers(config.getFinanceDeptId()));
        if (registrationUsers.isEmpty() || financeUsers.isEmpty()) throw exception(SALES_ORDER_APPROVAL_CONFIG_INVALID);
        BpmProcessInstanceCreateReqDTO processReq = new BpmProcessInstanceCreateReqDTO();
        processReq.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
        processReq.setBusinessKey(BUSINESS_KEY_PREFIX + order.getId());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("orderId", order.getId()); variables.put("leadId", order.getLeadId()); variables.put("personId", order.getPersonId()); variables.put("roundNo", roundNo);
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
        round.setSubmittedAt(now); round.setSubmissionIdempotencyKey(idempotencyKey);
        round.setSupervisorConfirmationEnabled(true); round.setVersion(0); roundMapper.insert(round);
        order.setCurrentApprovalRoundId(round.getId()); order.setSubmittedAt(now); orderMapper.updateById(order);
        if (opportunity != null) {
            opportunity.setStatus(OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL); opportunityMapper.updateById(opportunity);
        }
        List<Long> reviewerUserIds = java.util.stream.Stream.concat(
                registrationUsers.stream(), financeUsers.stream()).distinct().toList();
        LeadDO lead = order.getLeadId() == null ? null : leadMapper.selectById(order.getLeadId());
        Long leadSubmitterUserId = lead == null ? null : lead.getSourceUserId();
        publishOrderNotification(SUBMITTED, order, "sales-order-submitted:" + round.getId(),
                reviewerUserIds, List.of(), leadSubmitterUserId, null, now);
        if (leadSubmitterUserId != null) {
            publishOrderNotification(SUBMITTER_PENDING, order,
                    "sales-order-submitter-pending:" + round.getId(), List.of(), List.of(),
                    leadSubmitterUserId, null, now);
        }
    }

    private void publishOrderNotification(String sceneCode, SalesOrderDO order, String sourceEventKey,
                                          List<Long> reviewers, List<Long> resultUserIds,
                                          Long leadSubmitterUserId, String reason, LocalDateTime occurredAt) {
        SalesOrderApprovalConfigDO config = salesOrderApprovalConfigMapper.selectCurrent();
        List<String> departments = config == null ? List.of() : java.util.stream.Stream.of(
                        config.getRegistrationDeptId(), config.getFinanceDeptId())
                .filter(Objects::nonNull).map(deptApi::getDept)
                .filter(Objects::nonNull).map(item -> item.getName()).filter(Objects::nonNull).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewerUserIds", reviewers); payload.put("submitterUserId", order.getSubmitterUserId());
        payload.put("resultUserIds", resultUserIds); payload.put("leadSubmitterUserId", leadSubmitterUserId);
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
        agingPoolService.requireCanOperateForUpdate(leadId, lead.getOwnerUserId(), userId);
        if (lead.getSuspendedAt() != null || !STATUS_VALID.equals(lead.getStatus())) {
            throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        }
        LeadAppealDO latestAppeal = leadAppealMapper.selectLatestByLeadId(leadId);
        if (latestAppeal != null && Set.of(APPEAL_STATUS_SALES_MANAGER_REVIEWING, APPEAL_STATUS_QUALITY_REVIEWING,
                APPEAL_STATUS_CHAIRMAN_REVIEWING).contains(latestAppeal.getStatus())) throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        return lead;
    }

    private LeadDO requireRepurchaseLead(Long leadId, Long userId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        agingPoolService.requireCanOperateForUpdate(leadId, lead.getOwnerUserId(), userId);
        if (lead.getSuspendedAt() != null || !STATUS_WON.equals(lead.getStatus())) {
            throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        }
        return lead;
    }

    private LeadDO requireRevisionLead(Long leadId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (lead.getSuspendedAt() != null || !STATUS_VALID.equals(lead.getStatus())) {
            throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        }
        LeadAppealDO latestAppeal = leadAppealMapper.selectLatestByLeadId(leadId);
        if (latestAppeal != null && Set.of(APPEAL_STATUS_SALES_MANAGER_REVIEWING, APPEAL_STATUS_QUALITY_REVIEWING,
                APPEAL_STATUS_CHAIRMAN_REVIEWING).contains(latestAppeal.getStatus())) throw exception(SALES_ORDER_ENTRY_FORBIDDEN);
        return lead;
    }

    private OpportunityDO requireEligibleOpportunity(LeadDO lead) {
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(lead.getId());
        if (opportunity == null
                || !Objects.equals(opportunity.getLeadId(), lead.getId())
                || !Objects.equals(opportunity.getPersonId(), lead.getPersonId())
                || !Objects.equals(opportunity.getOwnerUserId(), lead.getOwnerUserId())
                || !Set.of(OPPORTUNITY_STATUS_OPEN, OPPORTUNITY_STATUS_FOLLOWING).contains(opportunity.getStatus())) {
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
        if (StrUtil.isNotBlank(req.getStudentMobile()) && !ValidationUtils.isMobile(req.getStudentMobile().trim())) {
            throw exception(LEAD_MOBILE_INVALID);
        }
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
        if (vouchers.isEmpty()) throw exception(SALES_ORDER_VOUCHER_REQUIRED);
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

    private List<SalesOrderItemDO> insertItems(Long orderId, List<ValidatedItem> items) {
        List<SalesOrderItemDO> created = new ArrayList<>();
        for (ValidatedItem validated : items) {
            SalesOrderItemDO item = new SalesOrderItemDO(); item.setOrderId(orderId);
            item.setProductRef(validated.snapshot().productRef()); item.setSkuRef(validated.snapshot().skuRef());
            item.setQuantity(BigDecimal.ONE); item.setUnitPrice(validated.snapshot().price());
            item.setDiscountAmount(BigDecimal.ZERO); item.setPayableAmount(validated.actualAmount());
            item.setProductSnapshot(JsonUtils.toJsonString(validated.snapshot())); itemMapper.insert(item);
            created.add(item);
        }
        return created;
    }

    private void createDealCashbacks(Long sourceLeadId, Long orderId, List<SalesOrderItemDO> orderItems,
                                     List<ValidatedItem> validatedItems) {
        if (sourceLeadId == null || !cashbackService.isEligibleDealLead(sourceLeadId)) return;
        for (int i = 0; i < orderItems.size(); i++) {
            SalesOrderItemDO item = orderItems.get(i);
            ValidatedItem validated = validatedItems.get(i);
            BigDecimal rate = cashbackService.resolveDealRate(validated.snapshot().productRef());
            cashbackService.ensureDealCashback(new CashbackService.DealCashbackCommand(sourceLeadId, orderId,
                    item.getId(), validated.snapshot().productRef(), validated.snapshot().name(),
                    validated.actualAmount(), rate));
        }
    }

    private void insertOrderWithNumber(SalesOrderDO order) {
        for (int attempt = 1; attempt <= 20; attempt++) {
            order.setOrderNo(orderNumberService.next());
            try {
                orderMapper.insert(order);
                return;
            } catch (DuplicateKeyException exception) {
                if (!isOrderNumberConflict(exception) || attempt == 20) {
                    throw exception;
                }
                order.setId(null);
            }
        }
    }

    private boolean isOrderNumberConflict(DuplicateKeyException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("uk_tenant_order_no")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
        result.setOpportunityId(order.getOpportunityId()); result.setStatus(order.getStatus()); result.setOrderType(order.getOrderType());
        result.setPersonId(order.getPersonId()); result.setFormalSalesUserId(order.getFormalSalesUserId());
        result.setSubmitterUserId(order.getSubmitterUserId()); result.setVersion(order.getVersion());
        result.setCurrentApprovalRoundId(order.getCurrentApprovalRoundId()); result.setRepurchaseReason(order.getRepurchaseReason());
        result.setTerminationReason(order.getTerminationReason());
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
        if (round != null) {
            result.setApprovalRoundNo(round.getRoundNo()); result.setApprovalRoundStatus(round.getStatus());
            result.setProcessInstanceId(round.getProcessInstanceId()); result.setDecisionReason(round.getDecisionReason());
            result.setApprovalRoundVersion(round.getVersion());
            result.setCanRequestSupervisorConfirmation(Boolean.TRUE.equals(round.getSupervisorConfirmationEnabled()));
            Map<String, BpmProcessNodeStatusRespDTO> nodeStatuses = processTaskApi.getProcessNodeStatuses(
                    round.getProcessInstanceId(), Set.of(TASK_REGISTRATION, TASK_FINANCE)).stream()
                    .collect(java.util.stream.Collectors.toMap(BpmProcessNodeStatusRespDTO::getTaskDefinitionKey, item -> item, (left, right) -> right));
            result.setRegistrationApproval(convertApprovalStatus(nodeStatuses.get(TASK_REGISTRATION)));
            result.setFinanceApproval(convertApprovalStatus(nodeStatuses.get(TASK_FINANCE)));
            Map<String, SalesOrderSupervisorConfirmationDO> confirmations = supervisorConfirmationService.getByRound(round.getId())
                    .stream().collect(java.util.stream.Collectors.toMap(
                            SalesOrderSupervisorConfirmationDO::getTaskDefinitionKey, item -> item, (left, right) -> right));
            result.setRegistrationSupervisorConfirmation(convertSupervisorConfirmation(confirmations.get(TASK_REGISTRATION)));
            result.setFinanceSupervisorConfirmation(convertSupervisorConfirmation(confirmations.get(TASK_FINANCE)));
        }
        if (task != null) { result.setTaskId(task.getId()); result.setTaskDefinitionKey(task.getTaskDefinitionKey()); result.setTaskStatus(task.getStatus()); result.setTaskReason(task.getReason()); result.setTaskCreateTime(task.getCreateTime()); result.setTaskEndTime(task.getEndTime()); }
        result.setCanRevise(Set.of(STATUS_REVISION_REQUIRED, STATUS_TERMINATED).contains(order.getStatus())
                && permissionService.canRevise(order, userId));
        result.setCanTerminate(STATUS_PENDING_APPROVAL.equals(order.getStatus())
                && (Objects.equals(order.getSubmitterUserId(), userId) || Objects.equals(order.getFormalSalesUserId(), userId)));
        return result;
    }

    private SalesOrderRespVO.ApprovalStatusVO convertApprovalStatus(BpmProcessNodeStatusRespDTO source) {
        if (source == null) return null;
        SalesOrderRespVO.ApprovalStatusVO result = new SalesOrderRespVO.ApprovalStatusVO();
        result.setStatus(source.getStatus()); result.setReviewerUserId(source.getReviewerUserId());
        result.setReviewerUserName(source.getReviewerUserName());
        result.setCreateTime(source.getCreateTime()); result.setEndTime(source.getEndTime());
        return result;
    }

    private SalesOrderRespVO.SupervisorConfirmationVO convertSupervisorConfirmation(SalesOrderSupervisorConfirmationDO source) {
        if (source == null) return null;
        SalesOrderRespVO.SupervisorConfirmationVO result = new SalesOrderRespVO.SupervisorConfirmationVO();
        result.setId(source.getId()); result.setStatus(source.getStatus()); result.setRequesterUserId(source.getRequesterUserId());
        cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO requester = adminUserApi.getUser(source.getRequesterUserId());
        result.setRequesterUserName(requester == null ? null : requester.getNickname());
        result.setRequestReason(source.getRequestReason()); result.setDecisionReason(source.getDecisionReason());
        result.setRequestedAt(source.getRequestedAt()); result.setDecidedAt(source.getDecidedAt());
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
        result.setStatus(order.getStatus()); result.setOrderType(order.getOrderType()); result.setPersonId(order.getPersonId());
        result.setStudentName(order.getStudentName()); result.setStudentMobile(order.getStudentMobile());
        result.setTotalAmount(order.getTotalAmount()); result.setSubmittedAt(order.getSubmittedAt()); result.setEffectiveAt(order.getEffectiveAt());
        if (round != null) result.setApprovalRoundNo(round.getRoundNo());
        if (task != null) {
            result.setTaskId(task.getId()); result.setTaskDefinitionKey(task.getTaskDefinitionKey()); result.setTaskStatus(task.getStatus());
            result.setTaskReason(task.getReason()); result.setTaskCreateTime(task.getCreateTime()); result.setTaskEndTime(task.getEndTime());
        }
        if (round != null && task != null) {
            SalesOrderSupervisorConfirmationDO confirmation = supervisorConfirmationService.getByRound(round.getId()).stream()
                    .filter(item -> Objects.equals(item.getTaskDefinitionKey(), task.getTaskDefinitionKey())).findFirst().orElse(null);
            if (confirmation != null) {
                result.setSupervisorConfirmationId(confirmation.getId());
                result.setSupervisorConfirmationStatus(confirmation.getStatus());
                cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO requester =
                        adminUserApi.getUser(confirmation.getRequesterUserId());
                result.setSupervisorRequesterName(requester == null ? null : requester.getNickname());
            }
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
