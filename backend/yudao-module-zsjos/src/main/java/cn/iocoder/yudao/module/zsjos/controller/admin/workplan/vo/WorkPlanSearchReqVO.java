package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkPlanSearchReqVO extends PageParam {
    private Long templateId;
    private String periodType;
    private String status;
    private Long ownerUserId;
    private Long ownerDeptId;
    private LocalDate startDate;
    private LocalDate endDate;
    @Valid private List<WorkPlanDynamicFilterReqVO> dynamicFilters;
}
