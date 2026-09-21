package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderFieldDefinition;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderFileRespVO;

@Data
public class ProductionTicketRespVO {
    private Long id;
    private String ticketNo;
    private Long accountId;
    private List<Long> accountIds;
    private List<Map<String, Object>> accounts;
    private Long ownerOperatorUserId;
    private Long assigneeFilmingEditorUserId;
    private Long reviewerUserId;
    private Long positioningSubmissionId;
    private Map<String, Object> dispatchContext;
    private String scriptText;
    private LocalDateTime expectedDeliveredAt;
    private LocalDateTime deadlineAt;
    private Integer maxRevisionCount;
    private Integer revisionCount;
    private Boolean overEntitlement;
    private String status;
    private Integer version;
    private List<String> availableActions;
    /** Frozen operator form projected from the linked production envelope. */
    private List<WorkOrderFieldDefinition> formFields;
    private Map<String, Object> formValues;
    private List<WorkOrderFileRespVO> requestAttachments;
    private String submitterName;
}
