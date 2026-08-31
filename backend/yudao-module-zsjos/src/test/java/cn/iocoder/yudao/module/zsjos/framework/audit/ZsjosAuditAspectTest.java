package cn.iocoder.yudao.module.zsjos.framework.audit;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.BusinessAuditController;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZsjosAuditAspectTest {

    @Mock private BusinessAuditService auditService;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    @Test
    void recordsSuccessfulSystemOperation() throws Throwable {
        Method method = Samples.class.getDeclaredMethod("run");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(Samples.class);
        when(auditService.begin(any())).thenReturn(42L);
        when(joinPoint.proceed()).thenReturn("done");

        Object result = new ZsjosAuditAspect(auditService).audit(joinPoint, method.getAnnotation(ZsjosAudit.class));

        assertEquals("done", result);
        ArgumentCaptor<ZsjosAuditOperation> operation = ArgumentCaptor.forClass(ZsjosAuditOperation.class);
        verify(auditService).begin(operation.capture());
        assertEquals("SYSTEM", operation.getValue().sourceType());
        verify(auditService).complete(eq(42L), eq(true), eq(0), isNull(), anyLong());
    }

    @Test
    void recordsFailureAndRethrowsOriginalException() throws Throwable {
        Method method = Samples.class.getDeclaredMethod("run");
        ServiceException failure = new ServiceException(1900001001, "stable failure");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(Samples.class);
        when(auditService.begin(any())).thenReturn(43L);
        when(joinPoint.proceed()).thenThrow(failure);

        ServiceException thrown = assertThrows(ServiceException.class,
                () -> new ZsjosAuditAspect(auditService).audit(joinPoint, method.getAnnotation(ZsjosAudit.class)));

        assertSame(failure, thrown);
        verify(auditService).complete(eq(43L), eq(false), eq(1900001001), eq("stable failure"), anyLong());
    }

    @Test
    void leavesControllerAuditingToHttpInterceptor() throws Throwable {
        Method method = Samples.class.getDeclaredMethod("run");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(BusinessAuditController.class);
        when(joinPoint.proceed()).thenReturn("done");

        Object result = new ZsjosAuditAspect(auditService).audit(joinPoint, method.getAnnotation(ZsjosAudit.class));

        assertEquals("done", result);
        verifyNoInteractions(auditService);
    }

    static class Samples {
        @ZsjosAudit(action = "sample.run", targetType = "sample")
        String run() {
            return "done";
        }
    }
}
