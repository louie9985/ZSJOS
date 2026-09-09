package cn.iocoder.yudao.module.zsjos.controller.admin.content;

import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadCompleteReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentVersionControllerPermissionTest {

    private static final String CREATE_OR_EDIT =
            "@ss.hasAnyPermissions('zsjos:content:create','zsjos:content:edit')";

    @Test
    void versionCreationAndFileUploadUseTheSamePermissionBoundary() throws NoSuchMethodException {
        assertEquals(CREATE_OR_EDIT, permission("create",
                cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionSaveReqVO.class));
        assertEquals(CREATE_OR_EDIT, permission("initUpload", ZsjosDirectUploadInitReqVO.class));
        assertEquals(CREATE_OR_EDIT, permission("completeUpload", ZsjosDirectUploadCompleteReqVO.class));
    }

    private String permission(String method, Class<?> parameterType) throws NoSuchMethodException {
        return ContentVersionController.class.getMethod(method, parameterType)
                .getAnnotation(PreAuthorize.class).value();
    }
}
