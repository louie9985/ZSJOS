package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LeadComplaintRespVO {
    private Long id;
    private Long leadId;
    private Long complainantUserId;
    private Long salesUserId;
    private String reason;
    private String evidenceRefs;
    private String status;
    private String result;
    private Long handlerUserId;
    private String handlerOpinion;
    private String handlerEvidenceRefs;
    private LocalDateTime handledAt;
    private LocalDateTime createTime;
}
