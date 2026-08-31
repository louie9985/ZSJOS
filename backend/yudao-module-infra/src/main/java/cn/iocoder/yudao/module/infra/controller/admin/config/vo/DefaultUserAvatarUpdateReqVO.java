package cn.iocoder.yudao.module.infra.controller.admin.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Schema(description = "管理后台 - 默认员工头像更新 Request VO")
@Data
public class DefaultUserAvatarUpdateReqVO {

    @Schema(description = "默认员工头像地址；空值表示清空", example = "https://example.com/avatar.png")
    @Size(max = 500, message = "默认员工头像地址不能超过 500 个字符")
    @URL(message = "默认员工头像地址格式不正确")
    private String avatar;

}
