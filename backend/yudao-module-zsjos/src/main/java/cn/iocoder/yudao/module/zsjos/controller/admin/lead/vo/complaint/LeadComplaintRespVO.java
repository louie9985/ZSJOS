package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadComplaintRespVO {
    private Long id;
    private Long leadId;
    private String leadNo;
    private Long complainantUserId;
    private String complainantUserName;
    private Long salesUserId;
    private String salesUserName;
    private String reason;
    private String evidenceRefs;
    private List<EvidenceVO> evidence;
    private String status;
    private String result;
    private Long handlerUserId;
    private String handlerUserName;
    private String handlerOpinion;
    private String handlerEvidenceRefs;
    private List<EvidenceVO> handlerEvidence;
    private LocalDateTime handledAt;
    private LocalDateTime createTime;

    @Data
    public static class EvidenceVO {
        private Long infraFileId;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
    }
}
