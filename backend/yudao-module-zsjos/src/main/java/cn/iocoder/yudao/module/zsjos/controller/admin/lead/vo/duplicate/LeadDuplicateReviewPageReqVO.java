package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadDuplicateReviewPageReqVO extends PageParam {
    @Pattern(regexp = "pending|completed") private String status;
    @Size(max = 100) private String keyword;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
