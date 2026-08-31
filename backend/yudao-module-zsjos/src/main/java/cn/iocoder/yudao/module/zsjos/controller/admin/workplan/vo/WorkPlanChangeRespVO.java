package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkPlanChangeRespVO {
    private Long id;
    private String subjectType;
    private Long subjectId;
    private String changeType;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String reason;
    private Long operatorUserId;
    private LocalDateTime changedAt;
}
