package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesOrderSubmitReqVOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void tearDown() {
        validator = null;
    }

    @Test
    void allowsBlankCityNameForOtherCityCode() {
        SalesOrderSubmitReqVO request = validRequest();
        request.setCityCode("OTHER");
        request.setCityName("");

        assertTrue(validator.validateProperty(request, "cityName").isEmpty());
    }

    @Test
    void validatesNestedItemsAndPaymentVouchers() {
        SalesOrderSubmitReqVO request = validRequest();
        request.getItems().get(0).setSpuRef("");
        request.getPaymentVouchers().get(0).setInfraFileId(null);

        var violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("items[0].spuRef")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("paymentVouchers[0].infraFileId")));
    }

    private static SalesOrderSubmitReqVO validRequest() {
        SalesOrderSubmitReqVO request = new SalesOrderSubmitReqVO();
        request.setStudentName("测试学员");
        request.setStudentNature("new_student");
        request.setProvinceCode("820000");
        request.setProvinceName("澳门特别行政区");
        request.setCityCode("OTHER");
        request.setCityName("");
        request.setServicePeriod("two_year");
        request.setStudentSource("agent_referral");
        request.setCustomerPaidAt(java.time.LocalDateTime.now());
        request.setFeeMode("retail");
        request.setPaymentMethod("company_qr");
        request.setIdempotencyKey("validation-test");

        SalesOrderSubmitReqVO.Item item = new SalesOrderSubmitReqVO.Item();
        item.setSpuRef("spu");
        item.setSkuRef("sku");
        item.setActualAmount(java.math.BigDecimal.ONE);
        request.setItems(List.of(item));

        SalesOrderSubmitReqVO.Attachment voucher = new SalesOrderSubmitReqVO.Attachment();
        voucher.setInfraFileId(1L);
        request.setPaymentVouchers(List.of(voucher));
        return request;
    }
}
