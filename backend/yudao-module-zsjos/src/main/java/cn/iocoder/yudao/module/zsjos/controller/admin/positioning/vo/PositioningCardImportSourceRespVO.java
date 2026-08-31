package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PositioningCardImportSourceRespVO {
    private Long submissionId;
    private Long cardId;
    private String cardNo;
    private Long accountId;
    private String accountLabel;
    private Integer submissionNo;
    private String status;
    private LocalDateTime submittedAt;
    private Boolean sameAccount;
}
