package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductionTicketRespVO {
    private Long id;
    private String ticketNo;
    private Long accountId;
    private Long ownerOperatorUserId;
    private Long assigneeFilmingEditorUserId;
    private Long reviewerUserId;
    private String scriptText;
    private LocalDateTime expectedDeliveredAt;
    private LocalDateTime deadlineAt;
    private Integer maxRevisionCount;
    private Integer revisionCount;
    private Boolean overEntitlement;
    private String status;
    private Integer version;
    private List<String> availableActions;
}
