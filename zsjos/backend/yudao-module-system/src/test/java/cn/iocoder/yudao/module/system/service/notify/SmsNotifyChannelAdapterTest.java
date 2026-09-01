package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.api.notify.NotifyRecipientMobileProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import cn.iocoder.yudao.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsNotifyChannelAdapterTest {

    private final SmsNotifyChannelAdapter adapter = new SmsNotifyChannelAdapter();
    @Mock private SmsSendApi smsSendApi;
    @Mock private NotifyRecipientMobileProvider partnerMobileProvider;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "smsSendApi", smsSendApi);
        ReflectionTestUtils.setField(adapter, "mobileProviders", List.of(partnerMobileProvider));
    }

    @Test
    void sendsPartnerSmsWithTypedIdentityAndResolvedMobile() {
        when(partnerMobileProvider.getUserType()).thenReturn(UserTypeEnum.PARTNER.getValue());
        when(partnerMobileProvider.getMobile(20L)).thenReturn("13800138000");
        when(smsSendApi.sendSingleSms(any(), eq(UserTypeEnum.PARTNER.getValue()))).thenReturn(88L);

        NotifySendResult result = adapter.send(context(UserTypeEnum.PARTNER.getValue(), 20L));

        assertTrue(result.isSuccess());
        verify(smsSendApi).sendSingleSms(argThat(request -> request.getUserId().equals(20L)
                && request.getMobile().equals("13800138000")), eq(UserTypeEnum.PARTNER.getValue()));
        verify(smsSendApi, never()).sendSingleSmsToAdmin(any());
    }

    @Test
    void failsClosedForUnsupportedRecipientType() {
        when(partnerMobileProvider.getUserType()).thenReturn(UserTypeEnum.PARTNER.getValue());

        NotifySendResult result = adapter.send(context(99, 20L));

        assertFalse(result.isSuccess());
        assertFalse(result.isRetryable());
        assertEquals("SMS_RECIPIENT_TYPE_UNSUPPORTED", result.getErrorCode());
        verifyNoInteractions(smsSendApi);
    }

    private NotifyDeliveryContext context(Integer userType, Long userId) {
        return NotifyDeliveryContext.builder().userType(userType).userId(userId).smsTemplateId("partner_notice")
                .variables(Map.of("name", "test")).build();
    }
}
