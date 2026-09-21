package cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NotifyDeliveryRespVO {
    private Long id;
    private Long ruleId;
    private String sceneCode;
    private String channelCode;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime createTime;
    private LocalDateTime succeededAt;
    private String errorCode;
    private List<Recipient> recipients;

    public record Recipient(Integer userType, Long userId, String status, int attempts, String errorCode) {}
}
