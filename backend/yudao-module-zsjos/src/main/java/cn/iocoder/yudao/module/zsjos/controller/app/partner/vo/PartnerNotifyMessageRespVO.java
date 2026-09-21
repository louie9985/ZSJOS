package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageRespVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerNotifyMessageRespVO extends NotifyMessageRespVO {
    private String businessTarget;
    private String targetUnavailableReason;
}
