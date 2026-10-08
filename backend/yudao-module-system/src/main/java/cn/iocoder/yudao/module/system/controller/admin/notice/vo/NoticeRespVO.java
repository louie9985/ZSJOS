package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 通知公告信息 Response VO")
@Data
public class NoticeRespVO {

    @Schema(description = "通知公告序号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    /** 来源部门与发布人展示使用公告保存的快照，不回查当前人员归属。 */
    private Long sourceDeptId;
    private String sourceDeptName;
    private Long publisherId;
    private String publisherName;
    private String audienceSummary;


    @Schema(description = "公告标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "小博主")
    private String title;

    @Schema(description = "公告类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "小博主")
    private Integer type;

    @Schema(description = "公告内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "半生编码")
    private String content;

    @Schema(description = "状态，参见 CommonStatusEnum 枚举类", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;
    private String audienceType;
    private List<Long> targetDeptIds;
    private List<Long> targetUserIds;
    private Integer recipientCount;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "时间戳格式")
    private LocalDateTime createTime;

    private String publishStatus;
    private LocalDateTime publishTime;
    private LocalDateTime offlineTime;
    private LocalDateTime highlightUntil;
    private Boolean highlighted;
    private List<NoticeAttachmentVO> attachments;

}
