package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadSubmitterFeedbackRespVO {
    private Long id;
    private String feedback;
    private String salesName;
    private String submitterName;
    private LocalDateTime createTime;
    private List<Attachment> attachments;

    public record Attachment(Long fileId, String originalName, String contentType, Long fileSize, String url) {}
}

