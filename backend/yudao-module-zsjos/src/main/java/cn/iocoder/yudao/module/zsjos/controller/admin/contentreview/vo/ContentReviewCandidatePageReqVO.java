package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContentReviewCandidatePageReqVO extends PageParam {
    private String keyword;
}
