package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.Map;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.*;

final class WithdrawalFilterFields {
    private WithdrawalFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, text(Sensitivity.STANDARD, "withdrawal.withdrawalNo", IDENTITY, "提现单号", bind("withdrawal", "w.withdrawal_no", null)));
        add(result, number(Sensitivity.FINANCIAL, "withdrawal.applicationAmount", MONEY, "申请金额（元）", bind("withdrawal", "w.application_amount", null)));
        add(result, number(Sensitivity.FINANCIAL, "withdrawal.approvedAmount", MONEY, "批准金额（元）", bind("withdrawal", "w.approved_amount", null)));
        add(result, number(Sensitivity.FINANCIAL, "withdrawal.availableBalanceSnapshot", MONEY, "申请时可用余额（元）", bind("withdrawal", "w.available_balance_snapshot", null)));
        add(result, text(Sensitivity.PERSONAL, "withdrawal.accountNameSnapshot", IDENTITY, "收款账户名", bind("withdrawal", "w.account_name_snapshot", null)));
        add(result, text(Sensitivity.PERSONAL, "withdrawal.bankNameSnapshot", EXTRA, "开户银行", bind("withdrawal", "w.bank_name_snapshot", null)));
        add(result, text(Sensitivity.PERSONAL, "withdrawal.branchNameSnapshot", EXTRA, "开户支行", bind("withdrawal", "w.branch_name_snapshot", null)));
        add(result, text(Sensitivity.FINANCIAL, "withdrawal.bankTransactionNo", MONEY, "银行流水号", bind("withdrawal", "w.bank_transaction_no", null)));
        add(result, date(Sensitivity.STANDARD, "withdrawal.submittedAt", TIME, "提交时间", bind("withdrawal", "w.submitted_at", null)));
        add(result, date(Sensitivity.STANDARD, "withdrawal.reviewedAt", TIME, "审核时间", bind("withdrawal", "w.reviewed_at", null)));
        add(result, date(Sensitivity.STANDARD, "withdrawal.paidAt", TIME, "打款时间", bind("withdrawal", "w.paid_at", null)));
        add(result, date(Sensitivity.STANDARD, "withdrawal.cancelledAt", TIME, "撤销时间", bind("withdrawal", "w.cancelled_at", null)));
        add(result, text(Sensitivity.FREE_TEXT, "withdrawal.rejectionReason", EXTRA, "驳回原因", bind("withdrawal", "w.rejection_reason", null)));
        add(result, text(Sensitivity.FREE_TEXT, "withdrawal.payoutRemark", EXTRA, "打款备注", bind("withdrawal", "w.payout_remark", null)));
        // 标签一律取自 statusLabel，避免审批中心等其它展示面再维护一份映射而漂移。
        add(result, select(Sensitivity.STANDARD, "withdrawal.status", STATUS, "提现状态", options(STATUS_PENDING, statusLabel(STATUS_PENDING), STATUS_APPROVED, statusLabel(STATUS_APPROVED), STATUS_REJECTED, statusLabel(STATUS_REJECTED), STATUS_PAID, statusLabel(STATUS_PAID), STATUS_CANCELLED, statusLabel(STATUS_CANCELLED)), bind("withdrawal", "w.status", null)));
        add(result, select(Sensitivity.STANDARD, "withdrawal.verificationStatus", STATUS, "核验状态", options(VERIFY_NORMAL, "正常", VERIFY_AMOUNT, "金额异常", VERIFY_DUPLICATE, "重复申请", VERIFY_BALANCE, "余额异常"), bind("withdrawal", "w.verification_status", null)));
        add(result, select(Sensitivity.FINANCIAL, "withdrawal.hasProof", EXTRA, "打款凭证", options("present", "有凭证", "absent", "无凭证"), bind("withdrawal", "CASE WHEN w.proof_file_id IS NULL THEN 'absent' ELSE 'present' END", null)));
        add(result, text(Sensitivity.PERSONAL, "withdrawal.partnerName", PEOPLE, "归属合作方", bind("withdrawal", "fp.name", "SELECT 1 FROM zsjos_partner fp WHERE fp.id=w.partner_id AND fp.tenant_id=w.tenant_id AND fp.deleted=b'0'")));
        add(result, text(Sensitivity.PERSONAL, "withdrawal.applicantName", PEOPLE, "申请人姓名", bind("withdrawal", "w.applicant_user_id", null)));
        String source = "SELECT 1 FROM zsjos_withdrawal_item wi JOIN zsjos_cashback cb ON cb.id=wi.cashback_id AND cb.tenant_id=wi.tenant_id AND cb.deleted=b'0'";
        String tail = " WHERE wi.withdrawal_id=w.id AND wi.tenant_id=w.tenant_id AND wi.deleted=b'0'";
        String lead = source + " JOIN zsjos_lead fl ON fl.id=cb.lead_id AND fl.tenant_id=cb.tenant_id AND fl.deleted=b'0'" + tail;
        String order = source + " JOIN zsjos_order fo ON fo.id=cb.order_id AND fo.tenant_id=cb.tenant_id AND fo.deleted=b'0'" + tail;
        add(result, text(Sensitivity.STANDARD, "withdrawal.leadNo", IDENTITY, "来源客资编号", bind("withdrawal", "fl.lead_no", lead)));
        add(result, text(Sensitivity.PERSONAL, "withdrawal.customerName", IDENTITY, "来源客户姓名", bind("withdrawal", "fl.submitted_name", lead)));
        add(result, text(Sensitivity.STANDARD, "withdrawal.orderNo", IDENTITY, "来源订单号", bind("withdrawal", "fo.order_no", order)));
        add(result, text(Sensitivity.PERSONAL, "withdrawal.studentName", IDENTITY, "来源订单学员", bind("withdrawal", "fo.student_name", order)));
        add(result, text(Sensitivity.STANDARD, "withdrawal.productName", PRODUCT, "返现产品名称快照", bind("withdrawal", "cb.product_name_snapshot", source + tail)));
    }
}
