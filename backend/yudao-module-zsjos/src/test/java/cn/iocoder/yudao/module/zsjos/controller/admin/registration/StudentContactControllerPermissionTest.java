package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudentContactControllerPermissionTest {

    @Test
    void onlyActiveCommandRoutesAreExposed() {
        java.util.Set<String> routes = java.util.Arrays.stream(StudentContactController.class.getMethods())
                .flatMap(method -> {
                    var post = method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
                    var put = method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
                    return java.util.stream.Stream.concat(
                            post == null ? java.util.stream.Stream.empty() : java.util.Arrays.stream(post.value()),
                            put == null ? java.util.stream.Stream.empty() : java.util.Arrays.stream(put.value()));
                }).collect(java.util.stream.Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertFalse(routes.contains("/{relationId}/delivery-stage"));
        org.junit.jupiter.api.Assertions.assertFalse(routes.contains("/{relationId}/exam-date"));
        org.junit.jupiter.api.Assertions.assertTrue(routes.contains("/{relationId}/contacts"));
    }

    @Test
    void collaboratorCandidatesAllowsAssignmentAndCorrectionPermissions() throws NoSuchMethodException {
        PreAuthorize authorization = StudentContactController.class
                .getMethod("getCollaboratorCandidates", Long.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasAnyPermissions('zsjos:student-collaborator:assign', "
                + "'zsjos:student-collaborator:correct', 'zsjos:student:director-operator-assign')", authorization.value());
    }

    @Test
    void attachmentUploadUsesOnlyActiveCommandPermissions() throws NoSuchMethodException {
        PreAuthorize authorization = StudentContactController.class
                .getMethod("uploadAttachment", Long.class, MultipartFile.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasAnyPermissions('zsjos:student-contact:first-submit', 'zsjos:student-contact:study-plan-submit', "
                + "'zsjos:student-contact:submit', 'zsjos:student-contact:checklist-update', "
                + "'zsjos:student-contact-extension:apply')", authorization.value());
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
