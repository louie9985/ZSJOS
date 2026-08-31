package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartnerWecomLoginReqVO {
    @NotBlank
    private String code;
    @NotBlank
    private String state;
    private String platform = "MOBILE";
}
