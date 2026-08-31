package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderFieldDefinition;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProductionTicketCreateContextRespVO {
    private String sceneCode;
    private String templateName;
    private List<String> allowedAssignmentTypes;
    private List<Long> targetDeptIds;
    private List<WorkOrderFieldDefinition> fields;
    private Boolean canCreate;
    private String unavailableReason;
    private Long accountId;
    private String accountNo;
    private String accountName;
    private String platformLabel;
    private String studentName;
    private List<MediaAccountDetailSnapshotVO> accountFields;
    private Long positioningSubmissionId;
    private Map<String, Object> positioning;
    private List<LeadAssignmentUserRespVO> assigneeCandidates;
}
