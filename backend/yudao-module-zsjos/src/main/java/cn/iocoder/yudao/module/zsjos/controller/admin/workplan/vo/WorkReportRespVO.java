package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class WorkReportRespVO {
    private Long id;
    private Integer revisionNo;
    private String completionSummary;
    private Long submitterUserId;
    private LocalDateTime submittedAt;
    private String confirmationDecision;
    private String confirmationComment;
    private Long confirmedByUserId;
    private LocalDateTime confirmedAt;
    private List<Long> infraFileIds;
    private Map<String, Object> reportFields;
}
