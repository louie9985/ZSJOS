package cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo;
import lombok.Data; import java.time.LocalDateTime; import java.util.List;
@Data public class StudentDeliveryPlanRespVO { private Long id; private Long accountId; private String status; private LocalDateTime accountOpenedAt; private List<Stage> stages; @Data public static class Stage { private Long id; private String stageCode; private String status; private LocalDateTime triggerAt; private LocalDateTime dueAt; private LocalDateTime completedAt; } }
