package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class PurchaseIntentSaveDraftReqVO {
    private Long id;
    @NotBlank @Size(max = 32) private String collectionMode;
    @NotBlank @Size(max = 32) private String purchaseType;
    private Long leadId;
    private Long personId;
    private Long opportunityId;
    @NotBlank @Size(max = 64) private String sourceKey;
    @NotNull private Map<String, Object> draft;
    @NotEmpty private List<Item> items;
    @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) private BigDecimal totalAmount;
    @NotBlank @Size(max = 128) private String idempotencyKey;
    private Integer version;

    @Data
    public static class Item {
        @NotBlank @Size(max = 64) private String spuRef;
        @NotBlank @Size(max = 64) private String skuRef;
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) private BigDecimal actualAmount;
        @Size(max = 200) private String skuName;
    }
}
