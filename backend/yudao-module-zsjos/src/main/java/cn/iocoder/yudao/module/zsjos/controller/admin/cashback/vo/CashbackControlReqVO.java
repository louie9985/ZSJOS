package cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class CashbackControlReqVO {
    @NotNull @Min(0) private Integer version;
    @NotBlank @Size(max = 500) private String reason;
}
