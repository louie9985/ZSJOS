package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartnerMeRespVO {
    private Long id;
    private String partnerNo;
    private String name;
    private String mobile;
    private String status;
    private String channelId;
    private LocalDateTime enabledAt;
    private LocalDateTime disabledAt;
}
