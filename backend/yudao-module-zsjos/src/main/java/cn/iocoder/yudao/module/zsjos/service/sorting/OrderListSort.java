package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderListItemRespVO;
import java.util.Map;

public final class OrderListSort {
    private OrderListSort() {}
    public static BusinessListSort<SalesOrderListItemRespVO> create() {
        return new BusinessListSort<SalesOrderListItemRespVO>(SalesOrderListItemRespVO::getId)
                .field("orderNo", SalesOrderListItemRespVO::getOrderNo)
                .field("orderType", row -> label(Map.of("first_purchase", "首购", "repurchase", "复购"), row.getOrderType(), "未知类型"))
                .field("status", row -> label(Map.of("pending_approval", "待审核", "revision_required", "已驳回待修改", "effective", "已通过", "superseded", "已被重提", "terminated", "已终止"), row.getStatus(), "未知状态"))
                .field("approvalRoundNo", SalesOrderListItemRespVO::getApprovalRoundNo)
                .field("taskDefinitionKey", row -> label(Map.of("registrationReview", "报名履约中心", "financeReview", "财务结算中心"), row.getTaskDefinitionKey(), null))
                .field("taskStatus", row -> row.getTaskStatus() == null ? null : Map.of(0,"待审批",1,"审批中",2,"已通过",3,"已拒绝",4,"已取消",5,"已退回",7,"通过中").getOrDefault(row.getTaskStatus(), "未知状态"))
                .field("supervisorConfirmationStatus", row -> label(Map.of("pending","等待确认","confirmed","已确认","rejected","已驳回","cancelled","已取消"), row.getSupervisorConfirmationStatus(), "未知状态"))
                .field("supervisorRequesterName", SalesOrderListItemRespVO::getSupervisorRequesterName)
                .field("buyerName", SalesOrderListItemRespVO::getBuyerName)
                .field("studentName", SalesOrderListItemRespVO::getStudentName)
                .field("studentNatureLabelSnapshot", SalesOrderListItemRespVO::getStudentNatureLabelSnapshot)
                .field("studentMobile", SalesOrderListItemRespVO::getStudentMobile)
                .field("studentWechatId", SalesOrderListItemRespVO::getStudentWechatId)
                .field("orderRegion", row -> BusinessListSort.join(" / ", row.getProvinceName(), row.getCityName()))
                .field("agreedExamTime", SalesOrderListItemRespVO::getAgreedExamTime)
                .field("classType", SalesOrderListItemRespVO::getClassType)
                .field("servicePeriodLabelSnapshot", SalesOrderListItemRespVO::getServicePeriodLabelSnapshot)
                .field("studentSourceLabelSnapshot", SalesOrderListItemRespVO::getStudentSourceLabelSnapshot)
                .field("productSummary", SalesOrderListItemRespVO::getProductSummary)
                .field("totalAmount", SalesOrderListItemRespVO::getTotalAmount)
                .field("customerPaidAt", SalesOrderListItemRespVO::getCustomerPaidAt)
                .field("feeModeLabelSnapshot", SalesOrderListItemRespVO::getFeeModeLabelSnapshot)
                .field("paymentMethodLabelSnapshot", SalesOrderListItemRespVO::getPaymentMethodLabelSnapshot)
                .field("remark", SalesOrderListItemRespVO::getRemark)
                .field("studentSpecialRequirements", SalesOrderListItemRespVO::getStudentSpecialRequirements)
                .field("materialDeliveryContact", SalesOrderListItemRespVO::getMaterialDeliveryContact)
                .field("repurchaseReason", SalesOrderListItemRespVO::getRepurchaseReason)
                .field("terminationReason", SalesOrderListItemRespVO::getTerminationReason)
                .field("leadNo", SalesOrderListItemRespVO::getLeadNo)
                .field("leadSourceLabel", SalesOrderListItemRespVO::getLeadSourceLabel)
                .field("leadSourceUserName", SalesOrderListItemRespVO::getLeadSourceUserName)
                .field("leadOwnerUserName", SalesOrderListItemRespVO::getLeadOwnerUserName)
                .field("formalOwnerIdentityLabel", SalesOrderListItemRespVO::getFormalOwnerIdentityLabel)
                .field("leadCategoryLabelSnapshot", SalesOrderListItemRespVO::getLeadCategoryLabelSnapshot)
                .field("leadSourceChannelLabelSnapshot", SalesOrderListItemRespVO::getLeadSourceChannelLabelSnapshot)
                .field("leadRegion", row -> BusinessListSort.join(" / ", row.getLeadProvinceName(), row.getLeadCityName()))
                .field("submittedAt", SalesOrderListItemRespVO::getSubmittedAt)
                .field("effectiveAt", SalesOrderListItemRespVO::getEffectiveAt)
                .field("taskCreateTime", SalesOrderListItemRespVO::getTaskCreateTime)
                .field("taskEndTime", SalesOrderListItemRespVO::getTaskEndTime)
                .field("taskReason", SalesOrderListItemRespVO::getTaskReason);
    }
    private static String label(Map<String,String> labels, String value, String fallback) {
        return value == null || value.isBlank() ? null : labels.getOrDefault(value, fallback);
    }
}
