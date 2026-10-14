package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderVoidReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.giftpurchase.GiftPurchaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.OrderPaymentAllocationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.OrderPaymentAllocationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_WON;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_VALID;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.OPPORTUNITY_STATUS_FOLLOWING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.OPPORTUNITY_TYPE_INITIAL_CONVERSION;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_CANCELLED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class SalesOrderVoidService {
    @Resource private SalesOrderMapper orders;
    @Resource private LeadMapper leads;
    @Resource private PersonMapper persons;
    @Resource private OpportunityMapper opportunities;
    @Resource private CashbackMapper cashbacks;
    @Resource private GiftPurchaseMapper gifts;
    @Resource private OrderPaymentAllocationMapper allocations;
    @Resource private SalesOrderObjectPermissionService permissions;
    @Resource private SalesOrderCommandService commands;

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "sales-order", bizId = "#orderId", action = "void")
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "sales-order.void", targetType = "sales-order")
    public void voidOrder(Long orderId, Long userId, SalesOrderVoidReqVO request) {
        SalesOrderDO located = orders.selectById(orderId);
        if (located == null) throw exception(SALES_ORDER_NOT_EXISTS);
        if (!permissions.canVoid(located, userId)) throw exception(SALES_ORDER_PERMISSION_DENIED);
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty() || reason.length() > 1000) throw exception(SALES_ORDER_VOID_REASON_REQUIRED);
        Long tenant = TenantContextHolder.getRequiredTenantId();
        // Entry commands lock Lead before Person. Serialize eligibility changes with fresh submissions.
        LeadDO customerLead = located.getLeadId() == null && located.getPersonId() != null
                ? leads.selectByPersonId(located.getPersonId()) : null;
        Long leadId = located.getLeadId() != null ? located.getLeadId() : customerLead == null ? null : customerLead.getId();
        LeadDO lead = leadId == null ? null : leads.selectByIdForUpdate(leadId, tenant);
        if (located.getPersonId() != null) persons.selectByIdForUpdate(located.getPersonId(), tenant);
        SalesOrderDO order = orders.selectByIdForUpdate(orderId, tenant);
        if (order == null) throw exception(SALES_ORDER_NOT_EXISTS);
        var command = new SalesOrderCommandService.Command(orderId, order.getCurrentApprovalRoundId(), null,
                "void", null, null, userId, commands.fingerprint(reason, request.getOrderVersion()));
        if (commands.replay(request.getIdempotencyKey(), command)) return;
        if (!STATUS_EFFECTIVE.equals(order.getStatus())) throw exception(SALES_ORDER_VOID_STATE_INVALID);
        if (!Objects.equals(order.getVersion(), request.getOrderVersion())) throw exception(SALES_ORDER_VERSION_CONFLICT);
        commands.register(request.getIdempotencyKey(), command);
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(STATUS_VOIDED);
        order.setVoidReason(reason);
        order.setVoidedAt(now);
        order.setVoidedByUserId(userId);
        order.setVersion(order.getVersion() + 1);
        orders.updateById(order);

        // Cancellation invalidates entitlement, including paid rebates, but never rewrites payout history.
        // Payout and settlement commands use the same rebate locks and expected-state transitions.
        for (var cashback : cashbacks.selectByOrderIdForUpdate(orderId, tenant)) {
            if (STATUS_CANCELLED.equals(cashback.getStatus())) continue;
            if (cashbacks.cancel(cashback.getId(), cashback.getVersion(), cashback.getStatus(), now,
                    "订单作废：" + reason.substring(0, Math.min(reason.length(), 450))) != 1) throw exception(SALES_ORDER_VERSION_CONFLICT);
        }
        gifts.update(null, new LambdaUpdateWrapper<GiftPurchaseDO>().eq(GiftPurchaseDO::getOrderId, orderId)
                .isNull(GiftPurchaseDO::getVoidedAt).set(GiftPurchaseDO::getVoidedAt, now));
        allocations.update(null, new LambdaUpdateWrapper<OrderPaymentAllocationDO>()
                .eq(OrderPaymentAllocationDO::getOrderId, orderId).isNull(OrderPaymentAllocationDO::getVoidedAt)
                .set(OrderPaymentAllocationDO::getVoidedAt, now));

        // No registration/service/BPM cancellation: delivery remains owned by its existing manual workflow.
        if (lead != null && STATUS_WON.equals(lead.getStatus()) && !orders.hasEffectiveOrder(order.getPersonId())
                && orders.selectActiveByLeadId(lead.getId(), ACTIVE_ORDER_STATUSES) == null) {
            lead.setStatus(STATUS_VALID);
            LeadMapper.advanceActivity(lead, now);
            leads.updateById(lead);
            var opportunity = opportunities.selectByLeadIdForUpdate(lead.getId(), tenant);
            if (opportunity == null) {
                // This is a new follow-up opportunity, not a reconstruction of the old approval history.
                opportunity = new OpportunityDO();
                opportunity.setLeadId(lead.getId()); opportunity.setPersonId(order.getPersonId());
                opportunity.setOwnerUserId(lead.getOwnerUserId()); opportunity.setType(OPPORTUNITY_TYPE_INITIAL_CONVERSION);
                opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING); opportunity.setVersion(0);
                opportunities.insert(opportunity);
            } else if (STATUS_WON.equals(opportunity.getStatus())) {
                opportunities.update(null, new LambdaUpdateWrapper<OpportunityDO>()
                        .eq(OpportunityDO::getId, opportunity.getId())
                        .set(OpportunityDO::getStatus, OPPORTUNITY_STATUS_FOLLOWING)
                        .set(OpportunityDO::getWonAt, null));
            }
        }
    }
}
