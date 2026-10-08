package cn.iocoder.yudao.module.system.controller.admin.notice.vo;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class NoticeShareRespVO {
    private boolean active;
    private Long version;
    private List<Long> attachmentIds;
    @ToString.Exclude private String url;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
}
