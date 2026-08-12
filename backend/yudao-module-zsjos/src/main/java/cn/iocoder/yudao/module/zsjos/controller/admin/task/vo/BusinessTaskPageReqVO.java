package cn.iocoder.yudao.module.zsjos.controller.admin.task.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "管理后台 - 我的业务任务分页 Request VO")
@Data
public class BusinessTaskPageReqVO extends PageParam {

    @Schema(description = "任务状态", example = "pending")
    @Pattern(regexp = "pending|done|completed|cancelled", message = "任务状态无效")
    private String status = "pending";

    @Schema(description = "待办时间分组", example = "today")
    private String bucket;

}
