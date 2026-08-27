package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "员工工作台 - 公告")
@Data
public class NoticeMyRespVO {
    private Long id;
    private String title;
    private Integer type;
    private String content;
    private LocalDateTime publishTime;
    private Boolean read;
    private LocalDateTime readTime;
    private List<NoticeAttachmentVO> attachments;
}
