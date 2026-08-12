package cn.iocoder.yudao.module.zsjos.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("zsjos_order")
@KeySequence("zsjos_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderDO extends TenantBaseDO {
    @TableId private Long id;
    private String orderNo;
    private Long leadId;
    private Long opportunityId;
    private Long personId;
    private String orderType;
    private String status;
    private Long submitterUserId;
    private Long formalSalesUserId;
    private String submitterCenterType;
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
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private LocalDateTime customerPaidAt;
    private String feeMode;
    private String paymentMethod;
    private String remark;
    private String studentSpecialRequirements;
    private String materialDeliveryContact;
    private String paymentVoucherRefs;
    private Long currentApprovalRoundId;
    private Long supersedesOrderId;
    private Long supersededByOrderId;
    private LocalDateTime submittedAt;
    private LocalDateTime effectiveAt;
    private String submissionIdempotencyKey;
    private String repurchaseReason;
    private String terminationReason;
    private LocalDateTime terminatedAt;
    private Integer version;
}
