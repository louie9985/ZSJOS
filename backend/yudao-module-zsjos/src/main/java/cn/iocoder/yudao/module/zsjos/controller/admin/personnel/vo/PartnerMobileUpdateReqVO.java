package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartnerMobileUpdateReqVO {
    @NotBlank @Mobile private String mobile;
}
