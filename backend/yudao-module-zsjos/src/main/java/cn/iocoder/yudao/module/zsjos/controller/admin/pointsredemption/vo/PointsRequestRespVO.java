package cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data public class PointsRequestRespVO {
    private Long id;
    private String requestNo;
    private String applicantName;
    private String packageName;
    private Integer creditsAmount;
    private Integer validDays;
    private String reason;
    private String approvalStatus;
    private String issueStatus;
    private String processInstanceId;
    private String codeStatus;
    private String lastError;
    private LocalDateTime expiresAt;
    private LocalDateTime redeemedAt;
    private LocalDateTime syncedAt;
    private LocalDateTime submittedAt;
    private Boolean canReveal;
    private List<Attachment> attachments;
    @Data public static class Attachment {
        private Long id;
        private String name;
        private String type;
        private Long size;
        private String url;
    }
}
