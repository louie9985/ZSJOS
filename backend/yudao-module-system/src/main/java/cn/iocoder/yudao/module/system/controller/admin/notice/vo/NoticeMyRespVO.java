package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "员工工作台 - 公告")
@Data
public class NoticeMyRespVO {
    private Long id;

    /** 来源部门与发布人展示使用公告保存的快照，不回查当前人员归属。 */
    private Long sourceDeptId;
    private String sourceDeptName;
    private Long publisherId;
    private String publisherName;
    private String audienceSummary;
    private String audienceType;

    private String title;
    private Integer type;
    private String content;
    private LocalDateTime publishTime;
    private LocalDateTime highlightUntil;
    private Boolean highlighted;
    private Boolean read;
    private LocalDateTime readTime;
    private List<NoticeAttachmentVO> attachments;
}
