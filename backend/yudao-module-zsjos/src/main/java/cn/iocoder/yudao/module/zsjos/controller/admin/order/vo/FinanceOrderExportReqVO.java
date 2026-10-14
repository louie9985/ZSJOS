package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceOrderExportReqVO extends PageParam {
    @Size(max = 64)
    @Pattern(regexp = "pending_approval|revision_required|effective|superseded|terminated",
            message = "订单状态不正确")
    private String status;
    @Size(max = 100)
    private String keyword;
    @Valid
    private AdvancedFilterGroupReqVO advancedFilter;
}
