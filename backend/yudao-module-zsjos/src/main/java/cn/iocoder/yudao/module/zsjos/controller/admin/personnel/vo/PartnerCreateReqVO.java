package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class PartnerCreateReqVO {
    @NotBlank @Size(max = 64) private String partnerNo;
    @NotBlank @Size(max = 100) private String name;
    @NotBlank @Mobile private String mobile;
    @NotBlank @Length(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") private String password;
    @Size(max = 64) private String channelId;
}
