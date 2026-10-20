package cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;

@Data
@EqualsAndHashCode(callSuper = true)
public class EducationTeamPageReqVO extends PageParam {
    private String keyword;
    private Integer accountStatus;

    @Valid private AdvancedFilterGroupReqVO advancedFilter;
}
