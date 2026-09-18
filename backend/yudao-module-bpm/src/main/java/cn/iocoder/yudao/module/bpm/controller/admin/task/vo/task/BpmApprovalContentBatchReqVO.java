package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 审批业务摘要批量查询 Request VO")
@Data
public class BpmApprovalContentBatchReqVO {

    @Schema(description = "流程任务编号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "任务编号列表不能为空")
    private List<String> taskIds;

    @Schema(description = "视图：todo（待办）或 done（已办）", example = "todo")
    private String view = "todo";
}
