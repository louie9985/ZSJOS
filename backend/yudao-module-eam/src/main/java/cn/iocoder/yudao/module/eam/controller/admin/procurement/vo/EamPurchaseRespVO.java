package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EamPurchaseRespVO {
    private Long id;
    private String no;
    private Integer status;
    private Integer paymentMode;
    private String paymentModeLabelSnapshot;
    private String supplierNameSnapshot;
    private String supplierContactSnapshot;
    private BigDecimal estimatedAmount;
    private BigDecimal actualAmount;
    private LocalDate expectedArrivalDate;
    private String processInstanceId;
    private Integer expenseStatus;
    private String expenseProcessInstanceId;
    private Long applicantUserId;
    private List<String> fileUrls;
    private String remark;
    private LocalDateTime createTime;
    private List<EamPurchaseItemRespVO> items;
}
