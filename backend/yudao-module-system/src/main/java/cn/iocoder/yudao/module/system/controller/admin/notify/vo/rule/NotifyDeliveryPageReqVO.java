package cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyDeliveryPageReqVO extends PageParam {
    private Long ruleId;
    private String sceneCode;
    @Pattern(regexp = "pending|processing|succeeded|failed")
    private String status;
}
