package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;
import jakarta.validation.constraints.NotNull; import lombok.Data; import java.time.LocalDateTime;
@Data public class ProductionTicketSaveReqVO {
    @NotNull private Long accountId; @NotNull private Long reviewerUserId; private Long assigneeFilmingEditorUserId;
    private String scriptText; private LocalDateTime expectedDeliveredAt; private LocalDateTime deadlineAt;
    private Integer maxRevisionCount;
}
