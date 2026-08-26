package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MediaAccountMaintenanceRevisionRespVO {
    private Long id;
    private Integer revisionNo;
    private String currentStatusValue;
    private String currentStatusLabelSnapshot;
    private String stageValue;
    private String stageLabelSnapshot;
    private List<MediaAccountMaintenanceProblemVO> primaryProblems;
    private String executionMeasureValue;
    private String executionMeasureLabelSnapshot;
    private String adjustmentDirection;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> changedFields;
    private Long operatedByUserId;
    private String operatedByUserName;
    private LocalDateTime operatedAt;
}
