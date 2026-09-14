package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import java.time.LocalDateTime;
import java.util.List;

public record LeadRemarkRespVO(String id, String kind, String content,
                               LocalDateTime occurredAt, String operatorName,
                               List<LeadRemarkAttachmentRespVO> attachments) {
    public LeadRemarkRespVO(String id, String kind, String content, LocalDateTime occurredAt, String operatorName) {
        this(id, kind, content, occurredAt, operatorName, List.of());
    }
}
