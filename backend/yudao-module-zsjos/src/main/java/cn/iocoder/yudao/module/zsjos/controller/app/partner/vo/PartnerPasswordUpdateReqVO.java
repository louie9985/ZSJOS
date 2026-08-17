package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class PartnerPasswordUpdateReqVO {
    @NotBlank private String oldPassword;
    @NotBlank @Length(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")
    private String newPassword;
}
