package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;

@Data
@EqualsAndHashCode(callSuper = true)
public class SubordinateSalesPageReqVO extends PageParam {
    private String keyword;
    private Integer accountStatus;
    private String presence;
    private Boolean accepting;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
