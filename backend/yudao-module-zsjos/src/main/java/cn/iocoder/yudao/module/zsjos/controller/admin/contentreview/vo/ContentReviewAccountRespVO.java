package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import lombok.Data;

/** Account identity and responsibility belong to this approval's frozen snapshot. */
@Data
public class ContentReviewAccountRespVO {
    private Long accountId;
    private String accountName;
    private String accountNo;
    private String platformValue;
    private String platformLabel;
    private Long operatorUserId;
    private String operatorName;
    private boolean operatorNameResolved;
    private Long directorUserId;
    private String directorName;
    private boolean directorNameResolved;
}
