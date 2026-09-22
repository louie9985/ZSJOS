package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContentReviewBatchSubmitReqVO {
    @NotNull(message = "缺少审核版本，请刷新后重试") private Integer expectedVersion;
}
