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
    @Test void readAndMaintenanceRequireExactVisibleService(){
        var service=mock(StudentServiceObjectPermissionProvider.class);
        var provider=new StudentArchivePermissionProvider();ReflectionTestUtils.setField(provider,"services",service);
        var user=new LoginUser();user.setId(7L);user.setUserType(2);
        SecurityFrameworkUtils.setLoginUser(user,new MockHttpServletRequest());
        try {
            when(service.hasPermission(10L,"read",7L)).thenReturn(true);
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
