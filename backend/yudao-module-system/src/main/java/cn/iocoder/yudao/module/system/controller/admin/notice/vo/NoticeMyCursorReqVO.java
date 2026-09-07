package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 我的通知公告游标查询 Request VO")
@Data
public class NoticeMyCursorReqVO {
    @Schema(description = "后端返回的下一页游标")
    private String cursor;
    @Schema(description = "每批数量", defaultValue = "20")
    @Min(1) @Max(100)
    private Integer limit = 20;
    @Schema(description = "标题关键词")
    private String keyword;
    @Schema(description = "公告类型")
    private Integer type;
    @Schema(description = "是否已读")
    private Boolean readStatus;
    @Schema(description = "是否高亮")
    private Boolean highlighted;
    @Schema(description = "发布时间范围")
    private LocalDateTime[] publishTime;
}
