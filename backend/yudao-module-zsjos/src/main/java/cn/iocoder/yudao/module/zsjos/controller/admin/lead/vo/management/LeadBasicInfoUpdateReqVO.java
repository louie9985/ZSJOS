package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class LeadBasicInfoUpdateReqVO {
    @NotBlank @Size(max = 100) private String name;
    @Size(max = 32) private String mobile;
    @Size(max = 64) private String wechatId;
    @NotBlank @Size(max = 32) private String provinceCode;
    @NotBlank @Size(max = 32) private String cityCode;
    @Size(max = 64) private String leadCategory;
    @Valid @Size(min = 1, max = 20) private List<LeadProductReqVO> intendedProducts;
    @NotBlank @Size(max = 500) private String reason;

    @AssertTrue(message = "手机号和微信号至少填写一个")
    public boolean isContactPresent() {
        return mobile != null && !mobile.isBlank() || wechatId != null && !wechatId.isBlank();
    }
}
