package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class PartnerLoginReqVO {
    @NotBlank @Mobile private String mobile;
    @NotBlank @Length(min = 4, max = 100) private String password;
    private String platform = "MOBILE";
}
