package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartnerNotifyChannelReqVO {
    @NotNull
    private Boolean wecomEnabled;
}
