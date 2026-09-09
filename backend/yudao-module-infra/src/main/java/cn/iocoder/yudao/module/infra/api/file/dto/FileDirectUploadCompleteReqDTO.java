package cn.iocoder.yudao.module.infra.api.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileDirectUploadCompleteReqDTO {

    @NotBlank(message = "上传令牌不能为空")
    private String uploadToken;
    @NotNull(message = "租户编号不能为空")
    private Long tenantId;
    @NotNull(message = "用户类型不能为空")
    private Integer userType;
    @NotNull(message = "用户编号不能为空")
    private Long userId;
    @NotBlank(message = "上传场景不能为空")
    private String scene;
}
