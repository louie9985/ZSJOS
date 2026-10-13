package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContentReviewBatchSubmitReqVO {
    // Cancel-draft also consumes this VO; required selection is validated only by submit.
    private Long directorUserId;
    private String processDefinitionId;
    @NotNull(message = "缺少审核版本，请刷新后重试") private Integer expectedVersion;
}
