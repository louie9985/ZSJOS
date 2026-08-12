package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.NotifyChannelAdapter;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import cn.iocoder.yudao.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** Bridges message-center deliveries to the existing SMS module. */
@Component
public class SmsNotifyChannelAdapter implements NotifyChannelAdapter {

    @Resource private SmsSendApi smsSendApi;

    @Override
    public String getChannelCode() {
        return NotifyChannelType.SMS;
    }

    @Override
    public NotifySendResult send(NotifyDeliveryContext context) {
        if (context.getSmsTemplateId() == null || context.getSmsTemplateId().isBlank()) {
            return NotifySendResult.failure("SMS_TEMPLATE_MISSING", "短信模板编号未配置", false);
        }
        try {
            SmsSendSingleToUserReqDTO request = new SmsSendSingleToUserReqDTO();
            request.setUserId(context.getUserId());
            request.setTemplateCode(context.getSmsTemplateId());
            request.setTemplateParams(context.getVariables());
            return NotifySendResult.success(String.valueOf(smsSendApi.sendSingleSmsToAdmin(request)));
        } catch (Exception ex) {
            return NotifySendResult.failure("SMS_SEND_FAILED", "短信发送失败", true);
        }
    }
}
