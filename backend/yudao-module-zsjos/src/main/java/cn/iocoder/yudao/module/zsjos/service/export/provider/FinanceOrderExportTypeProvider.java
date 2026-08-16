package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.FinanceOrderExportReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.FinanceOrderExportRowRespVO;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.EXPORT_PERMISSION_DENIED;

@Component
public class FinanceOrderExportTypeProvider extends AbstractPagedExportTypeProvider<FinanceOrderExportReqVO, FinanceOrderExportRowRespVO> {
    @Resource private SalesOrderService salesOrderService;
    @Resource private SalesOrderObjectPermissionService permissionService;

    @Override public String getType() { return "finance_order"; }
    @Override public String getCreatePermission() { return "zsjos:export:finance-order"; }
    @Override public void checkCreator(Long userId) {
        if (!permissionService.isFinanceCenterMember(userId)) throw exception(EXPORT_PERMISSION_DENIED);
    }
    @Override protected Class<FinanceOrderExportReqVO> requestType() { return FinanceOrderExportReqVO.class; }
    @Override protected PageResult<FinanceOrderExportRowRespVO> getPage(FinanceOrderExportReqVO request, Long creatorUserId) {
        return salesOrderService.getFinanceExportPage(request, creatorUserId);
    }
    @Override protected List<String> columns() {
        return List.of("订单编号", "首购/复购", "订单状态", "客户姓名", "学员姓名", "手机号", "微信号", "地区",
                "课程汇总", "订单总额", "付款时间", "付款方式", "正式销售", "实际提交人", "提交时间", "生效时间",
                "审批轮次", "报名履约结果", "报名履约审核人", "报名履约审核时间", "财务结果", "财务审核人",
                "财务审核时间", "当前驳回/终止原因");
    }
    @Override protected List<Object> toRow(FinanceOrderExportRowRespVO item) {
        return List.of(v(item.getOrderNo()), v(item.getOrderType()), v(item.getStatus()), v(item.getBuyerName()),
                v(item.getStudentName()), v(item.getStudentMobile()), v(item.getStudentWechatId()), v(item.getRegion()),
                v(item.getCourseSummary()), v(item.getTotalAmount()), v(item.getCustomerPaidAt()), v(item.getPaymentMethod()),
                v(item.getFormalSalesName()), v(item.getSubmitterName()), v(item.getSubmittedAt()), v(item.getEffectiveAt()),
                v(item.getApprovalRoundNo()), v(item.getRegistrationStatus()), v(item.getRegistrationReviewer()),
                v(item.getRegistrationReviewedAt()), v(item.getFinanceStatus()), v(item.getFinanceReviewer()),
                v(item.getFinanceReviewedAt()), v(item.getFinalReason()));
    }
    @Override protected String sheetName() { return "财务订单台账"; }
    private static Object v(Object value) { return value == null ? "" : value; }
}
