package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderPageReqVO extends PageParam {
    @Size(max = 32)
    private String center;
    private Boolean handled;

    @Schema(description = "订单当前状态；all 表示全部，与审批任务处理状态独立")
    @jakarta.validation.constraints.Pattern(regexp = "all|pending_approval|revision_required|effective|terminated|superseded")
    private String orderState;

    @Size(max = 64)
    private String groupKey;

    @Size(max = 64)
    private String optionKey;

    @Size(max = 100)
    private String keyword;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
    private String cursor;
    @Min(1) @Max(100) private Integer limit = 20;
    @Schema(hidden = true) private LocalDateTime cursorTaskTime;
    @Schema(hidden = true) private String cursorTaskId;
}
