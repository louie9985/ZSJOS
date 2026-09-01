package cn.iocoder.yudao.module.system.controller.admin.notify.vo.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 我的站内信游标查询 Request VO")
@Data
public class NotifyMessageMyCursorReqVO {

    @Schema(description = "后端返回的下一页游标")
    private String cursor;

    @Schema(description = "每批数量", defaultValue = "20")
    @Min(1)
    @Max(100)
    private Integer limit = 20;

    @Schema(description = "是否已读")
    private Boolean readStatus;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
