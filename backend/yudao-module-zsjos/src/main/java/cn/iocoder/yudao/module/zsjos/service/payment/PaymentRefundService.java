package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentRefundApplyReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentRefundRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.*;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayClient;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PaymentRefundService {
    @Resource private PaymentRefundMapper refundMapper;
    @Resource private PaymentTransactionMapper transactionMapper;
    @Resource private PaymentIntentMapper paymentMapper;
    @Resource private PaymentGatewayEventMapper eventMapper;
    @Resource private AllinpayProperties properties;
    @Resource private BpmProcessInstanceApi processInstanceApi;

    @Transactional(rollbackFor = Exception.class)
    public PaymentRefundRespVO apply(PaymentRefundApplyReqVO req, Long userId) {
        PaymentTransactionDO tx = requireTransaction(req.getPaymentTransactionId());
        PaymentRefundDO existing = refundMapper.selectByIdempotencyKey(req.getIdempotencyKey());
        if (existing != null) return toVO(existing);
        PaymentIntentDO payment = paymentMapper.selectById(tx.getPaymentOrderId());
        if (payment == null || !"allinpay".equals(payment.getProvider()) || !"paid".equals(payment.getStatus())) throw exception(PAYMENT_REFUND_NOT_ELIGIBLE);
        PaymentRefundDO active = refundMapper.selectActiveByTransaction(tx.getId());
        if (active != null) throw exception(PAYMENT_REFUND_STATE_INVALID);
        PaymentRefundDO refund = build(req, userId, tx, payment, "effective".equals(req.getOrderId() == null ? null : "effective") ? "bpm" : "direct");
        refund.setStatus("approval_pending"); refundMapper.insert(refund);
        BpmProcessInstanceCreateReqDTO process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey("zsjos_payment_refund_approval");
        process.setBusinessKey("payment-refund:" + refund.getId());
        process.setVariables(Map.of("paymentRefundId", refund.getId(), "refundAmount", refund.getRefundAmount()));
        try { refund.setProcessInstanceId(processInstanceApi.createProcessInstance(userId, process)); refundMapper.updateById(refund); }
        catch (RuntimeException ex) { throw exception(PAYMENT_REFUND_PROCESS_UNAVAILABLE); }
        return toVO(refund);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentRefundRespVO direct(PaymentRefundApplyReqVO req, Long userId) {
        PaymentTransactionDO tx = requireTransaction(req.getPaymentTransactionId());
        PaymentIntentDO payment = paymentMapper.selectById(tx.getPaymentOrderId());
        if (payment == null || !"allinpay".equals(payment.getProvider()) || !"paid".equals(payment.getStatus())) throw exception(PAYMENT_REFUND_NOT_ELIGIBLE);
        PaymentRefundDO refund = refundMapper.selectByIdempotencyKey(req.getIdempotencyKey());
        if (refund == null) { if (refundMapper.selectActiveByTransaction(tx.getId()) != null) throw exception(PAYMENT_REFUND_STATE_INVALID); refund = build(req, userId, tx, payment, "direct"); refund.setStatus("submitting"); refundMapper.insert(refund); }
        if ("succeeded".equals(refund.getStatus())) return toVO(refund);
        return submit(refund.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentRefundRespVO refresh(Long id) {
        PaymentRefundDO refund = refundMapper.selectByIdForUpdate(id); if (refund == null) throw exception(PAYMENT_REFUND_NOT_EXISTS);
        if (!("accepted".equals(refund.getStatus()) || "unknown".equals(refund.getStatus()))) return toVO(refund);
        AllinpayClient.GatewayResponse result = new AllinpayClient(properties).queryRefund(refund.getRefundReqsn(), null);
        recordEvent(refund, "refund_query", result, result.isSignatureValid());
        applyResult(refund, result); refund.setLastQueriedAt(LocalDateTime.now()); refundMapper.updateById(refund); return toVO(refund);
    }

    public PaymentRefundRespVO get(Long id) { PaymentRefundDO refund = refundMapper.selectById(id); if (refund == null) throw exception(PAYMENT_REFUND_NOT_EXISTS); return toVO(refund); }

    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "payment-refund.process-result", targetType = "payment-refund")
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(String processInstanceId, Integer processStatus) {
        PaymentRefundDO refund = refundMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentRefundDO>().eq(PaymentRefundDO::getProcessInstanceId, processInstanceId).last("LIMIT 1"));
        if (refund == null || !BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) return;
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus) && "approval_pending".equals(refund.getStatus())) {
            refund.setStatus("submitting"); refundMapper.updateById(refund); submit(refund.getId());
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)) {
            refund.setStatus("failed").setLastErrorMessage("退款审批驳回"); refundMapper.updateById(refund);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void notify(Map<String, Object> payload) {
        String reqsn = payload.get("reqsn") == null ? "" : payload.get("reqsn").toString();
        PaymentRefundDO refund = refundMapper.selectByRefundNo(reqsn);
        if (refund == null) {
            refund = refundMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentRefundDO>().eq(PaymentRefundDO::getRefundReqsn, reqsn).last("LIMIT 1"));
        }
        if (refund == null) throw exception(PAYMENT_REFUND_NOT_EXISTS);
        AllinpayClient.GatewayResponse result = AllinpayClient.GatewayResponse.from(payload, new AllinpayClient(properties).verify(payload));
        recordEvent(refund, "refund_notify", result, result.isSignatureValid());
        applyResult(refund, result); refund.setLastQueriedAt(LocalDateTime.now()); refundMapper.updateById(refund);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentRefundRespVO submit(Long id) {
        PaymentRefundDO refund = refundMapper.selectByIdForUpdate(id); if (refund == null) throw exception(PAYMENT_REFUND_NOT_EXISTS);
        if ("succeeded".equals(refund.getStatus())) return toVO(refund);
        if (!("submitting".equals(refund.getStatus()) || "unknown".equals(refund.getStatus()))) throw exception(PAYMENT_REFUND_STATE_INVALID);
        if ("unknown".equals(refund.getStatus())) return refresh(id);
        int fen = refund.getRefundAmount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        try {
            AllinpayClient.GatewayResponse result = new AllinpayClient(properties).refund(refund.getRefundReqsn(), fen, refund.getOriginalReqsn(), refund.getOriginalTrxId(), refund.getReason());
            recordEvent(refund, "refund_submit", result, result.isSignatureValid()); applyResult(refund, result);
        } catch (RuntimeException ex) { refund.setStatus("unknown").setLastErrorMessage(cut(ex.getMessage(), 500)).setRetryCount((refund.getRetryCount() == null ? 0 : refund.getRetryCount()) + 1); refundMapper.updateById(refund); }
        return toVO(refund);
    }

    private PaymentRefundDO build(PaymentRefundApplyReqVO req, Long userId, PaymentTransactionDO tx, PaymentIntentDO payment, String mode) {
        PaymentRefundDO result = new PaymentRefundDO().setRefundNo("RF" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase())
                .setPurchaseIntentId(payment.getPurchaseIntentId()).setPaymentOrderId(payment.getId()).setPaymentTransactionId(tx.getId()).setOrderId(req.getOrderId())
                .setRefundAmount(tx.getAmount()).setCurrency(tx.getCurrency()).setReason(cut(req.getReason().trim(), 500)).setRequesterUserId(userId)
                .setApprovalMode(mode).setStatus("approval_pending").setProvider("allinpay").setRefundReqsn("RF" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase())
                .setOriginalReqsn(tx.getReqsn()).setOriginalTrxId(tx.getTrxId()).setRetryCount(0).setIdempotencyKey(req.getIdempotencyKey()).setVersion(0);
        result.setTenantId(TenantContextHolder.getRequiredTenantId());
        return result;
    }
    private PaymentTransactionDO requireTransaction(Long id) { PaymentTransactionDO tx = transactionMapper.selectById(id); if (tx == null) throw exception(PAYMENT_REFUND_NOT_ELIGIBLE); return tx; }
    private void applyResult(PaymentRefundDO refund, AllinpayClient.GatewayResponse result) {
        if (!result.isSignatureValid()) { refund.setStatus("unknown").setLastErrorMessage("退款响应验签失败"); return; }
        if ("SUCCESS".equals(result.getRetcode()) && "0000".equals(result.getTrxstatus())) refund.setStatus("succeeded").setRefundedAt(LocalDateTime.now());
        else if ("SUCCESS".equals(result.getRetcode())) refund.setStatus("accepted").setAcceptedAt(LocalDateTime.now());
        else refund.setStatus("failed").setFailedAt(LocalDateTime.now()).setLastErrorMessage(result.getErrmsg());
    }
    private void recordEvent(PaymentRefundDO refund, String type, AllinpayClient.GatewayResponse result, boolean valid) {
        Map<String,Object> body = new LinkedHashMap<>(); body.put("retcode", result.getRetcode()); body.put("trxstatus", result.getTrxstatus()); body.put("reqsn", result.getReqsn()); body.put("trxid", result.getTrxid()); body.put("trxamt", result.getTrxamt());
        PaymentGatewayEventDO event = new PaymentGatewayEventDO().setEventId(type + ":" + refund.getRefundReqsn() + ":" + UUID.randomUUID())
                .setPaymentOrderId(refund.getPaymentOrderId()).setEventType(type).setResponsePayload(JsonUtils.toJsonString(body)).setSignatureValid(valid).setProcessingResult(result.getRetcode());
        event.setTenantId(refund.getTenantId()); eventMapper.insert(event);
    }
    private PaymentRefundRespVO toVO(PaymentRefundDO d) { PaymentRefundRespVO v = new PaymentRefundRespVO(); v.setId(d.getId()).setRefundNo(d.getRefundNo()).setPaymentTransactionId(d.getPaymentTransactionId()).setOrderId(d.getOrderId()).setRefundAmount(d.getRefundAmount()).setCurrency(d.getCurrency()).setReason(d.getReason()).setApprovalMode(d.getApprovalMode()).setStatus(d.getStatus()).setRefundReqsn(d.getRefundReqsn()).setOriginalReqsn(d.getOriginalReqsn()).setOriginalTrxId(d.getOriginalTrxId()).setAcceptedAt(d.getAcceptedAt()).setRefundedAt(d.getRefundedAt()).setLastQueriedAt(d.getLastQueriedAt()).setLastErrorMessage(d.getLastErrorMessage()); return v; }
    private static String cut(String value, int max) { if (value == null) return null; return value.length() <= max ? value : value.substring(0, max); }
}
