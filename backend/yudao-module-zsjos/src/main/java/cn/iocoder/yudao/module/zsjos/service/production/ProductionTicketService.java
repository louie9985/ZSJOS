package cn.iocoder.yudao.module.zsjos.service.production;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketCreateContextRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderCandidatePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderSceneRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAssignmentService;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ProductionTicketService {
    private static final String ASSIGNMENT_SCENE = "new_media_operator_filming_editor";

    @Resource private ProductionTicketMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private ProductionTicketObjectPermissionProvider objectPermissionProvider;
    @Resource private MediaDataScopeService dataScopeService;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private PersonMapper personMapper;
    @Resource private PositioningCardSubmissionMapper positioningSubmissionMapper;
    @Resource private LeadAssignmentService relationService;
    @Resource private MediaWorkflowEventService workflowEventService;
    @Resource private ProductionTicketCommandService commandService;
    @Resource private WorkOrderService workOrderService;

    public PageResult<ProductionTicketRespVO> page(ProductionTicketPageReqVO req, Long userId) {
        MediaDataScopeService.Scope scope = dataScopeService.resolve(userId, "zsjos:production-ticket:query-all");
        PageResult<ProductionTicketDO> page = mapper.selectPage(req, scope.userIds(), scope.all());
        return new PageResult<>(page.getList().stream().map(row -> toResp(row, userId)).toList(), page.getTotal());
    }

    public PageResult<ProductionTicketRespVO> poolPage(ProductionTicketPageReqVO req, Long userId) {
        PageResult<ProductionTicketDO> page = mapper.selectPoolPage(req);
        return new PageResult<>(page.getList().stream().map(row -> toResp(row, userId)).toList(), page.getTotal());
    }

    public List<ProductionTicketRespVO> myPending(Long userId) {
        return mapper.selectPendingByAssignee(userId).stream().map(row -> toResp(row, userId)).toList();
    }

    @ZsjosPermission(bizType = BIZ_TYPE_MEDIA_ACCOUNT, bizId = "#accountId", action = "production-ticket-create")
    public ProductionTicketCreateContextRespVO getCreateContext(Long accountId, String sceneCode, Long userId) {
        WorkOrderSceneRespVO template = workOrderService.catalog(1, 500, userId).getList().stream()
                .filter(item -> Objects.equals(item.getCode(), sceneCode))
                .filter(item -> "PRODUCTION_TICKET".equals(item.getProcessorType()))
                .findFirst().orElseThrow(() -> exception(PRODUCTION_TICKET_PERMISSION_DENIED));
        MediaAccountDO account = accountMapper.selectById(accountId);
        if (account == null || !Objects.equals(account.getOwnerOperatorUserId(), userId)) {
            throw exception(PRODUCTION_TICKET_PERMISSION_DENIED);
        }
        ProductionTicketCreateContextRespVO response = new ProductionTicketCreateContextRespVO();
        response.setSceneCode(template.getCode()); response.setTemplateName(template.getName());
        response.setAllowedAssignmentTypes(template.getAllowedAssignmentTypes());
        response.setTargetDeptIds(template.getTargetDeptIds());
        response.setFields(template.getFields());
        response.setAccountId(account.getId());
        response.setAccountNo(account.getAccountNo());
        response.setAccountName(account.getNickname());
        response.setPlatformLabel(account.getPlatformLabelSnapshot());
        PersonDO person = account.getStudentPersonId() == null ? null : personMapper.selectById(account.getStudentPersonId());
        response.setStudentName(person == null ? null : person.getName());
        response.setAccountFields(parseAccountFields(account.getDetailSnapshotJson()));
        PositioningCardSubmissionDO positioning = positioningSubmissionMapper.selectCurrentConfirmedByAccount(accountId);
        response.setCanCreate(positioning != null);
        response.setUnavailableReason(positioning == null ? "当前账号尚无已确认定位卡，请先完成定位确认" : null);
        if (positioning != null) {
            response.setPositioningSubmissionId(positioning.getId());
            response.setPositioning(positioningSnapshot(positioning));
        }
        WorkOrderCandidatePageReqVO candidateReq = new WorkOrderCandidatePageReqVO();
        candidateReq.setSceneCode(sceneCode); candidateReq.setPageNo(1); candidateReq.setPageSize(100);
        response.setAssigneeCandidates(workOrderService.candidatePage(candidateReq, userId).getList().stream().map(candidate -> {
            LeadAssignmentUserRespVO item = new LeadAssignmentUserRespVO();
            item.setId(candidate.getId()); item.setNickname(candidate.getName()); item.setDeptId(candidate.getDeptId());
            return item;
        }).toList());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductionTicketSaveReqVO req, Long userId) {
        // 账号页发起时必须绑定账号（前端已强制），工单中心直接发起时允许不绑定；
        // 两者共用这一个入口，因此账号在此是可选上下文而不是必填引用。
        List<Long> accountIds = normalizeAccountIds(req);
        String operatorRemark = StrUtil.trimToNull(req.getOperatorRemark());
        String fingerprint = commandService.fingerprint("create", req.getSceneCode(), accountIds,
                req.getAssigneeUserId(), req.getTargetDeptId(), operatorRemark, req.getValues(),
                req.getAttachmentIds(), userId);
        var command = commandService.begin(req.getIdempotencyKey(),
                new ProductionTicketCommandService.Command("create", accountIds.isEmpty() ? null : accountIds.getFirst(),
                        null, null, userId, fingerprint), Long.class);
        if (!command.created()) return command.result();
        List<ProductionTicketCreateContextRespVO> contexts = accountIds.stream()
                .map(accountId -> getCreateContext(accountId, req.getSceneCode(), userId)).toList();
        ProductionTicketCreateContextRespVO context = contexts.isEmpty() ? null : contexts.getFirst();
        if (accountIds.size() > 1 && req.getStudentPersonId() == null) throw exception(PRODUCTION_TICKET_REFERENCE_INVALID);
        if (req.getStudentPersonId() != null && accountIds.stream().map(accountMapper::selectById)
                .anyMatch(account -> account == null || !Objects.equals(account.getStudentPersonId(), req.getStudentPersonId()))) {
            throw exception(PRODUCTION_TICKET_REFERENCE_INVALID);
        }
        Map<String, Object> values = enrichAccountLinks(req.getValues(), contexts);
        // 定位卡仅作为上下文信息展示，不再阻断发起（原 canCreate 强制校验已移除）
        if ((req.getAssigneeUserId() == null) == (req.getTargetDeptId() == null)) {
            throw exception(PRODUCTION_TICKET_ASSIGNEE_INVALID);
        }
        // 未绑定账号时没有账号维度的候选列表，改用工单模板的接收人候选校验。
        if (req.getAssigneeUserId() != null) {
            List<LeadAssignmentUserRespVO> candidates = context != null
                    ? context.getAssigneeCandidates()
                    : templateCandidates(req.getSceneCode(), userId);
            if (candidates.stream().map(LeadAssignmentUserRespVO::getId).noneMatch(req.getAssigneeUserId()::equals)) {
                throw exception(PRODUCTION_TICKET_ASSIGNEE_INVALID);
            }
        }
        ProductionTicketDO ticket = new ProductionTicketDO();
        ticket.setTicketNo("PT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        ticket.setAccountId(accountIds.isEmpty() ? null : accountIds.getFirst());
        ticket.setAccountIdsJson(JsonUtils.toJsonString(accountIds));
        ticket.setOwnerOperatorUserId(userId);
        ticket.setReviewerUserId(userId);
        ticket.setAssigneeFilmingEditorUserId(req.getAssigneeUserId());
        ticket.setPositioningSubmissionId(context == null ? null : context.getPositioningSubmissionId());
        List<Map<String, Object>> accountSnapshots = contexts.stream().map(ProductionTicketService::accountSnapshot).toList();
        ticket.setAccountSnapshotJson(JsonUtils.toJsonString(accountSnapshots));
        ticket.setDispatchContextSnapshotJson(JsonUtils.toJsonString(contextSnapshot(context, accountSnapshots, operatorRemark)));
        ticket.setIdempotencyKey(req.getIdempotencyKey());
        ticket.setTicketVersion(1);
        ticket.setRevisionCount(0);
        ticket.setEntitlementQuota(0);
        ticket.setRemainingCount(0);
        ticket.setOverEntitlement(false);
        ticket.setStatus(req.getAssigneeUserId() == null ? TICKET_PUBLIC_POOL : TICKET_PENDING_ACCEPT);
        ticket.setVersion(0);
        try {
            mapper.insert(ticket);
        } catch (DuplicateKeyException ex) {
            throw exception(PRODUCTION_TICKET_CREATE_IDEMPOTENCY_CONFLICT);
        }
        workflowEventService.transition(BIZ_TYPE_PRODUCTION_TICKET, ticket.getId(), userId, null,
                ticket.getStatus(), null, "ticket-created:" + ticket.getId());
        workOrderService.createProductionEnvelope(req.getSceneCode(), ticket.getId(),
                accountIds.isEmpty() ? null : accountIds.getFirst(), userId,
                req.getAssigneeUserId(), req.getTargetDeptId(), operatorRemark == null ? "拍剪工单" : operatorRemark,
                values, req.getAttachmentIds(), req.getIdempotencyKey());
        if (req.getAssigneeUserId() != null) {
            workflowEventService.createTaskAndNotify("media.ticket.pending_accept", "MEDIA_TICKET_ACCEPT",
                    BIZ_TYPE_PRODUCTION_TICKET, ticket.getId(), req.getAssigneeUserId(), "拍剪工单待接",
                    ACTION_ACCEPT_TICKET, userId, "ticket-accept:" + ticket.getId(), ticketPayload(ticket));
        }
        commandService.complete(req.getIdempotencyKey(), userId, ticket.getId());
        return ticket.getId();
    }

    public Long createFromWorkOrder(ProductionTicketSaveReqVO req, Long userId) {
        Long ticketId = create(req, userId);
        return workOrderService.getProductionEnvelopeId(ticketId);
    }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "read")
    public ProductionTicketRespVO get(Long id, Long userId) { return toResp(require(id), userId); }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "accept")
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, Integer version) { transition(id, version, TICKET_PENDING_ACCEPT, TICKET_ACCEPTED); }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "reject-assignment")
    @Transactional(rollbackFor = Exception.class)
    public void rejectAssignment(Long id, Integer version, String reason, String key) {
        String normalized = normalizedReason(reason, PRODUCTION_TICKET_ASSIGNMENT_REJECT_REASON_REQUIRED);
        ProductionTicketDO ticket = require(id);
        Long operator = getLoginUserId();
        String envelopeStatus = workOrderService.rejectProductionAssignment(id, operator, normalized, key);
        String targetStatus = "AVAILABLE".equals(envelopeStatus) ? TICKET_PUBLIC_POOL : "assignment_rejected";
        if (mapper.rejectAssignment(id, version, targetStatus) == 0) throw exception(PRODUCTION_TICKET_VERSION_CONFLICT);
        workflowEventService.completeTask("MEDIA_TICKET_ACCEPT", id, ticket.getAssigneeFilmingEditorUserId());
        workflowEventService.transition(BIZ_TYPE_PRODUCTION_TICKET, id, operator, TICKET_PENDING_ACCEPT,
                targetStatus, normalized, "ticket-assignment-rejected:" + id + ":" + key);
        workflowEventService.notify("media.ticket.assignment_rejected", BIZ_TYPE_PRODUCTION_TICKET, id,
                ticket.getOwnerOperatorUserId(), operator, "ticket-assignment-rejected-notify:" + id + ":" + key,
                ticketPayload(ticket));
    }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "claim")
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id, Integer version, String key, Long userId) {
        ProductionTicketDO ticket = require(id);
        workOrderService.validateProductionPoolCandidate(id, userId);
        if (mapper.claim(id, version, userId) == 0) throw exception(PRODUCTION_TICKET_CLAIM_ALREADY_TAKEN);
        workOrderService.syncProductionStatus(id, TICKET_ACCEPTED, userId, userId, null,
                "ticket-claimed:" + id + ":" + key);
        workflowEventService.transition(BIZ_TYPE_PRODUCTION_TICKET, id, userId, TICKET_PUBLIC_POOL,
                TICKET_ACCEPTED, null, "ticket-claimed:" + id + ":" + key);
        workflowEventService.notify("media.ticket.claimed", BIZ_TYPE_PRODUCTION_TICKET, id,
                ticket.getOwnerOperatorUserId(), userId, "ticket-claimed-notify:" + id + ":" + key,
                ticketPayload(ticket));
    }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "produce")
    @Transactional(rollbackFor = Exception.class)
    public void startProduction(Long id, Integer version) { transition(id, version, TICKET_ACCEPTED, TICKET_IN_PRODUCTION); }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "submit")
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Integer version) { transition(id, version, TICKET_IN_PRODUCTION, TICKET_SUBMITTED); }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "check")
    @Transactional(rollbackFor = Exception.class)
    public void startCheck(Long id, Integer version) { transition(id, version, TICKET_SUBMITTED, TICKET_CHECKING); }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "check")
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Integer version) { transition(id, version, TICKET_CHECKING, TICKET_COMPLETED); }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "check")
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Integer version, String reason) {
        String normalized = normalizedReason(reason, PRODUCTION_TICKET_REJECT_REASON_REQUIRED);
        ProductionTicketDO ticket = require(id);
        if (mapper.rejectForRevision(id, version, normalized) == 0) throw exception(PRODUCTION_TICKET_VERSION_CONFLICT);
        Long operator = getLoginUserId();
        workOrderService.syncProductionStatus(id, TICKET_REJECTED, ticket.getAssigneeFilmingEditorUserId(), operator,
                normalized, "ticket:" + id + ":" + version + ":" + TICKET_REJECTED);
        workflowEventService.transition(BIZ_TYPE_PRODUCTION_TICKET, id, operator, TICKET_CHECKING, TICKET_REJECTED,
                normalized, "ticket:" + id + ":" + version + ":" + TICKET_REJECTED);
        workflowEventService.completeTask("MEDIA_TICKET_CHECK", id, ticket.getReviewerUserId());
        workflowEventService.notify("media.ticket.rejected", BIZ_TYPE_PRODUCTION_TICKET, id,
                ticket.getAssigneeFilmingEditorUserId(), operator,
                "ticket-result:" + id + ":" + version + ":" + TICKET_REJECTED, ticketPayload(ticket));
    }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "accept")
    @Transactional(rollbackFor = Exception.class)
    public void reaccept(Long id, Integer version) { transition(id, version, TICKET_REJECTED, TICKET_ACCEPTED); }

    public ProductionTicketDO require(Long id) {
        ProductionTicketDO ticket = mapper.selectById(id);
        if (ticket == null) throw exception(PRODUCTION_TICKET_NOT_EXISTS);
        return ticket;
    }

    private void transition(Long id, Integer version, String expected, String target) {
        transition(require(id), version, expected, target);
    }

    private void transition(ProductionTicketDO ticket, Integer version, String expected, String target) {
        if (!expected.equals(ticket.getStatus())) throw exception(PRODUCTION_TICKET_STATE_INVALID);
        if (mapper.transition(ticket.getId(), version, expected, target) == 0) throw exception(PRODUCTION_TICKET_VERSION_CONFLICT);
        Long operator = getLoginUserId();
        workOrderService.syncProductionStatus(ticket.getId(), target, ticket.getAssigneeFilmingEditorUserId(), operator,
                null, "ticket:" + ticket.getId() + ":" + version + ":" + target);
        workflowEventService.transition(BIZ_TYPE_PRODUCTION_TICKET, ticket.getId(), operator, expected, target, null,
                "ticket:" + ticket.getId() + ":" + version + ":" + target);
        if (TICKET_ACCEPTED.equals(target) && ticket.getAssigneeFilmingEditorUserId() != null) {
            workflowEventService.completeTask("MEDIA_TICKET_ACCEPT", ticket.getId(), ticket.getAssigneeFilmingEditorUserId());
        }
        if (TICKET_SUBMITTED.equals(target)) {
            workflowEventService.createTaskAndNotify("media.ticket.pending_check", "MEDIA_TICKET_CHECK",
                    BIZ_TYPE_PRODUCTION_TICKET, ticket.getId(), ticket.getReviewerUserId(), "拍剪工单待核对",
                    ACTION_START_TICKET_CHECK, operator, "ticket-check:" + ticket.getId() + ":" + version,
                    ticketPayload(ticket));
        }
        if (TICKET_COMPLETED.equals(target) || TICKET_REJECTED.equals(target)) {
            workflowEventService.completeTask("MEDIA_TICKET_CHECK", ticket.getId(), ticket.getReviewerUserId());
        }
        if (TICKET_COMPLETED.equals(target)) {
            workflowEventService.notify("media.ticket.approved", BIZ_TYPE_PRODUCTION_TICKET, ticket.getId(),
                    ticket.getAssigneeFilmingEditorUserId(), operator,
                    "ticket-result:" + ticket.getId() + ":" + version + ":" + target, ticketPayload(ticket));
        }
    }

    private Map<String, Object> ticketPayload(ProductionTicketDO ticket) {
        return Map.of("bizNo", ticket.getTicketNo(), "deepLink",
                "/zsjos/production-tickets?ticketId=" + ticket.getId());
    }

    private ProductionTicketRespVO toResp(ProductionTicketDO ticket, Long userId) {
        ProductionTicketRespVO response = BeanUtils.toBean(ticket, ProductionTicketRespVO.class);
        response.setAccountIds(parseAccountIds(ticket.getAccountIdsJson(), ticket.getAccountId()));
        response.setAccounts(parseAccountSnapshots(ticket.getAccountSnapshotJson()));
        response.setDispatchContext(parseMap(ticket.getDispatchContextSnapshotJson()));
        if (TICKET_PENDING_ACCEPT.equals(ticket.getStatus())) {
            if (!objectPermissionProvider.hasPermission(ticket.getId(), "accept", userId)) {
                response.setAvailableActions(List.of());
                return response;
            }
            List<String> actions = new java.util.ArrayList<>(2);
            if (permissionApi.hasAnyPermissions(userId, "zsjos:production-ticket:accept")) {
                actions.add(ACTION_ACCEPT_TICKET);
            }
            if (permissionApi.hasAnyPermissions(userId, "zsjos:production-ticket:reject-assignment")) {
                actions.add(ACTION_REJECT_TICKET_ASSIGNMENT);
            }
            response.setAvailableActions(actions);
            return response;
        }
        String action = objectAction(ticket.getStatus());
        String permission = actionPermission(ticket.getStatus());
        if (action == null || !objectPermissionProvider.hasPermission(ticket.getId(), action, userId)
                || !permissionApi.hasAnyPermissions(userId, permission)) {
            response.setAvailableActions(List.of());
            return response;
        }
        response.setAvailableActions(switch (ticket.getStatus()) {
            case TICKET_PUBLIC_POOL -> List.of(ACTION_CLAIM_TICKET);
            case TICKET_ACCEPTED -> List.of(ACTION_START_TICKET);
            case TICKET_IN_PRODUCTION -> List.of(ACTION_SUBMIT_TICKET);
            case TICKET_SUBMITTED -> List.of(ACTION_START_TICKET_CHECK);
            case TICKET_CHECKING -> List.of(ACTION_APPROVE_TICKET, ACTION_REJECT_TICKET);
            case TICKET_REJECTED -> List.of(ACTION_REACCEPT_TICKET);
            default -> List.of();
        });
        return response;
    }

    private static String objectAction(String status) {
        return switch (status) {
            case TICKET_REJECTED -> "accept";
            case TICKET_PUBLIC_POOL -> "claim";
            case TICKET_ACCEPTED -> "produce";
            case TICKET_IN_PRODUCTION -> "submit";
            case TICKET_SUBMITTED, TICKET_CHECKING -> "check";
            default -> null;
        };
    }

    private static String actionPermission(String status) {
        return switch (status) {
            case TICKET_REJECTED -> "zsjos:production-ticket:accept";
            case TICKET_PUBLIC_POOL -> "zsjos:production-ticket:claim";
            case TICKET_ACCEPTED -> "zsjos:production-ticket:produce";
            case TICKET_IN_PRODUCTION -> "zsjos:production-ticket:submit";
            case TICKET_SUBMITTED, TICKET_CHECKING -> "zsjos:production-ticket:check";
            default -> null;
        };
    }

    private static String normalizedReason(String reason, ErrorCode error) {
        String normalized = reason == null ? null : reason.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 500) throw exception(error);
        return normalized;
    }

    private static List<MediaAccountDetailSnapshotVO> parseAccountFields(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.parseArray(json, MediaAccountDetailSnapshotVO.class);
    }

    private static Map<String, Object> positioningSnapshot(PositioningCardSubmissionDO row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionNo", row.getSubmissionNo());
        result.put("submittedAt", row.getSubmittedAt());
        result.put("fields", parseList(row.getFieldsSnapshotJson()));
        result.put("values", parseMap(row.getValuesSnapshotJson()));
        result.put("dict", parseMap(row.getDictSnapshotJson()));
        result.put("layer1", parseMap(row.getLayer1Json()));
        result.put("layer2", parseMap(row.getLayer2Json()));
        result.put("formula", parseMap(row.getFormulaJson()));
        result.put("feasibility", parseMap(row.getFeasibilityJson()));
        result.put("contentForm", parseMap(row.getContentFormJson()));
        result.put("compliance", parseMap(row.getComplianceJson()));
        result.put("professionalRisk", row.getProfessionalRisk());
        return result;
    }

    private static Map<String, Object> contextSnapshot(ProductionTicketCreateContextRespVO context,
                                                       List<Map<String, Object>> accountSnapshots,
                                                       String operatorRemark) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 工单中心可直接发起不绑定账号的剪拍工单，此时只有备注与动态字段。
        result.put("accountId", context == null ? null : context.getAccountId());
        result.put("accountNo", context == null ? null : context.getAccountNo());
        result.put("accountName", context == null ? null : context.getAccountName());
        result.put("platformLabel", context == null ? null : context.getPlatformLabel());
        result.put("studentName", context == null ? null : context.getStudentName());
        result.put("accountFields", context == null ? List.of() : context.getAccountFields());
        result.put("accounts", accountSnapshots);
        result.put("positioningSubmissionId", context == null ? null : context.getPositioningSubmissionId());
        result.put("positioning", context == null ? null : context.getPositioning());
        result.put("operatorRemark", operatorRemark);
        return result;
    }

    private static Map<String, Object> accountSnapshot(ProductionTicketCreateContextRespVO context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", context.getAccountId()); result.put("accountNo", context.getAccountNo());
        result.put("accountName", context.getAccountName()); result.put("platformLabel", context.getPlatformLabel());
        result.put("homepageUrl", accountFieldValue(context, "homepage_url"));
        result.put("coverFileId", accountFieldValue(context, "cover"));
        return result;
    }

    private static Map<String, Object> enrichAccountLinks(Map<String, Object> submitted,
                                                           List<ProductionTicketCreateContextRespVO> contexts) {
        String links = contexts.stream().map(context -> accountFieldValue(context, "homepage_url"))
                .filter(String.class::isInstance).map(String.class::cast).filter(link -> !link.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (links.isBlank()) return submitted;
        Map<String, Object> values = submitted == null ? new LinkedHashMap<>() : new LinkedHashMap<>(submitted);
        values.put("account_link", links);
        return values;
    }

    private static Object accountFieldValue(ProductionTicketCreateContextRespVO context, String key) {
        return context.getAccountFields() == null ? null : context.getAccountFields().stream()
                .filter(field -> key.equals(field.getKey())).map(MediaAccountDetailSnapshotVO::getValue)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static List<Long> normalizeAccountIds(ProductionTicketSaveReqVO req) {
        List<Long> source = req.getAccountIds() == null || req.getAccountIds().isEmpty()
                ? (req.getAccountId() == null ? List.of() : List.of(req.getAccountId())) : req.getAccountIds();
        List<Long> ids = source.stream().filter(Objects::nonNull).distinct().toList();
        // 账号可以整体为空（工单中心发起的剪拍工单），但一旦给了就必须是合法去重的 ID。
        if (ids.size() != source.size()) throw exception(PRODUCTION_TICKET_REFERENCE_INVALID);
        return ids;
    }

    /** 未绑定账号时用模板的接收人候选做归属校验，避免跳过接收人合法性检查。 */
    private List<LeadAssignmentUserRespVO> templateCandidates(String sceneCode, Long userId) {
        WorkOrderCandidatePageReqVO candidateReq = new WorkOrderCandidatePageReqVO();
        candidateReq.setSceneCode(sceneCode); candidateReq.setPageNo(1); candidateReq.setPageSize(100);
        return workOrderService.candidatePage(candidateReq, userId).getList().stream().map(candidate -> {
            LeadAssignmentUserRespVO item = new LeadAssignmentUserRespVO();
            item.setId(candidate.getId()); item.setNickname(candidate.getName()); item.setDeptId(candidate.getDeptId());
            return item;
        }).toList();
    }

    private static List<Long> parseAccountIds(String json, Long legacyId) {
        List<Long> ids = json == null || json.isBlank() ? List.of() : JsonUtils.parseArray(json, Long.class);
        return ids.isEmpty() && legacyId != null ? List.of(legacyId) : ids;
    }

    private static List<Map<String, Object>> parseAccountSnapshots(String json) {
        return json == null || json.isBlank() ? List.of()
                : JsonUtils.parseObject(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(String json) {
        return json == null || json.isBlank() ? Map.of() : JsonUtils.parseObject(json, Map.class);
    }

    private static List<Object> parseList(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.parseArray(json, Object.class);
    }
}
