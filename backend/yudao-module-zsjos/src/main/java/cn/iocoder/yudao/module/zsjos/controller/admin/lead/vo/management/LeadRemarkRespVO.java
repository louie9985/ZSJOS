package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management;

import java.time.LocalDateTime;

public record LeadRemarkRespVO(String id, String kind, String content,
                               LocalDateTime occurredAt, String operatorName) {}
