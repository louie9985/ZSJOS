package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class GiftPurchaseSaveReqVO {
    private Long id;
    @NotBlank @Size(max = 100) private String studentName;
    @Size(max = 32) private String studentMobile;
    @Size(max = 64) private String studentWechatId;
    @NotEmpty @Size(max = 100) private List<@NotBlank String> giftItemCodes;
    @NotBlank @Size(max = 1000) private String shippingAddress;
}
