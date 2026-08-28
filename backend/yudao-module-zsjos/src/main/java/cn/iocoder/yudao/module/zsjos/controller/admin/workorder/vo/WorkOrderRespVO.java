package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderFieldDefinition;
@Data public class WorkOrderRespVO { private Long id; private String orderNo; private String businessType; private Long businessId; private String sceneCode; private Long sceneVersionId; private String sceneName; private String assignmentMode; private Long sourceUserId; private Long targetUserId; private Long targetDeptId; private String sourceName; private String targetName; private String status; private String processorType; private Integer currentRound; private String remark; private String completionRemark; private List<WorkOrderFieldDefinition> fields; private Map<String, Object> values; private List<Long> attachmentIds; private List<WorkOrderFileRespVO> requestAttachments; private List<Long> completionAttachmentIds; private String returnReason; private List<String> availableActions; private List<WorkOrderTimelineRespVO> timeline; private LocalDateTime createTime; private LocalDateTime claimedAt; private LocalDateTime completedAt; private LocalDateTime acceptedAt; private Integer version; }
