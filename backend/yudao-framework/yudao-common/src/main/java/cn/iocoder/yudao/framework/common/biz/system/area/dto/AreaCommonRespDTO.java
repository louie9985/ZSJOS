package cn.iocoder.yudao.framework.common.biz.system.area.dto;

import lombok.Data;

@Data
public class AreaCommonRespDTO {

    private Integer id;
    private String name;
    private Integer type;
    private Integer parentId;
    private Integer sort;
    private Integer status;

}
