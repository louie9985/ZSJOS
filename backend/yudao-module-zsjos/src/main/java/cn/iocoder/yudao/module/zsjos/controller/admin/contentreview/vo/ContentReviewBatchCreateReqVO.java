package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ContentReviewBatchCreateReqVO {
    @NotEmpty
    @Size(min = 1, max = 20)
    private List<Long> contentVersionIds;
}
