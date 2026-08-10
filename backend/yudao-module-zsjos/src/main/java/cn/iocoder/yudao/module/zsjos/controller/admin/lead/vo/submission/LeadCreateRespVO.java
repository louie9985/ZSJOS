package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LeadCreateRespVO {
    private Long leadId;
    private String outcome;
    private String assignmentStatus;
    private Long pendingAssigneeUserId;
}
