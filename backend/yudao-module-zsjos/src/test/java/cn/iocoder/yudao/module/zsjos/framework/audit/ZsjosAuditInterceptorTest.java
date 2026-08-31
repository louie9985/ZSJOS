package cn.iocoder.yudao.module.zsjos.framework.audit;

import cn.iocoder.yudao.module.zsjos.controller.admin.audit.BusinessAuditController;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.PartnerAppAuthController;
import cn.iocoder.yudao.module.zsjos.controller.pub.payment.PublicPaymentController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZsjosAuditInterceptorTest {

    @Test
    void classifiesReadAndWriteOperations() throws Exception {
        Method search = Samples.class.getDeclaredMethod("searchPage");
        Method create = Samples.class.getDeclaredMethod("create");
        Method explicitRead = Samples.class.getDeclaredMethod("calculate");
        Method sensitiveRead = Samples.class.getDeclaredMethod("download");
        Method explicitWrite = Samples.class.getDeclaredMethod("refresh");

        assertFalse(ZsjosAuditInterceptor.mustAudit("GET", create, null));
        assertTrue(ZsjosAuditInterceptor.mustAudit("POST", search, null));
        assertTrue(ZsjosAuditInterceptor.mustAudit("POST", create, null));
        assertTrue(ZsjosAuditInterceptor.mustAudit("PUT", create, null));
        assertFalse(ZsjosAuditInterceptor.mustAudit("POST", explicitRead,
                explicitRead.getAnnotation(ZsjosAudit.class)));
        assertTrue(ZsjosAuditInterceptor.mustAudit("GET", sensitiveRead,
                sensitiveRead.getAnnotation(ZsjosAudit.class)));
        assertTrue(ZsjosAuditInterceptor.mustAudit("GET", explicitWrite,
                explicitWrite.getAnnotation(ZsjosAudit.class)));
        assertTrue(ZsjosAuditInterceptor.sourceType(BusinessAuditController.class).equals("ADMIN"));
        assertTrue(ZsjosAuditInterceptor.sourceType(PartnerAppAuthController.class).equals("PARTNER"));
        assertTrue(ZsjosAuditInterceptor.sourceType(PublicPaymentController.class).equals("PUBLIC_CALLBACK"));
    }

    static class Samples {
        void searchPage() {}
        void create() {}
        @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
        void calculate() {}
        @ZsjosAudit(mode = ZsjosAudit.Mode.SENSITIVE_READ)
        void download() {}
        @ZsjosAudit
        void refresh() {}
    }

}
