package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadManagementPageReqVO extends PageParam {

    private String keyword;
    private String status;
    private String assignmentStatus;

    @Pattern(regexp = "submitter|owner", message = "客资收件箱视角不正确")
    private String audience;

    @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "客资收件箱分组不正确")
    private String inboxGroup;

    @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "客资收件箱环节不正确")
    private String inboxStage;
    private String sourceChannel;
    private String leadCategory;
    private Long sourceUserId;
    private Long ownerUserId;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] submittedAt;
    @Valid private AdvancedFilterGroupReqVO advancedFilter;
    private String cursor;
    @Min(1) @Max(100) private Integer limit = 20;
    @Schema(hidden = true) private LocalDateTime cursorActivityAt;
    @Schema(hidden = true) private Long cursorId;
}
