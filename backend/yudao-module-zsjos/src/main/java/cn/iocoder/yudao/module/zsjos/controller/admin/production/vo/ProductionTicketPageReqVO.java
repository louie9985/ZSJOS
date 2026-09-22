package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductionTicketPageReqVO extends PageParam {
    private String status;
    private String keyword;
    private Boolean pendingAssignment;
    @Pattern(regexp = "todo|producing|review|completed|all")
    private String statusGroup;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadlineFrom;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadlineTo;

    public List<String> groupedStatuses() {
        if (statusGroup == null) return List.of();
        return switch (statusGroup) {
            case "todo" -> List.of("pending_accept", "accepted", "rejected");
            case "producing" -> List.of("in_production");
            case "review" -> List.of("submitted", "checking");
            case "completed" -> List.of("completed");
            default -> List.of();
        };
    }
}
