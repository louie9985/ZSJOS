package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesOrderMyCursorReqVO {
    private String cursor;
    @Min(1) @Max(100) private Integer limit = 20;
    @Pattern(regexp = "pending_approval|revision_required|effective", message = "订单状态不正确")
    private String status;
    @Size(max = 100, message = "搜索关键字不能超过 100 个字符") private String keyword;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
