package cn.iocoder.yudao.module.zsjos.framework.audit;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents new ZSJOS endpoints from silently bypassing the default audit classification. */
class ZsjosAuditCoverageTest {

    private static final String CONTROLLER_PACKAGE = "cn.iocoder.yudao.module.zsjos.controller";

    @Test
    void everyControllerOperationHasDeterministicAuditTreatment() throws Exception {
        int getCount = 0;
        int postCount = 0;
        int putCount = 0;
        int deleteCount = 0;
        int auditedCount = 0;
        int readOnlyPostCount = 0;
        for (Class<?> controller : controllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                ZsjosAudit audit = AnnotatedElementUtils.findMergedAnnotation(method, ZsjosAudit.class);
                if (method.isAnnotationPresent(GetMapping.class)) {
                    getCount++;
                    boolean explicitlyAudited = audit != null && audit.mode() != ZsjosAudit.Mode.READ_ONLY;
                    assertEquals(explicitlyAudited, ZsjosAuditInterceptor.mustAudit("GET", method, audit), method.toString());
                    if (explicitlyAudited) auditedCount++;
                }
                if (method.isAnnotationPresent(PostMapping.class)) {
                    postCount++;
                    boolean audited = ZsjosAuditInterceptor.mustAudit("POST", method, audit);
                    boolean explicitlyReadOnly = audit != null && audit.mode() == ZsjosAudit.Mode.READ_ONLY;
                    assertEquals(!explicitlyReadOnly, audited, method.toString());
                    if (explicitlyReadOnly) readOnlyPostCount++;
                    if (audited) auditedCount++;
                }
                if (method.isAnnotationPresent(PutMapping.class)) {
                    putCount++;
                    assertTrue(ZsjosAuditInterceptor.mustAudit("PUT", method, audit), method.toString());
                    auditedCount++;
                }
                if (method.isAnnotationPresent(DeleteMapping.class)) {
                    deleteCount++;
                    assertTrue(ZsjosAuditInterceptor.mustAudit("DELETE", method, audit), method.toString());
                    auditedCount++;
                }
            }
        }
        assertEquals(241, getCount, "Review GET audit classification when endpoint inventory changes");
        assertEquals(237, postCount, "Review POST audit classification when endpoint inventory changes");
        assertEquals(28, readOnlyPostCount, "Every POST viewing endpoint must remain explicitly classified");
        assertEquals(73, putCount, "Review PUT audit classification when endpoint inventory changes");
        assertEquals(10, deleteCount, "Review DELETE audit classification when endpoint inventory changes");
        assertTrue(auditedCount > 250, "The mutation audit inventory unexpectedly shrank");
    }

    private static Set<Class<?>> controllerClasses() throws Exception {
        Set<Class<?>> result = new HashSet<>();
        String root = CONTROLLER_PACKAGE.replace('.', '/');
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + root + "/**/*Controller.class");
        for (Resource resource : resources) {
            String uri = resource.getURL().toString();
            int start = uri.lastIndexOf(root);
            if (start < 0) continue;
            String className = uri.substring(start).replace('/', '.').replaceAll("\\.class$", "");
            result.add(Class.forName(className));
        }
        assertFalse(result.isEmpty(), "No ZSJOS controllers were discovered");
        return result;
    }
}
