package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkPlanSummaryReqVO {
    @NotNull private Integer version;
    @NotBlank @Size(max = 4000) private String summary;
    private List<Long> infraFileIds;
    private Map<String, Object> summaryFields;
}
