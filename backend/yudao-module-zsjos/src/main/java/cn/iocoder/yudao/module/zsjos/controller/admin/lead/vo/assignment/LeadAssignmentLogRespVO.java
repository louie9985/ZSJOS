package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LeadAssignmentLogRespVO {

    private Long id;
    private String sourceUsers;
    private String targetUsers;
    private String actionType;
    private Long operatorUserId;
    private String operatorName;
    private LocalDateTime createTime;

}
