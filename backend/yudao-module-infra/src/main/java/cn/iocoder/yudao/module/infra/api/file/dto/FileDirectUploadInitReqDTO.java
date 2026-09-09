package cn.iocoder.yudao.module.infra.api.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FileDirectUploadInitReqDTO {

    @NotNull(message = "租户编号不能为空")
    private Long tenantId;
    @NotNull(message = "用户类型不能为空")
    private Integer userType;
    @NotNull(message = "用户编号不能为空")
    private Long userId;
    @NotBlank(message = "上传场景不能为空")
    private String scene;
    @NotBlank(message = "上传目录不能为空")
    private String directory;
    @NotBlank(message = "文件名不能为空")
    private String name;
    @NotBlank(message = "文件类型不能为空")
    private String contentType;
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long size;
}
