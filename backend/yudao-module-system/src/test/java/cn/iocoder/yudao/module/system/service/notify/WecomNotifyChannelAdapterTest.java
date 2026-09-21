package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.*;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.system.dal.dataobject.social.SocialClientDO;
import cn.iocoder.yudao.module.system.dal.mysql.social.SocialClientMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WecomNotifyChannelAdapterTest {
    @Spy @InjectMocks private WecomNotifyChannelAdapter adapter;
    @Mock private NotifyChannelConfigService configService;
    @Mock private SocialClientMapper socialClientMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> values;
    @Mock private NotifyRecipientWecomUserProvider recipient;
    private NotifyDeliveryContext context;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(adapter, "wecomUserProviders", List.of(recipient));
        when(configService.getEnabled(1L, "wecom")).thenReturn(NotifyChannelConfig.builder().enabled(true).build());
        when(recipient.getUserType()).thenReturn(3); when(recipient.getWecomUserId(7L)).thenReturn("synthetic-recipient");
        lenient().when(socialClientMapper.selectBySocialTypeAndUserType(30, 3)).thenReturn(new SocialClientDO().setId(5L)
                .setClientId("synthetic-corp").setClientSecret("synthetic-secret").setAgentId("1000001").setStatus(0));
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(values);
        lenient().when(values.get(anyString())).thenReturn("synthetic-access");
        context = NotifyDeliveryContext.builder().tenantId(1L).userType(3).userId(7L)
                .title("测试通知").content("测试正文").wecomClickPrepared(true).wecomClickUrl("https://example.test/click").build();
    }

    @Test void disabledDedicatedApplicationDoesNotFallBack() {
        when(socialClientMapper.selectBySocialTypeAndUserType(30, 3))
                .thenReturn(new SocialClientDO().setStatus(1));
        assertEquals("WECOM_CREDENTIAL_MISSING", adapter.send(context).getErrorCode());
        verify(socialClientMapper, never()).selectBySocialTypeAndUserType(30, 2);
        verify(adapter, never()).postMessage(anyString(), anyString());
    }
    @Test void recipientLookupFailureIsSafeToRetryBeforePost() {
        when(recipient.getWecomUserId(7L)).thenThrow(new IllegalStateException("synthetic lookup failure"));
        var result = adapter.send(context);
        assertEquals("WECOM_PREPARE_FAILED", result.getErrorCode());
        assertTrue(result.isRetryable());
        verify(adapter, never()).postMessage(anyString(), anyString());
    }
    @Test void acceptedMessageRequiresProviderId() {
        doReturn("{\"errcode\":0,\"msgid\":\"message-1\"}").when(adapter).postMessage(anyString(), anyString());
        assertEquals("message-1", adapter.send(context).getExternalId());
    }
    @Test void invalidUserIsFailureEvenWhenErrcodeIsZero() {
        doReturn("{\"errcode\":0,\"invaliduser\":\"synthetic-recipient\",\"msgid\":\"message-1\"}")
                .when(adapter).postMessage(anyString(), anyString());
        var result = adapter.send(context);
        assertEquals("WECOM_RECIPIENT_INVALID", result.getErrorCode()); assertFalse(result.isRetryable());
    }
    @Test void transientProviderRejectionCanBeRetriedByOutbox() {
        doReturn("{\"errcode\":-1}").when(adapter).postMessage(anyString(), anyString());
        assertTrue(adapter.send(context).isRetryable());
        verify(adapter).postMessage(anyString(), anyString());
    }
    @Test void transportTimeoutIsNotImmediatelyResent() {
        doThrow(new IllegalStateException("synthetic timeout")).when(adapter).postMessage(anyString(), anyString());
        var result = adapter.send(context);
        assertEquals("WECOM_DELIVERY_UNCERTAIN", result.getErrorCode()); assertFalse(result.isRetryable());
        verify(adapter).postMessage(anyString(), anyString());
        verify(stringRedisTemplate, never()).delete(anyString());
    }
    @Test void malformedOrIncompleteResponseDoesNotCountAsSuccess() {
        doReturn("{}").when(adapter).postMessage(anyString(), anyString());
        assertEquals("WECOM_DELIVERY_UNCERTAIN", adapter.send(context).getErrorCode());
    }
    @Test void tokenExpiryRetriesWithIdenticalBodyOnlyAfterExplicitRejection() {
        doReturn("{\"errcode\":42001}", "{\"errcode\":0,\"msgid\":\"message-2\"}")
                .when(adapter).postMessage(anyString(), anyString());
        assertTrue(adapter.send(context).isSuccess());
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(adapter, times(2)).postMessage(anyString(), bodies.capture());
        assertEquals(bodies.getAllValues().get(0), bodies.getAllValues().get(1));
        verify(stringRedisTemplate).delete(anyString());
    }
}
