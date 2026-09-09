package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentReviewDecisionReqVO {
    @NotBlank private String taskId;
    @NotNull private Integer expectedVersion;
    @NotBlank private String decision;
    @Size(max = 2000) private String comment;
    private Boolean collectMaterial;
}
