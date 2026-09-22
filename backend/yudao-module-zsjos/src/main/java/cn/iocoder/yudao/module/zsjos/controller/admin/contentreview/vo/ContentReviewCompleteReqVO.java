package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentReviewCompleteReqVO {
    /** Null retains compatibility with clients that infer the batch outcome. */
    @jakarta.validation.constraints.Pattern(regexp = "APPROVED|RETURNED", message = "请选择有效的整批审核结论：通过或退回")
    private String decision;
    @NotNull(message = "缺少审核版本，请刷新后重试") private Integer expectedVersion;
    @NotBlank(message = "当前审核任务不可用，请刷新待办") private String taskId;
    @NotBlank(message = "请填写整批审核意见") @Size(max = 2000, message = "审核意见不能超过 2000 字") private String reason;
}
