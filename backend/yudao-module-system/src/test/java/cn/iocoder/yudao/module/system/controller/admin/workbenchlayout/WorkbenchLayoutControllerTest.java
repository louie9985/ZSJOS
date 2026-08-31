package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout;

import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.WorkbenchLayoutPreviewReqVO;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.WorkbenchLayoutPublishReqVO;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.WorkbenchLayoutRestoreReqVO;
import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.WorkbenchLayoutSaveReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchLayoutControllerTest {

    @Test
    void shouldSplitQueryUpdateAndPublishPermissions() throws NoSuchMethodException {
        assertPermission("getCandidates", "system:workbench-layout:query");
        assertPermission("getDraft", "system:workbench-layout:query", String.class, Long.class);
        assertPermission("preview", "system:workbench-layout:query", WorkbenchLayoutPreviewReqVO.class);
        assertPermission("getPublishImpact", "system:workbench-layout:query", String.class, Long.class);
        assertPermission("getVersions", "system:workbench-layout:query", String.class, Long.class);
        assertPermission("saveDraft", "system:workbench-layout:update", WorkbenchLayoutSaveReqVO.class);
        assertPermission("restoreDraft", "system:workbench-layout:update", WorkbenchLayoutRestoreReqVO.class);
        assertPermission("publish", "system:workbench-layout:publish", WorkbenchLayoutPublishReqVO.class);
    }

    private void assertPermission(String methodName, String permission, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = WorkbenchLayoutController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasPermission('" + permission + "')");
    }

}
