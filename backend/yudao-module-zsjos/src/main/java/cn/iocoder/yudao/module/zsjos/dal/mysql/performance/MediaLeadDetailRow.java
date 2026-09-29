package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MediaLeadDetailRow {
    private String leadNo;
    private LocalDateTime submittedAt;
    private String contributorName;
    private String status;
    private String channelLabel;
    private String categoryLabel;
    private LocalDateTime orderEffectiveAt;
}
