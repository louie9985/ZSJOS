package cn.iocoder.yudao.module.zsjos.controller;

import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.StudentDeliveryController;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliverySubmissionReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class StudentDeliveryControllerContractTest {

    @Test
    void obsoleteCompletionEndpointIsRemoved() {
        var paths = Arrays.stream(StudentDeliveryController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(PostMapping.class).value()))
                .toList();

        assertFalse(paths.contains("/plan/{planId}/complete"));
    }

    @Test
    void formSubmissionRetainsItsPermission() throws NoSuchMethodException {
        var method = StudentDeliveryController.class.getMethod("submit", StudentDeliverySubmissionReqVO.class);

        assertArrayEquals(new String[]{"/submission"}, method.getAnnotation(PostMapping.class).value());
        assertEquals("@ss.hasPermission('zsjos:student-delivery:submit')",
                method.getAnnotation(PreAuthorize.class).value());
    }
}
