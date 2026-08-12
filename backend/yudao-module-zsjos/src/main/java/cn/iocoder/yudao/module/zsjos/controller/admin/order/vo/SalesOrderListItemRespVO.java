package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SalesOrderListItemRespVO {
    private Long id;
    private String orderNo;
    private Long leadId;
    private Long personId;
    private String orderType;
    private String status;
    private String studentName;
    private String studentMobile;
    private BigDecimal totalAmount;
    private Integer approvalRoundNo;
    private LocalDateTime submittedAt;
    private LocalDateTime effectiveAt;
    private String taskId;
    private String taskDefinitionKey;
    private Integer taskStatus;
    private String taskReason;
    private LocalDateTime taskCreateTime;
    private LocalDateTime taskEndTime;
}
