package cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "反馈分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackPageReqVO extends PageParam {

    @Schema(description = "反馈类型")
    private String feedbackType;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "当前处理人用户编号")
    private Long assigneeUserId;
    @Schema(description = "编号或标题关键词")
    private String keyword;
    @Schema(description = "创建时间范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
