package cn.iocoder.yudao.module.system.controller.pub.notice.vo;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class NoticeSharePublicRespVO {
    private String title;
    @ToString.Exclude private String content;
    private LocalDateTime publishTime;
    private List<Attachment> attachments;
    public record Attachment(Long id, String fileName, String mimeType, Long fileSize) {}
}
