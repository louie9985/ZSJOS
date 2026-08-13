package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadQualificationExceptionPageReqVO extends PageParam {
    @Pattern(regexp = "suspended|recycle_pending", message = "异常客资类型无效")
    private String type = "suspended";
}
