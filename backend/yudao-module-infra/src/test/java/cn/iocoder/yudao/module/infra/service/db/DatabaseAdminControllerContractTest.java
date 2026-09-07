package cn.iocoder.yudao.module.infra.service.db;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.infra.controller.admin.db.DatabaseAdminController;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.db.vo.DatabaseAdminRowUpdateReqVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseAdminControllerContractTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
        @Bean DatabaseAdminService service() { return mock(DatabaseAdminService.class); }
        @Bean DatabaseAdminController controller() { return new DatabaseAdminController(); }
    }

    @Test
    void configuredPermissionIsEnforcedBeforeWrites() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(DatabaseAdminController.class);
            var service = context.getBean(DatabaseAdminService.class);
            var req = new DatabaseAdminRowUpdateReqVO().setDataSourceConfigId(1L).setTableName("fixture")
                    .setPrimaryKeyValue("1").setValues(Map.of("note", ""));
            assertThrows(AccessDeniedException.class, () -> controller.updateRow(req));
            verifyNoInteractions(service);
            when(context.getBean(SecurityFrameworkService.class).hasPermission("infra:database-admin:update")).thenReturn(true);
            assertEquals(true, controller.updateRow(req).getData());
            verify(service).updateRow(req);
            assertThrows(AccessDeniedException.class, () -> controller.createRow(new DatabaseAdminRowCreateReqVO()));
            assertThrows(AccessDeniedException.class, () -> controller.getTableDetail(1L, "fixture"));
        }
    }

    @Test
    void createDefaultsAndSparseUpdateHaveSeparateValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new DatabaseAdminRowCreateReqVO().setDataSourceConfigId(1L)
                    .setTableName("fixture").setValues(Map.of())).isEmpty());
            assertFalse(validator.validate(new DatabaseAdminRowUpdateReqVO().setDataSourceConfigId(1L)
                    .setTableName("fixture").setPrimaryKeyValue("1").setValues(Map.of())).isEmpty());
        }
    }

    @Test
    void accessLogsNeverCaptureArbitraryRows() {
        for (var method : DatabaseAdminController.class.getDeclaredMethods()) {
            var annotation = method.getAnnotation(ApiAccessLog.class);
            assertNotNull(annotation, method.getName());
            assertFalse(annotation.requestEnable());
            assertFalse(annotation.responseEnable());
        }
    }
}
