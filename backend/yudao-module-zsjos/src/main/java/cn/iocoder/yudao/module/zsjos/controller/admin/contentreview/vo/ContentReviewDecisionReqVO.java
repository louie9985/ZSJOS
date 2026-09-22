package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentReviewDecisionReqVO {
    @NotBlank(message = "当前审核任务不可用，请刷新待办") private String taskId;
    @NotNull(message = "缺少审核版本，请刷新后重试") private Integer expectedVersion;
    @NotBlank(message = "请选择审核结论") private String decision;
    @Size(max = 2000, message = "审核意见不能超过 2000 字") private String comment;
    private Boolean collectMaterial;
}
