package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartnerLoginRespVO {
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresTime;
    private String clientId;
}
