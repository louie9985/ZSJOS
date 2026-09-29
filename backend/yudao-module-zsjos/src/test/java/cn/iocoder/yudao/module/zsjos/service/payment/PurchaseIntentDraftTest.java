package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PurchaseIntentSaveDraftReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.*;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseIntentDraftTest {
    private PurchaseIntentService service;
    private PurchaseIntentMapper drafts;
    private PaymentIntentMapper payments;
    private AllinpayProperties properties;

    @BeforeEach void setup() {
        service = new PurchaseIntentService();
        drafts = mock(PurchaseIntentMapper.class);
        payments = mock(PaymentIntentMapper.class);
        properties = new AllinpayProperties();
        ReflectionTestUtils.setField(service, "purchaseIntentMapper", drafts);
        ReflectionTestUtils.setField(service, "paymentIntentMapper", payments);
        ReflectionTestUtils.setField(service, "allinpayProperties", properties);
        doAnswer(call -> { ((PurchaseIntentDO) call.getArgument(0)).setId(9L); return 1; })
                .when(drafts).insert(any(PurchaseIntentDO.class));
    }

    private PurchaseIntentSaveDraftReqVO request(String mode, String... amounts) {
        var req = new PurchaseIntentSaveDraftReqVO();
        req.setCollectionMode(mode); req.setPersonId(10L); req.setPurchaseType("first_purchase");
        req.setSourceKey("lead:1"); req.setIdempotencyKey("draft-key");
        var items = new ArrayList<PurchaseIntentSaveDraftReqVO.Item>();
        for (String amount : amounts) {
            var item = new PurchaseIntentSaveDraftReqVO.Item();
            item.setSpuRef("spu"); item.setSkuRef("sku-" + items.size());
            item.setActualAmount(new BigDecimal(amount)); items.add(item);
        }
        req.setItems(items);
        req.setTotalAmount(items.stream().map(PurchaseIntentSaveDraftReqVO.Item::getActualAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        req.setDraft(Map.of("items", items));
        return req;
    }

    @Test void savesRestoresAndRetriesOfflineZeroDraft() {
        var req = request("offline_paid", "0.00", "0.00");
        var saved = service.saveDraft(req, 20L);
        var captor = org.mockito.ArgumentCaptor.forClass(PurchaseIntentDO.class);
        verify(drafts).insert(captor.capture());
        var persisted = captor.getValue();
        when(drafts.selectActive(null, 10L, "first_purchase", "lead:1", 20L)).thenReturn(persisted);
        assertEquals(new BigDecimal("0.00"), saved.getTotalAmount());
        assertEquals(saved.getDraft(), service.current(req, 20L).getDraft());
        assertEquals(saved.getId(), service.saveDraft(req, 20L).getId());
        verify(drafts, times(1)).insert(any(PurchaseIntentDO.class));
        verify(payments, never()).insert(any(PaymentIntentDO.class));
    }

    @Test void acceptsFreeItemsWithPositiveTotalForBothModes() {
        for (String mode : List.of("offline_paid", "online_link")) {
            assertEquals(new BigDecimal("0.01"), service.saveDraft(request(mode, "0", "0.01"), 20L).getTotalAmount());
        }
    }

    @Test void rejectsOnlineZeroBeforeAnyPaymentLookupEvenIfGatewayUnavailable() {
        var req = request("online_link", "0");
        assertServiceException(() -> service.saveDraft(req, 20L), PURCHASE_INTENT_ONLINE_AMOUNT_INVALID);
        assertServiceException(() -> service.createPaymentLink(req, 20L), PURCHASE_INTENT_ONLINE_AMOUNT_INVALID);
        verifyNoInteractions(payments);
        verify(drafts, never()).insert(any(PurchaseIntentDO.class));
    }

    @Test void positiveCentReachesExistingPaymentFlowAndCannotReusePersistedZero() {
        properties.setEnabled(true); properties.setPublicBaseUrl("https://example.test"); properties.setLinkHmacSecret("test-only");
        var req = request("online_link", "0.01");
        var persisted = new PurchaseIntentDO().setId(9L).setPersonId(10L).setPurchaseType("first_purchase")
                .setCollectionMode("online_link").setInitiatorUserId(20L).setTotalAmount(new BigDecimal("0.01"))
                .setLastIdempotencyKey("draft-key");
        when(drafts.selectActive(null, 10L, "first_purchase", "lead:1", 20L)).thenReturn(persisted);
        when(drafts.selectByIdForUpdate(9L)).thenReturn(persisted);
        when(payments.selectLatestByPurchaseIntent(9L)).thenReturn(new PaymentIntentDO().setStatus("created"));
        assertEquals(new BigDecimal("0.01"), service.createPaymentLink(req, 20L).getTotalAmount());
        persisted.setTotalAmount(BigDecimal.ZERO);
        assertServiceException(() -> service.createPaymentLink(req, 20L), PURCHASE_INTENT_ONLINE_AMOUNT_INVALID);
        verify(payments, never()).insert(any(PaymentIntentDO.class));
    }

    @Test void rejectsNegativeMissingPrecisionAndMismatchWithoutRounding() {
        for (var req : List.of(request("offline_paid", "-100", "100"), request("offline_paid", "0.001"),
                request("offline_paid", "1.999"), request("offline_paid", "10000000000000000"))) {
            assertServiceException(() -> service.saveDraft(req, 20L), PURCHASE_INTENT_DRAFT_INVALID);
        }
        var req = request("offline_paid", "0");
        req.setTotalAmount(null);
        assertServiceException(() -> service.saveDraft(req, 20L), PURCHASE_INTENT_DRAFT_INVALID);
        req.setTotalAmount(BigDecimal.ONE);
        assertServiceException(() -> service.saveDraft(req, 20L), PURCHASE_INTENT_DRAFT_INVALID);
        req.getItems().getFirst().setActualAmount(null);
        assertServiceException(() -> service.saveDraft(req, 20L), PURCHASE_INTENT_DRAFT_INVALID);
        req.setItems(Arrays.asList((PurchaseIntentSaveDraftReqVO.Item) null));
        assertServiceException(() -> service.saveDraft(req, 20L), PURCHASE_INTENT_DRAFT_INVALID);
        verify(drafts, never()).insert(any(PurchaseIntentDO.class));
    }

    @Test void zeroModeSwitchCannotBypassWaitingPaidOrPendingCloseLock() {
        var req = request("offline_paid", "0"); req.setId(9L);
        var persisted = new PurchaseIntentDO().setId(9L).setPersonId(10L).setPurchaseType("first_purchase")
                .setInitiatorUserId(20L).setCollectionMode("online_link").setSnapshotLocked(true);
        when(drafts.selectByIdForUpdate(9L)).thenReturn(persisted);
        for (String status : List.of("waiting", "paid", "created")) {
            when(payments.selectLatestByPurchaseIntent(9L)).thenReturn(new PaymentIntentDO().setStatus(status).setReqsn("issued"));
            assertServiceException(() -> service.saveDraft(req, 20L), PURCHASE_INTENT_PAYMENT_CONFLICT);
        }
        verify(payments, never()).updateById(any(PaymentIntentDO.class));
        assertTrue(persisted.getSnapshotLocked());
    }

    @Test void zeroDraftPreservesOwnershipAndVersionChecks() {
        var req = request("offline_paid", "0"); req.setId(9L); req.setVersion(0);
        var persisted = new PurchaseIntentDO().setId(9L).setPersonId(10L).setPurchaseType("first_purchase")
                .setInitiatorUserId(20L).setOwnerUserId(20L).setCollectionMode("offline_paid").setVersion(1);
        when(drafts.selectByIdForUpdate(9L)).thenReturn(persisted);
        assertServiceException(() -> service.saveDraft(req, 21L), PURCHASE_INTENT_PERMISSION_DENIED);
        assertServiceException(() -> service.saveDraft(req, 20L), PURCHASE_INTENT_VERSION_CONFLICT);
    }

    @Test void beanValidationCascadesIntoEveryItemAndAllowsZeroTotal() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var req = request("offline_paid", "0.00");
            assertTrue(validator.validate(req).isEmpty());
            for (String amount : List.of("-1", "0.001", "10000000000000000")) {
                req.getItems().getFirst().setActualAmount(new BigDecimal(amount));
                assertTrue(validator.validate(req).stream().anyMatch(v -> v.getPropertyPath().toString().equals("items[0].actualAmount")));
            }
            req.getItems().getFirst().setActualAmount(null);
            assertFalse(validator.validate(req).isEmpty());
            req.setItems(Arrays.asList((PurchaseIntentSaveDraftReqVO.Item) null));
            assertFalse(validator.validate(req).isEmpty());
            req.setTotalAmount(null);
            assertFalse(validator.validateProperty(req, "totalAmount").isEmpty());
        }
    }
}
