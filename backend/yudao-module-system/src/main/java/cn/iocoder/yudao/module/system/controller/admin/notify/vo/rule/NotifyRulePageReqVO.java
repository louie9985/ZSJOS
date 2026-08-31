package cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 业务通知规则分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyRulePageReqVO extends PageParam {

    private String name;
    private String sceneCode;
    private Integer status;
}
