package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MediaAccountCalendarItemRespVO {
    private Long id;
    private String accountNo;
    private String nickname;
    private String platformLabelSnapshot;
    private Long studentPersonId;
    private String studentName;
    private Long directorUserId;
    private String directorUserName;
    private Long operatorUserId;
    private String operatorUserName;
    private String currentStatusValue;
    private String currentStatusLabelSnapshot;
    private String stageValue;
    private String stageLabelSnapshot;
    private LocalDate startDate;
    private LocalDate endDate;
}
