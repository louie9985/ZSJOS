package cn.iocoder.yudao.module.zsjos.controller.admin.file.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ZsjosDirectUploadInitReqVO {

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名不能超过 255 个字符")
    private String name;

    @NotBlank(message = "文件类型不能为空")
    @Size(max = 100, message = "文件类型不能超过 100 个字符")
    private String contentType;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long size;
}
