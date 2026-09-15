package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class MaterialApprovalDecisionReqVO {
    @NotNull private Long versionId;
    @NotBlank private String taskId;
    @NotBlank @Size(max = 1000) private String reason;
}
