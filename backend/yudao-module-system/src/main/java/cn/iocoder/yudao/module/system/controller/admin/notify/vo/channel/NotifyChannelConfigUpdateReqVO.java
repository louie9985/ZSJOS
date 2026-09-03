package cn.iocoder.yudao.module.system.controller.admin.notify.vo.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotifyChannelConfigUpdateReqVO {
    @NotBlank
    private String channelCode;
    @NotNull
    private Boolean enabled;
}
