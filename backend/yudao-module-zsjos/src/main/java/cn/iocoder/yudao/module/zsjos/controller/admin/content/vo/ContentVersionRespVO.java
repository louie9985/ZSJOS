package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ContentVersionRespVO {
    private Long id;
    private Long contentId;
    private Integer versionNo;
    private String stage;
    private String materialRefsJson;
    private String deliverableUrl;
    private String scriptText;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private String reviewDecision;
    private String reviewComment;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
}
