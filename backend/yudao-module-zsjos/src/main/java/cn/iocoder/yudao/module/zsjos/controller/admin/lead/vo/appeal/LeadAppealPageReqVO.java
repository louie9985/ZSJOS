package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadAppealPageReqVO extends PageParam {
    private Boolean handled = false;
    private String cursor;
    @Min(1) @Max(100) private Integer limit = 20;
}
