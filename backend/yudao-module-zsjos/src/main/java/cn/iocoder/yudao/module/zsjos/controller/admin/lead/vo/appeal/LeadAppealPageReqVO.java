package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAppealPageReqVO extends PageParam {
    private Boolean handled = false;
    private String cursor;
    @Min(1) @Max(100) private Integer limit = 20;
    @Size(max = 100) private String keyword;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
