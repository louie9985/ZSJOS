package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SalesOrderRespVO {
    private Long id;
    private String orderNo;
    private Long leadId;
    private Long opportunityId;
    private String status;
    private String orderType;
    private Long personId;
    private Long formalSalesUserId;
    private Long submitterUserId;
    private Long supersedesOrderId;
    private Long supersededByOrderId;
    private String buyerName;
    private String studentName;
    private String studentNature;
    private String studentMobile;
    private String studentWechatId;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String agreedExamTime;
    private String classType;
    private String servicePeriod;
    private String studentSource;
    private BigDecimal totalAmount;
    private LocalDateTime customerPaidAt;
    private String feeMode;
    private String paymentMethod;
    private String remark;
    private String studentSpecialRequirements;
    private String materialDeliveryContact;
    private List<ItemVO> items;
    private List<AttachmentVO> paymentVouchers;
    private Integer approvalRoundNo;
    private String approvalRoundStatus;
    private String processInstanceId;
    private String taskId;
    private String taskDefinitionKey;
    private Integer taskStatus;
    private String taskReason;
    private LocalDateTime taskCreateTime;
    private LocalDateTime taskEndTime;
    private String decisionReason;
    private Boolean canRevise;
    private Boolean canTerminate;
    private String repurchaseReason;
    private String terminationReason;
    private Integer version;
    private Long currentApprovalRoundId;
    private Integer approvalRoundVersion;
    private LocalDateTime submittedAt;
    private LocalDateTime effectiveAt;
    private ApprovalStatusVO registrationApproval;
    private ApprovalStatusVO financeApproval;

    @Data
    public static class ApprovalStatusVO {
        private String status;
        private Long reviewerUserId;
        private String reviewerUserName;
        private LocalDateTime createTime;
        private LocalDateTime endTime;
    }

    @Data
    public static class ItemVO {
        private Long id;
        private String productRef;
        private String skuRef;
        private String productName;
        private String skuName;
        private List<String> categoryPath;
        private Map<String, String> attrValues;
        private BigDecimal actualAmount;
    }

    @Data
    public static class AttachmentVO {
        private Long infraFileId;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
    }
}
