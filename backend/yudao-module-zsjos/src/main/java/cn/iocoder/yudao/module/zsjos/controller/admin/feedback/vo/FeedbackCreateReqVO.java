package cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Schema(description = "创建反馈 Request VO")
@Data
public class FeedbackCreateReqVO {

    @Schema(description = "动态表单字段值")
    @NotNull
    private Map<String, Object> values;
    @Schema(description = "读取表单时的配置版本")
    @NotNull
    private Integer configVersion;
    @Schema(description = "幂等键")
    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;
}
