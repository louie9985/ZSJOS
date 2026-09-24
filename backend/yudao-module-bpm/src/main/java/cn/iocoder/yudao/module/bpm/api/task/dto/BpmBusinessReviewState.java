package cn.iocoder.yudao.module.bpm.api.task.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** Contains workflow evidence only, never financial account data. */
@Data
public class BpmBusinessReviewState {
    private boolean actionable;
    private String problem;
    private Integer processStatus;
    private Long reviewerUserId;
    private String reviewerName;
    private String reason;
    private LocalDateTime reviewedAt;
    private LocalDateTime endedAt;
    private List<String> taskIds = List.of();
    private List<Long> assigneeUserIds = List.of();
}
