package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProductionTicketCreateContextRespVO {
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
