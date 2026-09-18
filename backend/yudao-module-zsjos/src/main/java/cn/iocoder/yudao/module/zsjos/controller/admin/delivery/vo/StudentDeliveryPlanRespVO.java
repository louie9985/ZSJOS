package cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.*;
@Data public class StudentDeliveryPlanRespVO {
 private Long id, accountId; private String status, notificationRecipient; private LocalDateTime accountOpenedAt;
 private Long weeklyLeads;
 private Integer roundNo; private boolean sourceAvailable; private List<Stage> stages; private List<Round> rounds;
 public record Round(Long id, Integer roundNo, String status) {}
 public record Defer(Long id, String reason, LocalDateTime originalDueAt, LocalDateTime newDueAt, String status, LocalDateTime createdAt) {}
 @Data public static class Stage {
  private Long id; private String stageCode, status, completedByName; private Integer version;
  private LocalDateTime triggerAt,dueAt,completedAt; private Map<String,Object> agreement,confirmation;
  private List<Defer> defers; private boolean canSubmit,canDefer,canReposition;
 }
}
