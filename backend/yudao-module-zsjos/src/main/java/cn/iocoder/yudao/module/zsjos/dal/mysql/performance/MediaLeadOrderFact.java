package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MediaLeadOrderFact {
    private Long leadId;
    private LocalDateTime effectiveAt;
}
