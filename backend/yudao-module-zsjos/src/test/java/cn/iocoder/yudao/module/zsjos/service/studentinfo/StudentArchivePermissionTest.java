package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceObjectPermissionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentArchivePermissionTest {
    @Test void archiveEndpointsUseIndependentPermissionsWithoutGrantingRegistration() throws Exception {
        var archive=cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.StudentArchiveController.class;
        for (var type:java.util.List.of(archive,cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.StudentArchiveExtraController.class))
        for (var method:type.getDeclaredMethods()) {
            var annotation=method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            if(annotation==null) continue;
            if(method.getName().equals("saveVisibility")) {
                assertEquals("@ss.hasPermission('zsjos:exam-calendar:manage')",annotation.value());
                continue;
            }
            var granted=new java.util.HashSet<String>();
            var security=mock(cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService.class);
            when(security.hasPermission(anyString())).thenAnswer(call -> granted.contains(call.getArgument(0,String.class)));
            var context=new org.springframework.expression.spel.support.StandardEvaluationContext();
            context.setBeanResolver((ctx,name) -> security);
            var expression=new org.springframework.expression.spel.standard.SpelExpressionParser().parseExpression(annotation.value());
            granted.add("zsjos:student-info-form:read"); granted.add("zsjos:student-info-form:sensitive-read"); granted.add("zsjos:student-info-form:export");
            assertFalse(expression.getValue(context,Boolean.class),method.getName());
            granted.clear(); granted.add("zsjos:student-archive:read");
            if(java.util.Set.of("sensitive","image","upload","export","download").contains(method.getName()))
                assertFalse(expression.getValue(context,Boolean.class),method.getName());
            granted.add("zsjos:student-archive:update");
            granted.add("zsjos:student-archive:sensitive-read"); granted.add("zsjos:student-archive:export");
            assertTrue(expression.getValue(context,Boolean.class),method.getName());
            granted.remove("zsjos:student-archive:read");
            assertFalse(expression.getValue(context,Boolean.class),method.getName());
        }
        var registration=cn.iocoder.yudao.module.zsjos.controller.admin.lead.StudentInfoFormController.class;
        var image=registration.getMethod("image",Long.class,Long.class,jakarta.servlet.http.HttpServletResponse.class)
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value();
        assertTrue(image.contains("zsjos:student-info-form:sensitive-read"));
        assertFalse(image.contains("zsjos:student-archive:"));
    }
    @Test void readAndMaintenanceRequireExactVisibleService(){
        var service=mock(StudentServiceObjectPermissionProvider.class);
        var provider=new StudentArchivePermissionProvider();ReflectionTestUtils.setField(provider,"services",service);
        var user=new LoginUser();user.setId(7L);user.setUserType(2);
        SecurityFrameworkUtils.setLoginUser(user,new MockHttpServletRequest());
        try {
            when(service.hasPermission(10L,"read",7L)).thenReturn(true);
            when(service.hasPermission(10L,"archive-update",7L)).thenReturn(true);
            assertTrue(provider.hasPermission(10L,"read",7L));assertTrue(provider.hasPermission(10L,"update",7L));
            assertFalse(provider.hasPermission(11L,"read",7L));assertFalse(provider.hasPermission(11L,"update",7L));
            assertFalse(provider.hasPermission(10L,"unknown",7L));
        }finally{SecurityContextHolder.clearContext();}
    }
    @Test void controllerReadAndMaintenancePermissionsAreSeparate()throws Exception{
        var controller=cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.StudentArchiveController.class;
        String read=controller.getMethod("detail",Long.class).getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value();
        String save=controller.getMethod("save",Long.class,cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveVO.Save.class).getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value();
        assertTrue(read.contains("student-archive:read"));assertFalse(read.contains("student-archive:update"));
        assertTrue(save.contains("student-archive:read"));assertTrue(save.contains("student-archive:update"));
    }
}
