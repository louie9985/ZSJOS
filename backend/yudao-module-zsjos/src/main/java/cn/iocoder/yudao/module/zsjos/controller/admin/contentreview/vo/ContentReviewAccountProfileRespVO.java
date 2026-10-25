package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import lombok.Data;

@Data
public class ContentReviewAccountProfileRespVO {
    private Long id;
    private Long ownerOperatorUserId;
    private Long directorUserId;
    private String accountNo;
    private String nickname;
    private String platformAccountId;
    private String platformValue;
    private String platformLabel;
    private String sStage;
    private String sStageLabel;
    private Long sStageEnteredAt;
    private Long sStageDurationAsOf;
    private Long sStageDurationDays;
    private String sStageTimingSource;
    private String currentStatusValue;
    private String currentStatusLabel;
    private String operatorName;
    private String directorName;
    private String productGoal;
    private String productFormLabel;
    private String publishFrequency;
    private String bottleneckLabel;
}
