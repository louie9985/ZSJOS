package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import lombok.Data;

@Data
public class ContentReviewCandidateRespVO {
    private Long id;
    private String contentNo;
    private Long accountId;
    private String title;
    private String topic;
    private String status;
    private Integer currentVersionNo;
    private Long contentVersionId;
}
