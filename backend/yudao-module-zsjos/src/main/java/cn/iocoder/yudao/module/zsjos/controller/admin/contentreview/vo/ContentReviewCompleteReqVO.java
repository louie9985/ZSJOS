package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentReviewCompleteReqVO {
    @NotNull private Integer expectedVersion;
    @NotBlank private String taskId;
    @NotBlank @Size(max = 2000) private String reason;
}
