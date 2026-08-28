package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkOrderTimelineRespVO {
    private Long id;
    private Integer roundNo;
    private String operation;
    private String fromStatus;
    private String toStatus;
    private Long operatorUserId;
    private String operatorName;
    private String reason;
    private String resultRemark;
    private List<Long> attachmentIds;
    private LocalDateTime operatedAt;
}
