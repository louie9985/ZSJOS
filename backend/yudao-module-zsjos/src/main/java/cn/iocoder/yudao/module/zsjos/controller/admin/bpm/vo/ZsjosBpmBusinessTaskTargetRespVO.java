package cn.iocoder.yudao.module.zsjos.controller.admin.bpm.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "管理后台 - ZSJOS BPM 业务任务目标 Response VO")
@Data
public class ZsjosBpmBusinessTaskTargetRespVO {

    @Schema(description = "是否已接入员工端业务页", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean supported;

    @Schema(description = "员工端目标路由")
    private String route;

    @Schema(description = "目标路由查询参数")
    private Map<String, Object> query = new LinkedHashMap<>();

    @Schema(description = "业务类型", example = "sales_order")
    private String bizType;

    @Schema(description = "不可定位或不支持时的用户提示")
    private String message;
}
