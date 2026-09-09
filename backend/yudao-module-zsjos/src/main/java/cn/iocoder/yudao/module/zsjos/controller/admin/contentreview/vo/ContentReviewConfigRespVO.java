package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import lombok.Data;

import java.util.Map;

@Data
public class ContentReviewConfigRespVO {
    private Long id;
    private String processDefinitionKey;
    private String directorTaskKey;
    private String finalTaskKey;
    private String productionMaterialTypeCode;
    private Map<String, String> materialFieldMapping;
    private Map<String, Object> materialDefaultValues;
    private Integer version;
}
