package cn.iocoder.yudao.module.zsjos.controller.admin.export.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExportTaskCreateReqVO {
    @NotBlank @Size(max = 32) private String exportType;
    @Size(max = 10000) private String filterJson;
}
