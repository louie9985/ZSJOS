package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SalesOrderSubmitReqVO {
    @Size(max = 100) private String buyerName;
    @NotBlank @Size(max = 100) private String studentName;
    @NotBlank @Size(max = 64) private String studentNature;
    @Size(max = 32) private String studentMobile;
    @Size(max = 64) private String studentWechatId;
    @NotBlank @Size(max = 32) private String provinceCode;
    @NotBlank @Size(max = 100) private String provinceName;
    @NotBlank @Size(max = 32) private String cityCode;
    @Size(max = 100) private String cityName;
    @Size(max = 100) private String agreedExamTime;
    @Size(max = 100) private String classType;
    @NotBlank @Size(max = 64) private String servicePeriod;
    @NotBlank @Size(max = 64) private String studentSource;
    @NotNull private LocalDateTime customerPaidAt;
    @NotBlank @Size(max = 64) private String feeMode;
    @NotBlank @Size(max = 64) private String paymentMethod;
    @Size(max = 1000) private String remark;
    @Size(max = 1000) private String studentSpecialRequirements;
    @Size(max = 1000) private String materialDeliveryContact;
    @NotEmpty @Size(max = 50) private List<@Valid Item> items;
    @NotEmpty @Size(max = 6) private List<@Valid Attachment> paymentVouchers;
    @NotBlank @Size(max = 128) private String idempotencyKey;

    @Data
    public static class Item {
        @NotBlank @Size(max = 64) private String spuRef;
        @NotBlank @Size(max = 64) private String skuRef;
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) private BigDecimal actualAmount;
    }

    @Data
    public static class Attachment {
        @NotNull private Long infraFileId;
    }
}
