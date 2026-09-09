package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import lombok.Data;

import java.util.Map;

@Data
public class MaterialReferencePreviewRespVO {
    private Long materialVersionId;
    private Long targetContentVersionId;
    private Map<String, Object> before;
    private Map<String, Object> after;
}
