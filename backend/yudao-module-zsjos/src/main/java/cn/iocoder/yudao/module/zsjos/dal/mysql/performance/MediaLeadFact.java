package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MediaLeadFact {
    private Long id;
    private String leadNo;
    private Long userId;
    private String userName;
    private Long deptId;
    private LocalDateTime submittedAt;
    private String status;
    private String channelLabel;
    private String categoryLabel;
}
