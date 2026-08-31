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
    private String buyerName;
    private String studentName;
    private String studentNatureLabelSnapshot;
    private String studentMobile;
    private String studentWechatId;
    private String provinceName;
    private String cityName;
    private String agreedExamTime;
    private String classType;
    private String servicePeriodLabelSnapshot;
    private String studentSourceLabelSnapshot;
    private BigDecimal totalAmount;
    private LocalDateTime customerPaidAt;
    private String feeModeLabelSnapshot;
    private String paymentMethodLabelSnapshot;
    private String remark;
    private String studentSpecialRequirements;
    private String materialDeliveryContact;
    private String repurchaseReason;
    private String terminationReason;
    private String productSummary;
    private String leadNo;
    private String leadSourceLabel;
    private String leadSourceUserName;
    private String leadOwnerUserName;
    private String leadCategoryLabelSnapshot;
    private String leadSourceChannelLabelSnapshot;
    private String leadProvinceName;
    private String leadCityName;
    private Integer approvalRoundNo;
    private LocalDateTime submittedAt;
    private LocalDateTime effectiveAt;
    private String taskId;
    private String taskDefinitionKey;
    private Integer taskStatus;
    private String taskReason;
    private LocalDateTime taskCreateTime;
    private LocalDateTime taskEndTime;
    private Long supervisorConfirmationId;
    private String supervisorConfirmationStatus;
    private String supervisorRequesterName;
}
