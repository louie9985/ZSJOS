package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderFieldDefinition;
@Data public class WorkOrderRespVO { private Long id; private String orderNo; private String sceneCode; private String sceneName; private String assignmentMode; private Long sourceUserId; private Long targetUserId; private String sourceName; private String targetName; private String status; private List<WorkOrderFieldDefinition> fields; private Map<String, Object> values; private List<Long> attachmentIds; private String returnReason; private LocalDateTime createTime; private LocalDateTime claimedAt; private LocalDateTime completedAt; private LocalDateTime acceptedAt; private Integer version; }
