package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import jakarta.validation.Valid;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadClaimPoolPageReqVO extends PageParam {
    private String keyword;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
