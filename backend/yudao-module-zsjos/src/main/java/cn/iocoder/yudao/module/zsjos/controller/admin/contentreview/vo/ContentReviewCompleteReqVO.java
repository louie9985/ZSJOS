package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentReviewCompleteReqVO {
    /** Null retains compatibility with clients that infer the batch outcome. */
    @jakarta.validation.constraints.Pattern(regexp = "APPROVED|RETURNED")
    private String decision;
    @NotNull private Integer expectedVersion;
    @NotBlank private String taskId;
    @NotBlank @Size(max = 2000) private String reason;
}
