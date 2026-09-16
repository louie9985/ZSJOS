package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PurchaseIntentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PurchaseIntentSaveDraftReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PaymentSubjectDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.PurchaseIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PaymentIntentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.PurchaseIntentMapper;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PAYMENT_LINK_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicPaymentDetailTest {
    private final PurchaseIntentService service = spy(new PurchaseIntentService());
    private final PaymentIntentMapper payments = mock(PaymentIntentMapper.class);
    private final PurchaseIntentMapper intents = mock(PurchaseIntentMapper.class);
    private final ZsjosProductSkuService products = mock(ZsjosProductSkuService.class);
    private final PaymentSubjectService subjects = mock(PaymentSubjectService.class);
    private final AllinpayProperties properties = new AllinpayProperties();

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setPublicBaseUrl("https://payment.example.invalid");
        properties.setLinkHmacSecret("test-only-not-a-real-secret");
        ReflectionTestUtils.setField(service, "paymentIntentMapper", payments);
        ReflectionTestUtils.setField(service, "purchaseIntentMapper", intents);
        ReflectionTestUtils.setField(service, "productSkuService", products);
        ReflectionTestUtils.setField(service, "paymentSubjectService", subjects);
        ReflectionTestUtils.setField(service, "allinpayProperties", properties);
    }

    @Test
    void newPaymentFreezesAuthoritativeNamesAndSpecsButKeepsNegotiatedPrices() {
        var request = prepareDraft();
        when(subjects.getPaymentSubjectByCode("school")).thenReturn(new PaymentSubjectDO());
        when(products.validateLeadProduct("COURSE-1", false, "SKU-1", false)).thenReturn(product("课程一", "高级班"));
        when(products.validateLeadProduct("COURSE-2", false, "SKU-2", false)).thenReturn(product("课程二", "基础班"));

        service.createPaymentLink(request, 7L);

        var capture = ArgumentCaptor.forClass(PaymentIntentDO.class);
        verify(payments).insert(capture.capture());
        var payment = capture.getValue();
        var snapshots = JsonUtils.parseArray(payment.getProductItemsSnapshot(), PaymentProductSnapshot.class);
        assertEquals(2, snapshots.size());
        assertEquals("课程一", snapshots.getFirst().productName());
        assertEquals("高级班", snapshots.getFirst().skuName());
        assertEquals("线上授课", snapshots.getFirst().specs().getFirst().label());
        assertEquals(new BigDecimal("3980.00"), snapshots.getFirst().actualAmount());
        assertEquals(new BigDecimal("680.00"), snapshots.getLast().actualAmount());
        assertEquals(new BigDecimal("4660.00"), payment.getExpectedAmount());
        // Existing command readers must still deserialize the original reference/amount fields.
        var compatible = JsonUtils.parseArray(payment.getProductItemsSnapshot(), PurchaseIntentSaveDraftReqVO.Item.class);
        assertEquals("COURSE-1", compatible.getFirst().getSpuRef());
        assertEquals("SKU-1", compatible.getFirst().getSkuRef());

        clearInvocations(products);
        expose(payment);
        var detail = service.publicDetail("PAY-TEST", "test-token");
        assertEquals("课程一", detail.getItems().getFirst().productName());
        assertEquals("高级班", detail.getDescription());
        verifyNoInteractions(products);
        String publicJson = JsonUtils.toJsonString(detail);
        assertFalse(publicJson.contains("spuRef"));
        assertFalse(publicJson.contains("skuRef"));
        assertFalse(publicJson.contains("subjectSnapshot"));
    }

    @Test
    void historicalSnapshotDoesNotInventProductNameOrResolveCurrentCatalog() {
        expose(payment("[{\"spuRef\":\"OLD\",\"skuRef\":\"OLD-SKU\",\"skuName\":\"旧版班型\",\"actualAmount\":680.00}]"));
        var detail = service.publicDetail("PAY-TEST", "test-token");
        var item = detail.getItems().getFirst();
        assertNull(item.productName());
        assertNull(item.specs());
        assertEquals("旧版班型", item.skuName());
        assertEquals(new BigDecimal("680.00"), item.actualAmount());
        verifyNoInteractions(products);
        verify(payments, never()).updateById(any(PaymentIntentDO.class));
    }

    @Test
    void emptyHistoricalSnapshotReturnsEmptyItems() {
        expose(payment(null));
        assertTrue(service.publicDetail("PAY-TEST", "test-token").getItems().isEmpty());
        verifyNoInteractions(products);
    }

    @Test
    void unknownTokenCannotReadProductDetails() {
        var error = assertThrows(ServiceException.class, () -> service.publicDetail("PAY-TEST", "wrong-token"));
        assertEquals(PAYMENT_LINK_INVALID.getCode(), error.getCode());
        verify(payments).selectByNoAndTokenHash("PAY-TEST", SecureUtil.sha256("wrong-token"));
        verifyNoInteractions(products);
    }

    @Test
    void blankTokenDoesNotQueryThePayment() {
        assertThrows(ServiceException.class, () -> service.publicDetail("PAY-TEST", ""));
        verifyNoInteractions(payments, products);
    }

    @Test
    void reusingAnExistingLinkDoesNotReplaceItsSnapshot() {
        var request = prepareDraft();
        when(payments.selectLatestByPurchaseIntent(1L)).thenReturn(payment("[]"));
        service.createPaymentLink(request, 7L);
        verifyNoInteractions(products, subjects);
        verify(payments, never()).insert(any(PaymentIntentDO.class));
    }

    @Test
    void disabledOrInvalidProductCannotCreateANewPayment() {
        var request = prepareDraft();
        when(subjects.getPaymentSubjectByCode("school")).thenReturn(new PaymentSubjectDO());
        when(products.validateLeadProduct("COURSE-1", false, "SKU-1", false))
                .thenThrow(new ServiceException(1, "商品不可用"));
        assertThrows(ServiceException.class, () -> service.createPaymentLink(request, 7L));
        verify(payments, never()).insert(any(PaymentIntentDO.class));
    }

    private PurchaseIntentSaveDraftReqVO prepareDraft() {
        var request = new PurchaseIntentSaveDraftReqVO();
        request.setCollectionMode("online_link");
        request.setItems(List.of(item("COURSE-1", "SKU-1", "3980.00"), item("COURSE-2", "SKU-2", "680.00")));
        var saved = new PurchaseIntentRespVO();
        saved.setId(1L);
        doReturn(saved).when(service).saveDraft(request, 7L);
        var intent = new PurchaseIntentDO().setId(1L).setTotalAmount(new BigDecimal("4660.00"))
                .setItemSnapshotJson(JsonUtils.toJsonString(request.getItems()));
        when(intents.selectByIdForUpdate(1L)).thenReturn(intent);
        return request;
    }

    private PurchaseIntentSaveDraftReqVO.Item item(String product, String sku, String amount) {
        var item = new PurchaseIntentSaveDraftReqVO.Item();
        item.setSpuRef(product); item.setSkuRef(sku); item.setActualAmount(new BigDecimal(amount));
        item.setSkuName("Untrusted client label");
        return item;
    }

    private LeadProductSnapshot product(String name, String skuName) {
        return new LeadProductSnapshot("COURSE", name, 1L, "培训", 2L, "课程")
                .withSku("SKU", skuName, "{}", new BigDecimal("9999.00"))
                .withSpecs(List.of(new ProductSpecVO("mode", "授课方式", "online", "线上授课", false)));
    }

    private PaymentIntentDO payment(String snapshot) {
        return new PaymentIntentDO().setStatus("created").setCurrency("CNY").setExpectedAmount(new BigDecimal("4660.00"))
                .setExpiresAt(LocalDateTime.now().plusHours(1)).setProductItemsSnapshot(snapshot);
    }

    private void expose(PaymentIntentDO payment) {
        when(payments.selectByNoAndTokenHash("PAY-TEST", SecureUtil.sha256("test-token"))).thenReturn(payment);
    }
}
