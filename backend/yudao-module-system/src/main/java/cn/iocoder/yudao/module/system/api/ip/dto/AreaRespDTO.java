package cn.iocoder.yudao.module.system.api.ip.dto;

import lombok.Data;

@Data
public class AreaRespDTO {

    private Integer id;
    private String name;
    private String selectionCode;
    private Integer type;
    private Integer parentId;
    private Integer sort;
    private Integer status;
    private Boolean leafSelectable;

}
