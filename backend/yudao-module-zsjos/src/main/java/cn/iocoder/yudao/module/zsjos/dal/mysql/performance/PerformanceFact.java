package cn.iocoder.yudao.module.zsjos.dal.mysql.performance;
import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;
@Data public class PerformanceFact {
 private Boolean currentQualification; private Boolean currentAssignment;
 private String channelCode;
 private String outcome; private LocalDateTime endedAt;
 private Long id; private String number; private Long leadId; private Long userId; private String userName;
 private LocalDateTime occurredAt; private LocalDateTime receivedAt; private LocalDateTime dueAt; private LocalDateTime completedAt;
 private BigDecimal amount; private String groupKey; private String label; private String category; private String stage;
 private String status; private String orderType; private Long deptId; private Long centerId; private Long assignmentId;
}
