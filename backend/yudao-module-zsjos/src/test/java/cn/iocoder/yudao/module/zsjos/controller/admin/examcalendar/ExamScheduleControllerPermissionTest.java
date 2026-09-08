package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamSchedulePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleSaveReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExamScheduleControllerPermissionTest {
    @Test
    void endpointsSplitQueryAndManagePermissions() throws NoSuchMethodException {
        assertPermission("page", new Class<?>[]{ExamSchedulePageReqVO.class}, "zsjos:exam-calendar:query");
        assertPermission("rough", new Class<?>[]{ExamSchedulePageReqVO.class}, "zsjos:exam-calendar:query");
        assertPermission("categoryOptions", new Class<?>[]{}, "zsjos:exam-calendar:query");
        assertPermission("productOptions", new Class<?>[]{}, "zsjos:exam-calendar:manage");
        assertPermission("create", new Class<?>[]{ExamScheduleSaveReqVO.class}, "zsjos:exam-calendar:manage");
        assertPermission("update", new Class<?>[]{Long.class, ExamScheduleSaveReqVO.class}, "zsjos:exam-calendar:manage");
        assertPermission("publish", new Class<?>[]{Long.class}, "zsjos:exam-calendar:manage");
        assertPermission("revoke", new Class<?>[]{Long.class}, "zsjos:exam-calendar:manage");
    }

    private static void assertPermission(String method, Class<?>[] parameters, String permission)
            throws NoSuchMethodException {
        PreAuthorize annotation = ExamScheduleController.class.getMethod(method, parameters)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('" + permission + "')", annotation.value());
    }
}
