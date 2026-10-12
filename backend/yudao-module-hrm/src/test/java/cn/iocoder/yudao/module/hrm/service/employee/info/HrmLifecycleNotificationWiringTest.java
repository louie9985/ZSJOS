package cn.iocoder.yudao.module.hrm.service.employee.info;

import cn.iocoder.yudao.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.employment.HrmEmployeeChangeRecordMapper;
import cn.iocoder.yudao.module.hrm.service.config.HrmConfigService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.service.notify.NotifySceneRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.*;

class HrmLifecycleNotificationWiringTest {
    @Test void sceneRegistrationDoesNotCreateNotificationServiceCycle() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(HrmEmployeeMapper.class, () -> mock(HrmEmployeeMapper.class));
            context.registerBean(HrmEmployeeChangeRecordMapper.class, () -> mock(HrmEmployeeChangeRecordMapper.class));
            context.registerBean(HrmConfigService.class, () -> mock(HrmConfigService.class));
            context.registerBean(DeptApi.class, () -> mock(DeptApi.class));
            context.registerBean(AdminUserApi.class, () -> mock(AdminUserApi.class));
            context.registerBean(NotifyBusinessEventApi.class, () -> {
                context.getBean(NotifySceneRegistry.class);
                return mock(NotifyBusinessEventApi.class);
            });
            context.register(HrmLifecycleReminderService.class, HrmLifecycleNotifySceneProvider.class, NotifySceneRegistry.class);
            context.refresh();
            assertEquals(3, context.getBean(NotifySceneRegistry.class).getScenes().size());
            assertNotNull(context.getBean(HrmLifecycleReminderService.class));
        }
    }
}
