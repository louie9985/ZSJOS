package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudentContactControllerPermissionTest {

    @Test
    void collaboratorCandidatesAllowsAssignmentAndCorrectionPermissions() throws NoSuchMethodException {
        PreAuthorize authorization = StudentContactController.class
                .getMethod("getCollaboratorCandidates", Long.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasAnyPermissions('zsjos:student-collaborator:assign', "
                + "'zsjos:student-collaborator:correct', 'zsjos:student:director-operator-assign')", authorization.value());
    }

    @Test
    void attachmentUploadAllowsDeliveryStageSubmitPermission() throws NoSuchMethodException {
        PreAuthorize authorization = StudentContactController.class
                .getMethod("uploadAttachment", Long.class, MultipartFile.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals(true, authorization.value().contains("zsjos:student-contact:delivery-stage-submit"));
    }

    @Test
    void contextReadAllowsDirectorAndPlannerQueryPermissions() throws NoSuchMethodException {
        PreAuthorize authorization = StudentContactController.class
                .getMethod("getContext", Long.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasAnyPermissions('zsjos:student:query-my', 'zsjos:media-student:query-my')",
                authorization.value());
    }

    @Test
    void contactRecordsReadAllowsDirectorAndPlannerQueryPermissions() throws NoSuchMethodException {
        PreAuthorize authorization = StudentContactController.class
                .getMethod("getRecords", Long.class, cn.iocoder.yudao.framework.common.pojo.PageParam.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasAnyPermissions('zsjos:student:query-my', 'zsjos:media-student:query-my')",
                authorization.value());
    }

}
