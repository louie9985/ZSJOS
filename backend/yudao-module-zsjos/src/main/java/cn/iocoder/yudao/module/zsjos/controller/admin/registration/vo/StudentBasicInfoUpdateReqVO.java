package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentBasicInfoUpdateReqVO {
    @NotBlank @Size(max = 100)
    private String name;
    @Size(max = 32)
    private String mobile;
    @Size(max = 64)
    private String wechatId;
    @NotBlank @Size(max = 500)
    private String reason;

    @AssertTrue(message = "手机号和微信号至少填写一个")
    public boolean isContactPresent() {
        return mobile != null && !mobile.isBlank() || wechatId != null && !wechatId.isBlank();
    }
}
