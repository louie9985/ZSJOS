package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class EamPurchaseCreateReqVO {
    @NotNull(message = "付款方式不能为空")
    private Integer paymentMode;
    private String supplierName;
    private String supplierContact;
    private BigDecimal estimatedAmount;
    private LocalDate expectedArrivalDate;
    private String remark;
    private List<String> fileUrls;
    @Valid
    @NotEmpty(message = "采购明细不能为空")
    private List<EamPurchaseItemReqVO> items;
}
