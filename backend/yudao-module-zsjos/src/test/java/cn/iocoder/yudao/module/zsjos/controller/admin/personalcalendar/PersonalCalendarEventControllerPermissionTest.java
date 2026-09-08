package cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar;

import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventListReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventSaveReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalCalendarEventControllerPermissionTest {
    @Test
    void endpointsUseIndependentPermissions() throws NoSuchMethodException {
        assertPermission("list", new Class<?>[]{PersonalCalendarEventListReqVO.class}, "zsjos:personal-calendar:query");
        assertPermission("create", new Class<?>[]{PersonalCalendarEventSaveReqVO.class}, "zsjos:personal-calendar:create");
        assertPermission("update", new Class<?>[]{Long.class, PersonalCalendarEventSaveReqVO.class}, "zsjos:personal-calendar:update");
        assertPermission("delete", new Class<?>[]{Long.class}, "zsjos:personal-calendar:delete");
    }

    private static void assertPermission(String method, Class<?>[] parameters, String permission)
            throws NoSuchMethodException {
        PreAuthorize annotation = PersonalCalendarEventController.class.getMethod(method, parameters)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('" + permission + "')", annotation.value());
    }
}
