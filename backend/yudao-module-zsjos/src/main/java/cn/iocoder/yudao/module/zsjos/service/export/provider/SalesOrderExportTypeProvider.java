package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderListItemRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderMyPageReqVO;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SalesOrderExportTypeProvider extends AbstractPagedExportTypeProvider<SalesOrderMyPageReqVO, SalesOrderListItemRespVO> {

    @Resource
    private SalesOrderService salesOrderService;

    @Override public String getType() { return "order"; }
    @Override public String getCreatePermission() { return "zsjos:export:order"; }
    @Override protected Class<SalesOrderMyPageReqVO> requestType() { return SalesOrderMyPageReqVO.class; }
    @Override protected PageResult<SalesOrderListItemRespVO> getPage(SalesOrderMyPageReqVO request, Long creatorUserId) {
        return salesOrderService.getMyPage(request, creatorUserId);
    }
    @Override protected List<String> columns() {
        return List.of("订单ID", "订单编号", "订单类型", "状态", "学员姓名", "学员手机号", "订单金额", "提交时间", "生效时间");
    }
    @Override protected List<Object> toRow(SalesOrderListItemRespVO item) {
        return List.of(value(item.getId()), value(item.getOrderNo()), value(item.getOrderType()), value(item.getStatus()),
                value(item.getStudentName()), value(item.getStudentMobile()), value(item.getTotalAmount()),
                value(item.getSubmittedAt()), value(item.getEffectiveAt()));
    }
    @Override protected String sheetName() { return "订单"; }

    private static Object value(Object value) {
        return value == null ? "" : value instanceof Long ? value.toString() : value;
    }
}
