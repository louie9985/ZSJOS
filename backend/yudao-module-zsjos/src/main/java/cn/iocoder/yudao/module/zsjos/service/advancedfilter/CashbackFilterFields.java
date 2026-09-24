package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.Map;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.*;

final class CashbackFilterFields {
    private CashbackFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, text(Sensitivity.STANDARD, "cashback.cashbackNo", IDENTITY, "返现编号", bind("cashback", "c.cashback_no", null)));
        add(result, text(Sensitivity.STANDARD, "cashback.productName", PRODUCT, "产品名称快照", bind("cashback", "c.product_name_snapshot", null)));
        add(result, number(Sensitivity.FINANCIAL, "cashback.baseAmount", MONEY, "返现基数（元）", bind("cashback", "c.base_amount", null)));
        add(result, number(Sensitivity.FINANCIAL, "cashback.rateSnapshot", MONEY, "返现比例（小数）", bind("cashback", "c.rate_snapshot", null)));
        add(result, number(Sensitivity.FINANCIAL, "cashback.amount", MONEY, "返现金额（元）", bind("cashback", "c.amount", null)));
        add(result, number(Sensitivity.STANDARD, "cashback.observationDaysSnapshot", TIME, "观察期（天）", bind("cashback", "c.observation_days_snapshot", null)));
        add(result, date(Sensitivity.STANDARD, "cashback.generatedAt", TIME, "返现生成时间", bind("cashback", "c.generated_at", null)));
        add(result, date(Sensitivity.STANDARD, "cashback.availableAt", TIME, "可提现时间", bind("cashback", "c.available_at", null)));
        add(result, date(Sensitivity.STANDARD, "cashback.settledAt", TIME, "结算时间", bind("cashback", "c.settled_at", null)));
        add(result, date(Sensitivity.STANDARD, "cashback.cancelledAt", TIME, "取消时间", bind("cashback", "c.cancelled_at", null)));
        add(result, text(Sensitivity.FREE_TEXT, "cashback.cancelReason", EXTRA, "取消原因", bind("cashback", "c.cancel_reason", null)));
        add(result, select(Sensitivity.STANDARD, "cashback.type", STATUS, "返现类型", options(TYPE_VALID, "有效返现", TYPE_DEAL, "成交返现"), bind("cashback", "c.type", null)));
        add(result, select(Sensitivity.STANDARD, "cashback.status", STATUS, "返现状态", options(STATUS_PENDING, "待结算", STATUS_AVAILABLE, "可提现", STATUS_WITHDRAWING, "提现中", STATUS_WITHDRAWN, "已提现", STATUS_CANCELLED, "已取消"), bind("cashback", "c.status", null)));
        add(result, text(Sensitivity.STANDARD, "cashback.leadNo", IDENTITY, "客资编号", bind("cashback", "fl.lead_no", "SELECT 1 FROM zsjos_lead fl WHERE fl.id=c.lead_id AND fl.tenant_id=c.tenant_id AND fl.deleted=b'0'")));
        add(result, text(Sensitivity.STANDARD, "cashback.orderNo", IDENTITY, "订单号", bind("cashback", "fo.order_no", "SELECT 1 FROM zsjos_order fo WHERE fo.id=c.order_id AND fo.tenant_id=c.tenant_id AND fo.deleted=b'0'")));
        add(result, text(Sensitivity.PERSONAL, "cashback.partnerName", PEOPLE, "合作方名称", bind("cashback", "fp.name", "SELECT 1 FROM zsjos_partner fp WHERE fp.id=c.partner_id AND fp.tenant_id=c.tenant_id AND fp.deleted=b'0'")));
        add(result, text(Sensitivity.PERSONAL, "cashback.beneficiaryName", PEOPLE, "返现受益人姓名", bind("cashback", "c.beneficiary_user_id", null)));
        add(result, text(Sensitivity.PERSONAL, "cashback.customerName", IDENTITY, "客户姓名", bind("cashback", "fl.submitted_name", "SELECT 1 FROM zsjos_lead fl WHERE fl.id=c.lead_id AND fl.tenant_id=c.tenant_id AND fl.deleted=b'0'")));
        add(result, text(Sensitivity.PERSONAL, "cashback.studentName", IDENTITY, "订单学员姓名", bind("cashback", "fo.student_name", "SELECT 1 FROM zsjos_order fo WHERE fo.id=c.order_id AND fo.tenant_id=c.tenant_id AND fo.deleted=b'0'")));
    }
}
