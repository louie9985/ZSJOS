package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class PartnerPasswordResetReqVO {
    @NotBlank @Length(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")
    private String password;
}
