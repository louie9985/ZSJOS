package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;
import cn.iocoder.yudao.module.zsjos.enums.LeadConstants;
import cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class OrderFilterFields {
    private OrderFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, select(Sensitivity.STANDARD, "order.formalOwnerIdentity", EXTRA, "成交归属身份", options(LeadConstants.OWNER_SALES, "销售", LeadConstants.OWNER_EDUCATION, "教务"), orderBind("formal_owner_identity", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.STANDARD, "order.orderNo", IDENTITY, "订单号", orderBind("order_no", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, select(Sensitivity.STANDARD, "order.status", STATUS, "订单状态", options(SalesOrderConstants.STATUS_PENDING_APPROVAL, "审批中", SalesOrderConstants.STATUS_REVISION_REQUIRED, "待修改", SalesOrderConstants.STATUS_EFFECTIVE, "已生效", SalesOrderConstants.STATUS_SUPERSEDED, "已被接续", SalesOrderConstants.STATUS_TERMINATED, "已终止"), orderBind("status", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, select(Sensitivity.STANDARD, "order.type", STATUS, "订单类型", options(SalesOrderConstants.ORDER_TYPE_FIRST_PURCHASE, "首购", SalesOrderConstants.ORDER_TYPE_REPURCHASE, "复购"), orderBind("order_type", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.PERSONAL, "order.buyerName", IDENTITY, "购买方", orderBind("buyer_name", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.PERSONAL, "order.studentName", IDENTITY, "学员姓名", orderBind("student_name", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.PERSONAL, "order.studentMobile", IDENTITY, "学员手机号", orderBind("student_mobile", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.PERSONAL, "order.studentWechatId", IDENTITY, "学员微信号", orderBind("student_wechat_id", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource(Sensitivity.PERSONAL, "order.submitterUserId", PEOPLE, "订单提交人", "visible-users", orderBind("submitter_user_id", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource(Sensitivity.PERSONAL, "order.formalSalesUserId", PEOPLE, "成交负责人", "visible-users", orderBind("formal_sales_user_id", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, number(Sensitivity.FINANCIAL, "order.totalAmount", MONEY, "订单总金额", orderBind("total_amount", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource(Sensitivity.PERSONAL, "order.studentNature", IDENTITY, "学员性质", "dict:zsjos_order_student_nature", orderBind("student_nature", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.PERSONAL, "order.region", IDENTITY, "所在地区", orderExpression("CONCAT_WS('/', %s.province_name, %s.city_name)", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource(Sensitivity.FINANCIAL, "order.feeMode", MONEY, "缴费方式", "dict:zsjos_order_fee_mode", orderBind("fee_mode", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource(Sensitivity.FINANCIAL, "order.paymentMethod", MONEY, "支付方式", "dict:zsjos_order_payment_method", orderBind("payment_method", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, date(Sensitivity.FINANCIAL, "order.customerPaidAt", MONEY, "客户付款时间", orderBind("customer_paid_at", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.STANDARD, "order.classType", PRODUCT, "开通班种", orderBind("class_type", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "order.servicePeriod", PRODUCT, "服务周期", "dict:zsjos_order_service_period", orderBind("service_period", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "order.studentSource", EXTRA, "学生来源", "dict:zsjos_order_student_source", orderBind("student_source", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "order.submittedAt", TIME, "订单提交时间", orderBind("submitted_at", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "order.effectiveAt", TIME, "订单生效时间", orderBind("effective_at", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "order.remark", EXTRA, "订单备注", orderBind("remark", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "order.specialRequirements", EXTRA, "学员特殊要求", orderBind("student_special_requirements", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "order.materialDelivery", EXTRA, "教材邮递联系", orderBind("material_delivery_contact", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, select(Sensitivity.FINANCIAL, "order.hasVoucher", EXTRA, "缴费凭证", options("present", "有附件", "absent", "无附件"), orderExpression("CASE WHEN NULLIF(TRIM(%s.payment_voucher_refs),'') IS NULL THEN 'absent' ELSE 'present' END", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "orderItem.productRef", PRODUCT, "成交产品", "product-catalog:spu", bind("lead", "oi.product_ref", orderFromLead, "order", "oi.product_ref", itemFromOrder, "lead_appeal", "oi.product_ref", orderFromAppeal, "registration", "oi.product_ref", orderFromRegistration, "student", "oi.product_ref", serviceFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "orderItem.skuRef", PRODUCT, "成交 SKU", "product-catalog:sku", bind("lead", "oi.sku_ref", orderFromLead, "order", "oi.sku_ref", itemFromOrder, "lead_appeal", "oi.sku_ref", orderFromAppeal, "registration", "oi.sku_ref", orderFromRegistration, "student", "oi.sku_ref", serviceFromStudent)));

        add(result, text(Sensitivity.STANDARD, "orderItem.product", PRODUCT, "成交商品或课程", bind("lead", "oi.product_snapshot", orderFromLead, "order", "oi.product_snapshot", itemFromOrder, "lead_appeal", "oi.product_snapshot", orderFromAppeal, "registration", "oi.product_snapshot", orderFromRegistration, "student", "oi.product_snapshot", serviceFromStudent)));
        add(result, number(Sensitivity.FINANCIAL, "orderItem.payableAmount", MONEY, "商品应付金额", bind("lead", "oi.payable_amount", orderFromLead, "order", "oi.payable_amount", itemFromOrder, "lead_appeal", "oi.payable_amount", orderFromAppeal, "registration", "oi.payable_amount", orderFromRegistration, "student", "oi.payable_amount", serviceFromStudent)));
        add(result, text(Sensitivity.STANDARD, "order.agreedExamTime", EXTRA, "商定考试时间（文本）", orderBind("agreed_exam_time", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.PERSONAL, "order.giftShippingAddress", EXTRA, "礼品邮寄地址", orderBind("gift_shipping_address", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "order.repurchaseReason", EXTRA, "复购原因", orderBind("repurchase_reason", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "order.terminationReason", EXTRA, "订单终止原因", orderBind("termination_reason", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
        add(result, date(Sensitivity.STANDARD, "order.terminatedAt", TIME, "订单终止时间", orderBind("terminated_at", orderFromLead, orderFromAppeal, orderFromRegistration, serviceFromStudent)));
    }
}
