package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 我的通知公告分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeMyPageReqVO extends PageParam {
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
