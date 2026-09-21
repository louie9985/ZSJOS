package cn.iocoder.yudao.module.zsjos.service.wecom;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WecomClickTicketServiceTest {
    @InjectMocks private WecomClickTicketService service;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> values;
    @Mock private LeadMapper leadMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private PartnerAccountMapper partnerAccountMapper;
    private final Map<String, String> tickets = new HashMap<>();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "partnerH5BaseUrl", "https://partner.example.test");
        ReflectionTestUtils.setField(service, "workbenchBaseUrl", "https://staff.example.test");
        ReflectionTestUtils.setField(service, "ticketTtlMinutes", 30L);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(values);
        lenient().doAnswer(call -> { tickets.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        lenient().when(values.getAndDelete(anyString())).thenAnswer(call -> tickets.remove(call.getArgument(0)));
    }

    @Test void partnerOrderTargetsOwnedLeadWithoutQueryingBusinessDuringPublicResolve() {
        when(orderMapper.selectById(42L)).thenReturn(new SalesOrderDO().setId(42L).setLeadId(73L));
        when(leadMapper.selectById(73L)).thenReturn(new LeadDO().setId(73L).setPartnerId(8L));
        when(partnerAccountMapper.selectById(15L)).thenReturn(new PartnerAccountDO().setId(15L).setPartnerId(8L));
        String url = service.createClickUrl(context(3, "sales_order"));
        assertTrue(url.startsWith("https://partner.example.test/wecom/click?ticket="));
        clearInvocations(orderMapper, leadMapper, partnerAccountMapper);
        var result = service.resolve(ticket(url));
        assertEquals("/lead/73", result.getTargetPath());
        assertEquals("PARTNER", result.getAudience());
        assertEquals("/messages", result.getFallbackPath());
        verifyNoInteractions(orderMapper, leadMapper, partnerAccountMapper);
    }

    @Test void partnerOrderCannotTargetAnotherPartnersLead() {
        when(orderMapper.selectById(42L)).thenReturn(new SalesOrderDO().setLeadId(73L));
        when(leadMapper.selectById(73L)).thenReturn(new LeadDO().setId(73L).setPartnerId(9L));
        when(partnerAccountMapper.selectById(15L)).thenReturn(new PartnerAccountDO().setPartnerId(8L));
        var result = service.resolve(ticket(service.createClickUrl(context(3, "sales_order"))));
        assertNull(result.getTargetPath());
        assertEquals("/messages", result.getFallbackPath());
    }

    @Test void missingOrderOrLeadFallsBackWithoutInventingRelationship() {
        var result = service.resolve(ticket(service.createClickUrl(context(3, "sales_order"))));
        assertNull(result.getTargetPath());
        verifyNoInteractions(leadMapper);
    }

    @Test void adminOrderDestinationDoesNotChange() {
        var result = service.resolve(ticket(service.createClickUrl(context(2, "sales_order"))));
        assertEquals("/zsjos/sales-order-approvals?workType=approval&orderId=42", result.getTargetPath());
        assertEquals("ADMIN", result.getAudience());
        verifyNoInteractions(orderMapper, partnerAccountMapper);
    }

    @Test void withdrawalAndFeedbackKeepPartnerDetailDestinations() {
        for (String type : new String[]{"withdrawal", "feedback"}) {
            var result = service.resolve(ticket(service.createClickUrl(context(3, type))));
            assertEquals("/" + type + "/42", result.getTargetPath());
        }
    }

    @Test void messageDetailActionFallsBackInsteadOfOpeningBusiness() {
        var context = NotifyDeliveryContext.builder().tenantId(1L).userType(3).userId(15L)
                .bizType("withdrawal").bizId(42L).actionType("message_detail").build();
        assertNull(service.resolve(ticket(service.createClickUrl(context))).getTargetPath());
    }

    @Test void oneTimeAndShortLivedContractIsPreserved() {
        String ticket = ticket(service.createClickUrl(context(3, "withdrawal")));
        verify(values).set(anyString(), anyString(), eq(Duration.ofMinutes(30)));
        assertNotNull(service.resolve(ticket));
        assertThrows(ServiceException.class, () -> service.resolve(ticket));
        assertThrows(ServiceException.class, () -> service.resolve("expired-or-unknown"));
    }

    private NotifyDeliveryContext context(int type, String bizType) {
        return NotifyDeliveryContext.builder().tenantId(1L).userType(type).userId(15L)
                .bizType(bizType).bizId(42L).actionType("business_detail").build();
    }
    private String ticket(String url) { return URI.create(url).getQuery().substring("ticket=".length()); }
}
