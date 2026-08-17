package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerProfileUpdateReqVO {
    @NotBlank @Size(max = 100) private String nickname;
    @Email @Size(max = 100) private String email;
    @Size(max = 512) private String avatar;
    private Integer sex;
}
