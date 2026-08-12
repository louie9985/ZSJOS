package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class WorkPlanSummaryRespVO {
    private Long id;
    private String summary;
    private Long submitterUserId;
    private LocalDateTime submittedAt;
    private List<Long> infraFileIds;
    private Map<String, Object> summaryFields;
}
