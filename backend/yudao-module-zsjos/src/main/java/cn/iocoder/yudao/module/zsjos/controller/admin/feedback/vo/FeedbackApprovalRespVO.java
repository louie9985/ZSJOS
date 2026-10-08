package cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FeedbackApprovalRespVO {
    private Integer roundNo;
    private Integer latestRoundNo;
    private List<Round> rounds;
    private String availability;
    private String unavailableReason;
    private BpmProcessProgressDTO progress;
    private List<FeedbackFormRespVO.Field> fields;
    private Map<String, Object> values;
    private LocalDateTime lastUrgedAt;
    private LocalDateTime nextUrgeAt;
    private boolean canUrge;
    private Integer version;
    public record Round(Integer roundNo, String status, LocalDateTime submittedAt) {}
    public record Summary(String availability, List<BpmProcessProgressDTO.PendingTask> currentTasks) {}
}
