package cn.iocoder.yudao.module.zsjos.service.production;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.production.vo.ProductionTicketSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.common.MediaDataScopeService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ProductionTicketService {
    @Resource private ProductionTicketMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private ProductionTicketObjectPermissionProvider objectPermissionProvider;
    @Resource private MediaDataScopeService dataScopeService;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private MediaWorkflowEventService workflowEventService;

    public PageResult<ProductionTicketRespVO> page(ProductionTicketPageReqVO req, Long userId) {
        MediaDataScopeService.Scope scope = dataScopeService.resolve(userId, "zsjos:production-ticket:query-all");
        PageResult<ProductionTicketDO> page = mapper.selectPage(req, scope.userIds(), scope.all());
        return new PageResult<>(page.getList().stream().map(row -> toResp(row, userId)).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductionTicketSaveReqVO req, Long userId) {
        if (accountMapper.selectById(req.getAccountId()) == null) throw exception(PRODUCTION_TICKET_REFERENCE_INVALID);
        try {
            if (req.getAssigneeFilmingEditorUserId() != null) {
                adminUserApi.validateUser(req.getAssigneeFilmingEditorUserId());
            }
            if (req.getReviewerUserId() != null) adminUserApi.validateUser(req.getReviewerUserId());
        } catch (RuntimeException ex) {
            throw exception(PRODUCTION_TICKET_REFERENCE_INVALID);
        }
        ProductionTicketDO ticket = new ProductionTicketDO();
        ticket.setTicketNo("PT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        ticket.setAccountId(req.getAccountId());
        ticket.setOwnerOperatorUserId(userId);
        ticket.setReviewerUserId(req.getReviewerUserId());
        ticket.setAssigneeFilmingEditorUserId(req.getAssigneeFilmingEditorUserId());
        ticket.setScriptText(req.getScriptText());
        // The current UI exposes one delivery deadline while the persisted contract
        // keeps both expected delivery and deadline timestamps non-null.
        LocalDateTime deliveryAt = req.getExpectedDeliveredAt() != null
                ? req.getExpectedDeliveredAt() : req.getDeadlineAt();
        if (deliveryAt == null) throw exception(PRODUCTION_TICKET_REFERENCE_INVALID);
        ticket.setExpectedDeliveredAt(deliveryAt);
        ticket.setDeadlineAt(req.getDeadlineAt() != null ? req.getDeadlineAt() : deliveryAt);
        ticket.setMaxRevisionCount(req.getMaxRevisionCount() == null ? 0 : req.getMaxRevisionCount());
        ticket.setRevisionCount(0);
        ticket.setStatus(TICKET_PENDING_ACCEPT);
        ticket.setVersion(0);
        ticket.setTicketVersion(1);
        ticket.setEntitlementQuota(0);
        ticket.setRemainingCount(0);
        ticket.setOverEntitlement(false);
        mapper.insert(ticket);
        if (ticket.getAssigneeFilmingEditorUserId() != null) {
            workflowEventService.createTaskAndNotify("media.ticket.pending_accept", "MEDIA_TICKET_ACCEPT",
                    BIZ_TYPE_PRODUCTION_TICKET, ticket.getId(), ticket.getAssigneeFilmingEditorUserId(), "拍剪工单待接",
                    ACTION_ACCEPT_TICKET, userId, "ticket-accept:" + ticket.getId(), ticketPayload(ticket));
        }
        return ticket.getId();
    }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "read")
    public ProductionTicketRespVO get(Long id, Long userId) { return toResp(require(id), userId); }

    @ZsjosPermission(bizType = BIZ_TYPE_PRODUCTION_TICKET, bizId = "#id", action = "accept")
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, Integer version) { transition(id, version, TICKET_PENDING_ACCEPT, TICKET_ACCEPTED); }

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
        String normalizedReason = reason == null ? null : reason.trim();
        if (normalizedReason == null || normalizedReason.isEmpty() || normalizedReason.length() > 500) {
            throw exception(PRODUCTION_TICKET_REJECT_REASON_REQUIRED);
        }
        ProductionTicketDO ticket = require(id);
        if (ticket.getRevisionCount() >= ticket.getMaxRevisionCount()) throw exception(PRODUCTION_TICKET_REVISION_LIMIT);
        if (mapper.rejectForRevision(id, version, normalizedReason) == 0) throw exception(PRODUCTION_TICKET_VERSION_CONFLICT);
        Long operator=cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        workflowEventService.transition(BIZ_TYPE_PRODUCTION_TICKET,id,operator,TICKET_CHECKING,TICKET_REJECTED,normalizedReason,"ticket:"+id+":"+version+":"+TICKET_REJECTED);
        workflowEventService.completeTask("MEDIA_TICKET_CHECK",id,ticket.getReviewerUserId());
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
        Long operator=cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        workflowEventService.transition(BIZ_TYPE_PRODUCTION_TICKET,ticket.getId(),operator,expected,target,null,"ticket:"+ticket.getId()+":"+version+":"+target);
        if(TICKET_ACCEPTED.equals(target)&&ticket.getAssigneeFilmingEditorUserId()!=null)workflowEventService.completeTask("MEDIA_TICKET_ACCEPT",ticket.getId(),ticket.getAssigneeFilmingEditorUserId());
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

    private java.util.Map<String, Object> ticketPayload(ProductionTicketDO ticket) {
        return java.util.Map.of("bizNo", ticket.getTicketNo(),
                "deepLink", "/zsjos/production-tickets?ticketId=" + ticket.getId());
    }

    private ProductionTicketRespVO toResp(ProductionTicketDO ticket, Long userId) {
        ProductionTicketRespVO response = BeanUtils.toBean(ticket, ProductionTicketRespVO.class);
        if (!objectPermissionProvider.hasPermission(ticket.getId(), "read", userId)) {
            response.setAvailableActions(List.of()); return response;
        }
        String objectAction = switch (ticket.getStatus()) {
            case TICKET_PENDING_ACCEPT, TICKET_REJECTED -> "accept";
            case TICKET_ACCEPTED -> "produce";
            case TICKET_IN_PRODUCTION -> "submit";
            case TICKET_SUBMITTED, TICKET_CHECKING -> "check";
            default -> null;
        };
        String permission = switch (ticket.getStatus()) {
            case TICKET_PENDING_ACCEPT, TICKET_REJECTED -> "zsjos:production-ticket:accept";
            case TICKET_ACCEPTED -> "zsjos:production-ticket:produce";
            case TICKET_IN_PRODUCTION -> "zsjos:production-ticket:submit";
            case TICKET_SUBMITTED, TICKET_CHECKING -> "zsjos:production-ticket:check";
            default -> null;
        };
        if (permission == null || !objectPermissionProvider.hasPermission(ticket.getId(), objectAction, userId)
                || !permissionApi.hasAnyPermissions(userId, permission)) {
            response.setAvailableActions(List.of()); return response;
        }
        response.setAvailableActions(switch (ticket.getStatus()) {
            case TICKET_PENDING_ACCEPT -> List.of(ACTION_ACCEPT_TICKET);
            case TICKET_ACCEPTED -> List.of(ACTION_START_TICKET);
            case TICKET_IN_PRODUCTION -> List.of(ACTION_SUBMIT_TICKET);
            case TICKET_SUBMITTED -> List.of(ACTION_START_TICKET_CHECK);
            case TICKET_CHECKING -> List.of(ACTION_APPROVE_TICKET, ACTION_REJECT_TICKET);
            case TICKET_REJECTED -> List.of(ACTION_REACCEPT_TICKET);
            default -> List.of();
        });
        return response;
    }
}
