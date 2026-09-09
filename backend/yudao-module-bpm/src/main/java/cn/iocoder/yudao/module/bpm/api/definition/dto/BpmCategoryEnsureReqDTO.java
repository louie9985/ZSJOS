package cn.iocoder.yudao.module.bpm.api.definition.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BpmCategoryEnsureReqDTO {

    private String name;
    private String code;
    private String description;
    private Integer status;
    private Integer sort;
}
