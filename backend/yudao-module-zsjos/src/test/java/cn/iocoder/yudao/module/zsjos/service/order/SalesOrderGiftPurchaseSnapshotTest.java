package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.giftpurchase.GiftPurchaseDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.giftpurchase.GiftPurchaseMapper;
import cn.iocoder.yudao.module.zsjos.service.giftpurchase.GiftPurchaseService;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentGiftAddressService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SalesOrderGiftPurchaseSnapshotTest {
    SalesOrderServiceImpl service;
    GiftPurchaseMapper mapper;
    StudentGiftAddressService addresses;
    GiftPurchaseService purchases;
    SalesOrderDO order;
    @BeforeEach void setup() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "fixture"), GiftPurchaseDO.class);
        TenantContextHolder.setTenantId(1L);
        service = new SalesOrderServiceImpl(); mapper = mock(GiftPurchaseMapper.class);
        addresses = mock(StudentGiftAddressService.class); purchases = mock(GiftPurchaseService.class);
        ReflectionTestUtils.setField(service, "giftPurchaseMapper", mapper);
        ReflectionTestUtils.setField(service, "giftAddressService", addresses);
        ReflectionTestUtils.setField(service, "giftPurchaseService", purchases);
        order = new SalesOrderDO(); order.setId(100L); order.setPersonId(20L);
        order.setGiftItems("[{\"code\":\"gift\"}]"); order.setGiftShippingAddress("old-order-address");
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    void generate() { ReflectionTestUtils.invokeMethod(service, "createGiftPurchaseIfNeeded", order, LocalDateTime.now()); }
    @Test void freezesArchiveAddressAndDoesNotOverwriteExistingPurchase() {
        when(addresses.latestAddress(20L)).thenReturn("collected-address");
        generate();
        var capture = ArgumentCaptor.forClass(GiftPurchaseDO.class);
        verify(mapper).insert(capture.capture());
        assertEquals("collected-address", capture.getValue().getShippingAddress());
        when(mapper.selectCount(any())).thenReturn(1L);
        generate();
        verify(addresses, times(1)).latestAddress(20L);
        verify(mapper, times(1)).insert(any(GiftPurchaseDO.class));
    }
    @Test void missingArchiveAddressDoesNotCopyLegacyOrderAddress() {
        generate();
        var capture = ArgumentCaptor.forClass(GiftPurchaseDO.class);
        verify(mapper).insert(capture.capture());
        assertNull(capture.getValue().getShippingAddress());
    }
    @Test void noGiftDoesNotGeneratePurchase() {
        order.setGiftItems("[]"); generate();
        verifyNoInteractions(mapper, addresses, purchases);
    }
}
