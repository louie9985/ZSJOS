package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TeachingServiceLifecycleRespVO {
    private boolean closed;
    private Long previousClassId;
    private LocalDateTime closedAt;
    private List<String> availableActions;
    private List<Event> history;
    @Data public static class Event {
        private String action;
        private String reason;
        private String operatorName;
        private Long fromClassId;
        private Long targetClassId;
        private Long targetOwnerUserId;
        private String targetClassName;
        private String targetOwnerName;
        private LocalDateTime occurredAt;
    }
}
