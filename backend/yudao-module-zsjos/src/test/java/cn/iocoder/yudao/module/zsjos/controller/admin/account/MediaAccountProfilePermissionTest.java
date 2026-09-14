package cn.iocoder.yudao.module.zsjos.controller.admin.account;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.*;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MediaAccountProfilePermissionTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean(name="ss") SecurityFrameworkService security(){return mock(SecurityFrameworkService.class);}
        @Bean MediaAccountProfileController controller(){return new MediaAccountProfileController();}
    }
    private AnnotationConfigApplicationContext context(){
        var ctx=new AnnotationConfigApplicationContext();
        ctx.getBeanFactory().registerSingleton("service",mock(MediaAccountProfileService.class));
        ctx.register(Config.class);ctx.refresh();return ctx;
    }
    @Test void deniesUnconfiguredQueryPatchAppendAndHistoryBeforeService(){
        try(var ctx=context()){
            var controller=ctx.getBean(MediaAccountProfileController.class);
            assertThrows(AccessDeniedException.class,()->controller.get(1L));
            assertThrows(AccessDeniedException.class,()->controller.patch(1L,new Patch()));
            assertThrows(AccessDeniedException.class,()->controller.append(1L,new RecordRequest()));
            assertThrows(AccessDeniedException.class,()->controller.history(1L,new PageParam()));
            verifyNoInteractions(ctx.getBean(MediaAccountProfileService.class));
        }
    }
    @Test void configuredMaintenanceReachesObjectAndFieldCheckedService(){
        try(var ctx=context()){
            when(ctx.getBean(SecurityFrameworkService.class).hasAnyPermissions("zsjos:media-account:edit","zsjos:media-account:maintenance")).thenReturn(true);
            when(ctx.getBean(MediaAccountProfileService.class).patch(eq(1L),any(),isNull())).thenReturn(2);
            assertEquals(2,ctx.getBean(MediaAccountProfileController.class).patch(1L,new Patch()).getData());
        }
    }
    @Test void configuredQueryReachesObjectCheckedService(){
        try(var ctx=context()){
            when(ctx.getBean(SecurityFrameworkService.class).hasPermission("zsjos:media-account:query")).thenReturn(true);
            ctx.getBean(MediaAccountProfileController.class).get(1L);
            verify(ctx.getBean(MediaAccountProfileService.class)).get(eq(1L), isNull());
        }
    }
    @Test void maintenanceDoesNotReplaceConfiguredQuery(){
        try(var ctx=context()){
            when(ctx.getBean(SecurityFrameworkService.class).hasAnyPermissions("zsjos:media-account:edit","zsjos:media-account:maintenance")).thenReturn(true);
            assertThrows(AccessDeniedException.class,()->ctx.getBean(MediaAccountProfileController.class).get(1L));
            verifyNoInteractions(ctx.getBean(MediaAccountProfileService.class));
        }
    }
}
