package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderPoolPageReqVO extends PageParam {
    @Schema(description = "工单类型编码，不传时查询当前用户有资格接收的全部类型")
    @Size(max = 64)
    private String sceneCode;
}
