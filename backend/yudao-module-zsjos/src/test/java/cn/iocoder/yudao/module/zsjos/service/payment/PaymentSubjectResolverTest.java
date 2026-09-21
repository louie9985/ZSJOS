package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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

    @ParameterizedTest
    @CsvSource({"0,0,10", "10,10,10", "20,20,20", "10,0,10", "20,0,20",
            "10,20,20", "30,30,30", "30,10,20", "30,20,20", "30,0,20", "30,40,20"})
    void routingMatrixIgnoresOrderDuplicatesAndDefaultFlag(long first, long second, long expected) {
        var school = subject(10); school.setSubjectCode("school");
        var company = subject(20); company.setSubjectCode("company");
        var other = subject(30); other.setIsDefault(true);
        when(subjects.getPaymentSubjectByCode("school")).thenReturn(school);
        when(subjects.getPaymentSubjectByCode("company")).thenReturn(company);
        when(subjects.getPaymentSubject(10L)).thenReturn(school);
        when(subjects.getPaymentSubject(20L)).thenReturn(company);
        when(subjects.getPaymentSubject(30L)).thenReturn(other);
        when(subjects.getPaymentSubject(40L)).thenReturn(subject(40));
        Map<Long, Long> configured = new HashMap<>();
        if (first != 0) configured.put(101L, first);
        if (second != 0) configured.put(202L, second);
        when(products.getPaymentSubjectIdsByProductIds(anyList())).thenReturn(configured);
        assertEquals(expected, resolver.resolve(List.of(101L, 202L, 101L)).getId());
        school.setIsDefault(true); other.setIsDefault(false);
        assertEquals(expected, resolver.resolve(List.of(202L, 101L)).getId());
        verify(subjects, never()).getDefaultPaymentSubject();
    }

    @Test
    void sameConfiguredSubjectDoesNotRequireSchoolOrCompany() {
        when(products.getPaymentSubjectIdsByProductIds(anyList())).thenReturn(Map.of(101L, 30L, 202L, 30L));
        when(subjects.getPaymentSubject(30L)).thenReturn(subject(30));
        assertEquals(30L, resolver.resolve(List.of(101L, 202L, 101L)).getId());
        verify(subjects, times(1)).getPaymentSubject(30L);
        verify(subjects, never()).getPaymentSubjectByCode(any());
        verify(subjects, never()).getDefaultPaymentSubject();
    }

    @Test
    void singleUnconfiguredProductUsesSchoolOnly() {
        when(subjects.getPaymentSubjectByCode("school")).thenReturn(subject(10));
        assertEquals(10L, resolver.resolve(List.of(101L, 101L)).getId());
        verify(subjects, never()).getPaymentSubjectByCode("company");
        verify(subjects, never()).getDefaultPaymentSubject();
    }

    @Test
    void missingSchoolAndMissingConflictCompanyHaveDistinctErrors() {
        assertServiceException(() -> resolver.resolve(List.of(101L)), PAYMENT_SCHOOL_SUBJECT_MISSING);
        when(products.getPaymentSubjectIdsByProductIds(anyList())).thenReturn(Map.of(101L, 30L, 202L, 40L));
        when(subjects.getPaymentSubject(30L)).thenReturn(subject(30));
        when(subjects.getPaymentSubject(40L)).thenReturn(subject(40));
        assertServiceException(() -> resolver.resolve(List.of(101L, 202L)), PAYMENT_COMPANY_SUBJECT_MISSING);
    }

    @Test
    void brokenAssociationDoesNotBecomeUnconfigured() {
        when(products.getPaymentSubjectIdsByProductIds(anyList())).thenReturn(Map.of(101L, 10L));
        assertServiceException(() -> resolver.resolve(List.of(101L)), PAYMENT_SUBJECT_NOT_EXISTS);
        verify(subjects, never()).getPaymentSubjectByCode(any());
    }

    @Test
    void invalidParticipatingSubjectCannotHideBehindCompany() {
        when(products.getPaymentSubjectIdsByProductIds(anyList())).thenReturn(Map.of(101L, 10L, 202L, 30L));
        when(subjects.getPaymentSubject(10L)).thenReturn(subject(10));
        var invalid = subject(30); invalid.setStatus(1);
        when(subjects.getPaymentSubject(30L)).thenReturn(invalid);
        assertServiceException(() -> resolver.resolve(List.of(101L, 202L)), PAYMENT_SUBJECT_DISABLED);
        invalid.setStatus(0); invalid.setAppid(" ");
        assertServiceException(() -> resolver.resolve(List.of(101L, 202L)), PAYMENT_SUBJECT_CONFIG_INVALID);
        invalid.setAppid("test"); invalid.setMerchantPrivateKey("invalid");
        assertServiceException(() -> resolver.resolve(List.of(101L, 202L)), PAYMENT_SUBJECT_CONFIG_INVALID);
        verify(subjects, never()).getPaymentSubjectByCode(any());
    }

    @ParameterizedTest
    @CsvSource({"school", "company"})
    void fallbackSubjectsMustBeEnabledAndComplete(String code) {
        List<Long> ids = List.of(101L);
        if (code.equals("company")) {
            ids = List.of(101L, 202L);
            when(products.getPaymentSubjectIdsByProductIds(anyList())).thenReturn(Map.of(101L, 30L, 202L, 40L));
            when(subjects.getPaymentSubject(30L)).thenReturn(subject(30));
            when(subjects.getPaymentSubject(40L)).thenReturn(subject(40));
        }
        var selected = ids;
        var invalid = subject(10); invalid.setStatus(1);
        when(subjects.getPaymentSubjectByCode(code)).thenReturn(invalid);
        assertServiceException(() -> resolver.resolve(selected), PAYMENT_SUBJECT_DISABLED);
        invalid.setStatus(0); invalid.setPlatformPublicKey("invalid");
        assertServiceException(() -> resolver.resolve(selected), PAYMENT_SUBJECT_CONFIG_INVALID);
    }

    @Test
    void invalidProductSelectionCannotSelectSubject() {
        assertServiceException(() -> resolver.resolve(List.of()), PRODUCT_SKU_INVALID);
        assertServiceException(() -> resolver.resolve(null), PRODUCT_SKU_INVALID);
        assertServiceException(() -> resolver.resolve(java.util.Arrays.asList(101L, null)), PRODUCT_SKU_INVALID);
        verifyNoInteractions(subjects, products);
    }
}
