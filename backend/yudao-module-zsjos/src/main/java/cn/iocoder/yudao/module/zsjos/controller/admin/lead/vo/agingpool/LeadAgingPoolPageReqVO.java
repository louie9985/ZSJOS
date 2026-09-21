package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import jakarta.validation.Valid;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAgingPoolPageReqVO extends PageParam {
    private String keyword;
    @Pattern(regexp = "waiting_assignment|assigned|deal_pending", message = "公海状态不正确")
    private String status;
    private String inboxGroup;
    private String inboxStage;
    @Pattern(regexp = "owned|following", message = "公海关系筛选不正确")
    private String relationScope;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
