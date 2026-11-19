package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SalesOrderSubmitReqVO {
    @Positive private Long associatedLeadId;
    @Valid private OrderProfileUpdateReqVO profileUpdate;
    private Long purchaseIntentId;
    @Size(max = 5) private List<@Pattern(regexp = "studentNature|servicePeriod|studentSource|feeMode|paymentMethod") String> refreshedDictionaryFields;
    @Size(max = 100) private String buyerName;
    @NotBlank @Size(max = 100) private String studentName;
    @NotBlank @Size(max = 64) private String studentNature;
    @NotBlank(message = "请填写手机号") @Size(max = 32) private String studentMobile;
    @NotBlank(message = "请填写微信号") @Size(max = 64) private String studentWechatId;
    @NotBlank @Size(max = 32) private String provinceCode;
    @NotBlank @Size(max = 100) private String provinceName;
    @NotBlank @Size(max = 32) private String cityCode;
    @Size(max = 100) private String cityName;
    @NotBlank(message = "请选择商定考试月份")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "商定考试月份格式应为 YYYY-MM")
    private String agreedExamMonth;
    @Size(max = 100) private String classType;
    @NotBlank @Size(max = 64) private String servicePeriod;
    @NotBlank @Size(max = 64) private String studentSource;
    @NotNull private LocalDateTime customerPaidAt;
    @NotBlank @Size(max = 64) private String feeMode;
    @NotBlank @Size(max = 64) private String paymentMethod;
    @Size(max = 1000) private String remark;
    @Size(max = 1000) private String studentSpecialRequirements;
    @Size(max = 1000) private String materialDeliveryContact;
    @Size(max = 1000) private String giftShippingAddress;
    private List<String> giftItems;
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
