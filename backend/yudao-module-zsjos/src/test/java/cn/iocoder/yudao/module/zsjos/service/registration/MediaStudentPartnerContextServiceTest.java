package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStudentInvitationContextRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerInvitationService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerStudentLinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaStudentPartnerContextServiceTest {
    @InjectMocks private MediaStudentPartnerContextService service;
    @Mock private MyStudentService students;
    @Mock private PermissionApi permissions;
    @Mock private ServiceRelationMapper relations;
    @Mock private PartnerStudentLinkService links;
    @Mock private PartnerInvitationService invitations;

    @Test
    void operatorWithCreatePermissionReadsBindingWithoutReceivingInvitation() {
        when(permissions.hasAnyPermissions(45L, "zsjos:partner-invitation:create-student")).thenReturn(true);
        when(links.hasActiveStudentLink(6028L)).thenReturn(true);
        var result = service.getContext(45L, 6028L);
        assertTrue(result.isOpened());
        assertFalse(result.isCanInviteStudent());
        assertNull(result.getInvitation());
        assertNull(result.getDefaultOperatorUserId());
        verify(students).getMediaStudent(45L, 6028L);
        verifyNoInteractions(invitations);
    }

    @Test
    void readerWithoutCreatePermissionCanReadUnboundStatus() {
        var result = service.getContext(45L, 6028L);
        assertFalse(result.isOpened());
        assertFalse(result.isCanInviteStudent());
        verify(links).hasActiveStudentLink(6028L);
        verifyNoInteractions(relations, invitations);
    }

    @Test
    void acceptedDirectorWithPermissionRetainsInvitationContext() {
        when(permissions.hasAnyPermissions(42L, "zsjos:partner-invitation:create-student")).thenReturn(true);
        when(relations.selectActiveByContentDirectorAndPerson(42L, 6028L))
                .thenReturn(java.util.List.of(new ServiceRelationDO()));
        var original = new PartnerStudentInvitationContextRespVO();
        original.setDefaultOperatorUserId(45L);
        original.setOperatorAssignmentConflict(true);
        when(invitations.getStudentContext(6028L, 42L)).thenReturn(original);
        var result = service.getContext(42L, 6028L);
        assertTrue(result.isCanInviteStudent());
        assertEquals(45L, result.getDefaultOperatorUserId());
        assertTrue(result.isOperatorAssignmentConflict());
        verifyNoInteractions(links);
    }

    @Test
    void inaccessibleOrOtherTenantStudentIsRejectedBeforeReadingBinding() {
        doThrow(new IllegalStateException("student outside current visibility"))
                .when(students).getMediaStudent(45L, 6028L);
        assertThrows(IllegalStateException.class, () -> service.getContext(45L, 6028L));
        verifyNoInteractions(permissions, relations, links, invitations);
    }

    @Test
    void bindingFailureIsNotPresentedAsUnbound() {
        when(links.hasActiveStudentLink(6028L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> service.getContext(45L, 6028L));
    }

    @Test
    void endpointRequiresMediaReadAndServiceRequiresStudentObjectRead() throws Exception {
        var endpoint = cn.iocoder.yudao.module.zsjos.controller.admin.registration.MediaStudentController.class
                .getMethod("partnerContext", Long.class);
        assertEquals("@ss.hasPermission('zsjos:media-student:query-my')", endpoint
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value());
        var annotation = MediaStudentPartnerContextService.class.getMethod("getContext", Long.class, Long.class)
                .getAnnotation(cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission.class);
        assertEquals("student", annotation.bizType());
        assertEquals("#personId", annotation.bizId());
        assertEquals("read", annotation.action());
    }
}
