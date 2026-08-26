package cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PositioningCardImportReqVO {
    @NotNull private Long sourceSubmissionId;
    @NotNull private Long accountId;
    @NotNull private Long studentPersonId;
    @NotNull private Long serviceRelationId;
    private Long targetDraftId;
    private Integer version;
    private LocalDate trialEndDate;
}
