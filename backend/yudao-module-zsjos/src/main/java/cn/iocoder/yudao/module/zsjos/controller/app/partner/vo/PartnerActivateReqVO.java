package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class PartnerActivateReqVO {

    @NotBlank
    @Mobile
    private String mobile;

    @NotBlank
    @Length(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")
    private String password;

    @NotBlank
    @Length(min = 8, max = 20)
    private String confirmPassword;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{4}\\d{4}$", message = "邀请码格式不正确")
    private String inviteCode;

    private String platform = "MOBILE";
}
