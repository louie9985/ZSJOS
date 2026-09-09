package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ContentReviewConfigSaveReqVO {
    @NotBlank @Size(max = 128) private String processDefinitionKey;
    @NotBlank @Size(max = 128) private String directorTaskKey;
    @NotBlank @Size(max = 128) private String finalTaskKey;
    @NotBlank @Size(max = 64) private String productionMaterialTypeCode;
    @NotNull private Map<String, String> materialFieldMapping;
    @NotNull private Map<String, Object> materialDefaultValues;
    @NotNull private Integer version;
}
