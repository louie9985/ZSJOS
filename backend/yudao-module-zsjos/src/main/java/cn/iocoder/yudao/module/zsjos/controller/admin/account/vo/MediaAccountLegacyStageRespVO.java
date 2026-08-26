package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MediaAccountLegacyStageRespVO {
    private Long id;
    private String fromStage;
    private String toStage;
    private String direction;
    private String judgmentBasis;
    private Long judgedByUserId;
    private String judgedByUserName;
    private LocalDateTime judgedAt;
}
