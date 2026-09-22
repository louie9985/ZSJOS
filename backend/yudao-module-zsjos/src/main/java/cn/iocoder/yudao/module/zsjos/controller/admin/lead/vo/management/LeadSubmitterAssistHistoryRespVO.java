package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadSubmitterAssistHistoryRespVO {
    private Long id; private String leadNo; private String status; private Integer version;
    private String problem; private String expectedAssistance; private String remark;
    private String requesterName; private String submitterName; private String assigneeName;
    private LocalDateTime requestedAt; private String responseRemark;
    private String responderName; private LocalDateTime respondedAt;
    private List<Attachment> requestAttachments;
    private List<Attachment> responseAttachments;

    @Data
    public static class Attachment {
        private Long infraFileId;
        private String name;
        private String type;
        private Long size;
        private String url;
    }
}
