package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackRespVO;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CashbackExportTypeProvider extends AbstractPagedExportTypeProvider<CashbackPageReqVO, CashbackRespVO> {

    @Resource
    private CashbackService cashbackService;

    @Override public String getType() { return "cashback"; }
    @Override public String getCreatePermission() { return "zsjos:export:cashback"; }
    @Override protected Class<CashbackPageReqVO> requestType() { return CashbackPageReqVO.class; }
    @Override protected PageResult<CashbackRespVO> getPage(CashbackPageReqVO request, Long creatorUserId) {
        return cashbackService.getPage(request, null);
    }
    @Override protected List<String> columns() {
        return List.of("返现ID", "返现编号", "类型", "状态", "受益人ID", "客资ID", "订单ID", "产品",
                "基准金额", "返现比例", "返现金额", "可提现时间", "结算时间");
    }
    @Override protected List<Object> toRow(CashbackRespVO item) {
        return List.of(value(item.getId()), value(item.getCashbackNo()), value(item.getType()), value(item.getStatus()),
                value(item.getBeneficiaryUserId()), value(item.getLeadId()), value(item.getOrderId()),
                value(item.getProductNameSnapshot()), value(item.getBaseAmount()), value(item.getRateSnapshot()),
                value(item.getAmount()), value(item.getAvailableAt()), value(item.getSettledAt()));
    }
    @Override protected String sheetName() { return "返现"; }

    private static Object value(Object value) {
        return value == null ? "" : value instanceof Long ? value.toString() : value;
    }
}
