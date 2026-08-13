package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import jakarta.validation.Valid;

@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderMyPageReqVO extends PageParam {

    @Pattern(regexp = "pending_approval|revision_required|effective", message = "订单状态不正确")
    private String status;

    @Size(max = 100, message = "搜索关键字不能超过 100 个字符")
    private String keyword;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
