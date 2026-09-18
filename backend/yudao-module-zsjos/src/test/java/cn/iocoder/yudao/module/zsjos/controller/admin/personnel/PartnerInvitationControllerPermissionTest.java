package cn.iocoder.yudao.module.zsjos.controller.admin.personnel;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStudentInvitationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationPageReqVO;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerInvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PartnerInvitationControllerPermissionTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean(name="ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean PartnerInvitationController controller() { return new PartnerInvitationController(); }
    }
    private AnnotationConfigApplicationContext context() {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.getBeanFactory().registerSingleton("service", mock(PartnerInvitationService.class));
        ctx.register(Config.class); ctx.refresh(); return ctx;
    }
    @Test void deniesStudentReadCreateAndCandidatesWithoutConfiguredPermission() {
        try (var ctx = context()) {
            var controller = ctx.getBean(PartnerInvitationController.class);
            assertThrows(AccessDeniedException.class, () -> controller.studentContext(88L));
            assertThrows(AccessDeniedException.class, () -> controller.createStudentInvitation(new PartnerStudentInvitationCreateReqVO()));
            assertThrows(AccessDeniedException.class, () -> controller.operatorCandidates(null, 1, 100));
            verifyNoInteractions(ctx.getBean(PartnerInvitationService.class));
        }
    }
    @Test void studentPermissionEnablesScopedReadButNotGlobalList() {
        try (var ctx = context()) {
            var security = ctx.getBean(SecurityFrameworkService.class);
            when(security.hasPermission("zsjos:partner-invitation:create-student")).thenReturn(true);
            when(security.hasAnyPermissions("zsjos:partner-invitation:query", "zsjos:partner-invitation:create",
                    "zsjos:partner-invitation:create-student")).thenReturn(true);
            var controller = ctx.getBean(PartnerInvitationController.class);
            controller.studentContext(88L);
            controller.createStudentInvitation(new PartnerStudentInvitationCreateReqVO());
            controller.operatorCandidates(null, 1, 100);
            verify(ctx.getBean(PartnerInvitationService.class)).getStudentContext(eq(88L), isNull());
            verify(ctx.getBean(PartnerInvitationService.class)).createStudentInvitation(any(), isNull());
            assertThrows(AccessDeniedException.class, () -> controller.page(new PartnerInvitationPageReqVO()));
        }
    }
    @Test void existingGeneralInvitationCandidatePermissionRemainsSupported() {
        try (var ctx = context()) {
            when(ctx.getBean(SecurityFrameworkService.class).hasAnyPermissions(
                    "zsjos:partner-invitation:query", "zsjos:partner-invitation:create",
                    "zsjos:partner-invitation:create-student")).thenReturn(true);
            ctx.getBean(PartnerInvitationController.class).operatorCandidates(null, 1, 100);
            verify(ctx.getBean(PartnerInvitationService.class)).getOperatorCandidatePage(null, 1, 100);
            assertThrows(AccessDeniedException.class, () -> ctx.getBean(PartnerInvitationController.class).studentContext(88L));
        }
    }
}
