package cn.iocoder.yudao.module.zsjos.controller.admin.file.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ZsjosDirectUploadCompleteReqVO {

    @NotBlank(message = "上传令牌不能为空")
    private String uploadToken;
}
