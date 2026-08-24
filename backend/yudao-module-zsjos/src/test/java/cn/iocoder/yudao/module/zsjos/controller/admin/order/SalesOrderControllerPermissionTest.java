package cn.iocoder.yudao.module.zsjos.controller.admin.order;

import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderRepurchaseReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesOrderControllerPermissionTest {

    @Test
    void studentRepurchaseUsesDedicatedPermission() throws NoSuchMethodException {
        PreAuthorize authorization = SalesOrderController.class
                .getMethod("createStudentRepurchase", Long.class, SalesOrderRepurchaseReqVO.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('zsjos:sales-order:student-repurchase')", authorization.value());
    }
}
