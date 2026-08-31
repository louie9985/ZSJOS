package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderNotifySceneProviderTest {

    @InjectMocks private SalesOrderNotifySceneProvider provider;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private CashbackService cashbackService;
    @Mock private PartnerAccountMapper partnerAccountMapper;

    @Test
    void orderScenesUseOrderNumberInsteadOfStudentName() {
        provider.getScenes().forEach(scene -> {
            var keys = scene.getVariables().stream().map(variable -> variable.getKey()).toList();
            assertTrue(keys.contains("order.no"));
            assertFalse(keys.contains("order.studentName"));
        });
    }

    @Test
    void resolvesOrderNumberWithoutCustomerName() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(7L); order.setOrderNo("DD202608180001"); order.setStudentName("不应进入通知");
        when(orderMapper.selectById(7L)).thenReturn(order);
        Map<String, Object> values = provider.resolveVariables(
                NotifyBusinessEvent.builder().bizId(7L).payload(new HashMap<>()).build(), NotifyRecipientDTO.admin(3L));
        assertTrue(values.containsKey("order.no"));
        assertFalse(values.containsKey("order.studentName"));
    }

    @Test
    void toleratesMissingPayload() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(8L); order.setOrderNo("DD202608180002");
        when(orderMapper.selectById(8L)).thenReturn(order);
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().bizId(8L).build();

        assertEquals(Set.of(), provider.resolveRecipients(event, Set.of()));
        assertEquals("DD202608180002",
                provider.resolveVariables(event, NotifyRecipientDTO.admin(3L)).get("order.no"));
    }
}
