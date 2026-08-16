package cn.iocoder.yudao.module.eam.controller.admin.repair.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - EAM 维修完成 Request VO")
@Data
public class EamRepairFinishReqVO {

    @Schema(description = "维修记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "维修记录不能为空")
    private Long id;

    @Schema(description = "完成时间，留空取当前时间", example = "2026-08-20T15:00:00")
    private LocalDateTime endTime;

    @Schema(description = "维修费用", example = "800.00")
    private BigDecimal cost;

    @Schema(description = "维修结果", example = "已更换屏幕")
    private String result;

}
