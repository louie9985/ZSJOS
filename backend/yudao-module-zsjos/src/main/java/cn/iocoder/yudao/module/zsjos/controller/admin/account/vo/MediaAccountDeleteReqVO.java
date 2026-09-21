package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MediaAccountDeleteReqVO {
    @NotBlank @Size(max = 500)
    private String reason;
}
