package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

import java.time.LocalDate;

@Data
public class WorkPlanPageReqVO extends PageParam {
    private String periodType;
    private String status;
    private Long templateId;
    private Long ownerUserId;
    private Long ownerDeptId;
    private LocalDate startDate;
    private LocalDate endDate;
}
