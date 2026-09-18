package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PartnerStudentLinkRespVO {
    private boolean bound;
    private String partnerNo;
    private String partnerName;
    private LocalDateTime startedAt;
}
