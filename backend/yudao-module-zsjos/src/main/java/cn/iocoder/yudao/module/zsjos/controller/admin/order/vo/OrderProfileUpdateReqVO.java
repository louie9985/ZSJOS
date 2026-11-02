package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class OrderProfileUpdateReqVO {
    @NotBlank @Size(max = 64) private String fingerprint;
    @NotNull @Size(max = 3)
    private List<@Pattern(regexp = "name|mobile|wechatId") String> fields;
}
