package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.Data;

@Data
public class WorkPlanTypeRespVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Integer sort;
}
