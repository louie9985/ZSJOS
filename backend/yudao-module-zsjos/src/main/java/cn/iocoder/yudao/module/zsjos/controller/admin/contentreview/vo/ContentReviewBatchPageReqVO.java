package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContentReviewBatchPageReqVO extends PageParam {
    private String keyword;
    private String status;
    private Long accountId;
    private Boolean mine;
}
