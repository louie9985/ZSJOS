package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContentReviewBatchSubmitReqVO {
    @NotNull private Integer expectedVersion;
}
