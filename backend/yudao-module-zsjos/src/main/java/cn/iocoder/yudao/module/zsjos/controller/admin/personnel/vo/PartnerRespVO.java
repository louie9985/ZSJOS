package cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PartnerRespVO {
    private Long id;
    private String partnerNo;
    private String name;
    private String mobile;
    private String status;
    private Long boundSystemUserId;
    private String channelId;
    private LocalDateTime enabledAt;
    private LocalDateTime disabledAt;
}
