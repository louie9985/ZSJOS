package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class MaterialSaveReqVO {
    @NotNull private Long materialTypeId;
    @Size(max = 255) private String title;
    private Long coverFileId;
    @Size(max = 2000) private String summary;
    @NotNull private Map<String, Object> values;
    private Boolean pinned;
    private Integer priority;
    private Integer expectedMaterialVersion;
}
