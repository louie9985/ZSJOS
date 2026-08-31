package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import jakarta.validation.Valid;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;

@Data
public class SalesOrderSupervisorCursorReqVO {
    private String cursor;
    @Min(1) @Max(100) private Integer limit = 20;
    @NotNull private Boolean handled;
    @Size(max = 100) private String keyword;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
