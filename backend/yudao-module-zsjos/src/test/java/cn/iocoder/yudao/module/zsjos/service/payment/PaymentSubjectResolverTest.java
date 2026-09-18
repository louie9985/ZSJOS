package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.payment.PaymentSubjectTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSubjectResolverTest {
    private final PaymentSubjectResolver resolver = new PaymentSubjectResolver();
    private final PaymentSubjectService subjects = mock(PaymentSubjectService.class);
    private final ProductPaymentSubjectService products = mock(ProductPaymentSubjectService.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "paymentSubjectService", subjects);
        ReflectionTestUtils.setField(resolver, "productPaymentSubjectService", products);
        ReflectionTestUtils.setField(resolver, "gatewayFactory", factory(new AllinpayProperties()));
    }

    @Test
    void singleProductAndRepeatedSkusUseConfiguredSubjectWithoutDefault() {
        when(products.getPaymentSubjectIdsByProductIds(List.of(101L))).thenReturn(Map.of(101L, 10L));
        var configured = subject(10);
        when(subjects.getPaymentSubject(10L)).thenReturn(configured);
        assertSame(configured, resolver.resolve(List.of(101L, 101L)));
        verify(subjects, never()).getDefaultPaymentSubject();
        verify(subjects, never()).getPaymentSubjectByCode(any());
    }

    @Test
    void differentProductsWithSameSubjectDoNotRequireDefault() {
        when(products.getPaymentSubjectIdsByProductIds(List.of(101L, 202L)))
                .thenReturn(Map.of(101L, 10L, 202L, 10L));
        when(subjects.getPaymentSubject(10L)).thenReturn(subject(10));
        assertEquals(10L, resolver.resolve(List.of(101L, 202L)).getId());
        verify(subjects, times(1)).getPaymentSubject(10L);
        verify(subjects, never()).getDefaultPaymentSubject();
    }

    @Test
    void mixedSubjectsUseConfiguredDefaultRegardlessOfItsCode() {
        when(products.getPaymentSubjectIdsByProductIds(List.of(101L, 202L)))
                .thenReturn(Map.of(101L, 10L, 202L, 20L));
        when(subjects.getPaymentSubject(10L)).thenReturn(subject(10));
        when(subjects.getPaymentSubject(20L)).thenReturn(subject(20));
        when(subjects.getDefaultPaymentSubject()).thenReturn(subject(30));
        assertEquals(30L, resolver.resolve(List.of(101L, 202L)).getId());
        verify(subjects, never()).getPaymentSubjectByCode(any());
    }

    @Test
    void unconfiguredProductUsesDefaultAndParticipatesInMixedResolution() {
        when(products.getPaymentSubjectIdsByProductIds(List.of(101L, 202L))).thenReturn(Map.of(101L, 10L));
        when(subjects.getPaymentSubject(10L)).thenReturn(subject(10));
        when(subjects.getDefaultPaymentSubject()).thenReturn(subject(30));
        assertEquals(30L, resolver.resolve(List.of(101L, 202L)).getId());
        verify(subjects, times(1)).getDefaultPaymentSubject();
    }

    @Test
    void unconfiguredProductWithoutDefaultFails() {
        assertServiceException(() -> resolver.resolve(List.of(101L)), PAYMENT_DEFAULT_SUBJECT_MISSING);
    }

    @Test
    void mixedSubjectsWithoutDefaultFail() {
        when(products.getPaymentSubjectIdsByProductIds(List.of(101L, 202L)))
                .thenReturn(Map.of(101L, 10L, 202L, 20L));
        when(subjects.getPaymentSubject(10L)).thenReturn(subject(10));
        when(subjects.getPaymentSubject(20L)).thenReturn(subject(20));
        assertServiceException(() -> resolver.resolve(List.of(101L, 202L)), PAYMENT_DEFAULT_SUBJECT_MISSING);
    }

    @Test
    void brokenAssociationDoesNotBecomeUnconfigured() {
        when(products.getPaymentSubjectIdsByProductIds(List.of(101L))).thenReturn(Map.of(101L, 10L));
        assertServiceException(() -> resolver.resolve(List.of(101L)), PAYMENT_SUBJECT_NOT_EXISTS);
        verify(subjects, never()).getDefaultPaymentSubject();
    }

    @Test
    void disabledOrIncompleteSubjectCannotHideBehindMixedDefault() {
        when(products.getPaymentSubjectIdsByProductIds(List.of(101L, 202L)))
                .thenReturn(Map.of(101L, 10L, 202L, 20L));
        var invalid = subject(20); invalid.setStatus(1);
        when(subjects.getPaymentSubject(10L)).thenReturn(subject(10));
        when(subjects.getPaymentSubject(20L)).thenReturn(invalid);
        assertServiceException(() -> resolver.resolve(List.of(101L, 202L)), PAYMENT_SUBJECT_DISABLED);
        invalid.setStatus(0); invalid.setAppid(" ");
        assertServiceException(() -> resolver.resolve(List.of(101L, 202L)), PAYMENT_SUBJECT_CONFIG_INVALID);
        verify(subjects, never()).getDefaultPaymentSubject();
    }

    @Test
    void defaultMustAlsoBeEnabledAndComplete() {
        var invalid = subject(30); invalid.setStatus(1);
        when(subjects.getDefaultPaymentSubject()).thenReturn(invalid);
        assertServiceException(() -> resolver.resolve(List.of(101L)), PAYMENT_SUBJECT_DISABLED);
        invalid.setStatus(0); invalid.setMerchantPrivateKey("invalid");
        assertServiceException(() -> resolver.resolve(List.of(101L)), PAYMENT_SUBJECT_CONFIG_INVALID);
    }

    @Test
    void emptyProductSelectionCannotSelectDefault() {
        assertServiceException(() -> resolver.resolve(List.of()), PRODUCT_SKU_INVALID);
        verifyNoInteractions(subjects, products);
    }
}
