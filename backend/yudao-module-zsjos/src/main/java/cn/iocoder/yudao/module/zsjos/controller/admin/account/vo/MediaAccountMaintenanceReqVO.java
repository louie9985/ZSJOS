package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class MediaAccountMaintenanceReqVO {
    @NotNull private Integer version;
    private String currentStatusValue;
    private String stageValue;
    @Size(max = 10) private List<String> primaryProblemValues;
    private String executionMeasureValue;
    @Size(max = 1000) private String adjustmentDirection;
    private LocalDate startDate;
    private LocalDate endDate;
}
