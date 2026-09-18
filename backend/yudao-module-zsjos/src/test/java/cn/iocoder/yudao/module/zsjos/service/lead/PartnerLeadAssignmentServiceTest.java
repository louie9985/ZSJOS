package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation.UserRelationSceneDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.userrelation.UserRelationSceneMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadAssignmentConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class PartnerLeadAssignmentServiceTest {
    @InjectMocks PartnerLeadAssignmentService service;
    @Mock UserRelationSceneMapper sceneMapper;
    @Mock LeadAssignmentRelationMapper relationMapper;
    @Mock LeadAssignmentRelationLogMapper logMapper;
    @Mock PartnerMapper partnerMapper;
    @Mock AdminUserApi userApi;
    @Mock PermissionApi permissionApi;

    private UserRelationSceneDO scene() { var s = new UserRelationSceneDO(); s.setCode(PARTNER_SCENE); s.setStatus(0); return s; }
    private LeadAssignmentRelationDO relation(long target, String identity) {
        var r = new LeadAssignmentRelationDO(); r.setSourceUserId(7L); r.setTargetUserId(target); r.setStatus(0); r.setOwnerIdentity(identity); return r;
    }
    private void enabled(long id) {
        var u = new AdminUserRespDTO(); u.setId(id); u.setStatus(0);
        when(userApi.getUser(id)).thenReturn(u); when(permissionApi.hasAnyPermissions(id, PERMISSION_ACCEPT)).thenReturn(true);
    }
    @Test void missingSceneHidesChoice() {
        assertFalse(service.options(7L).configured());
        assertEquals(PARTNER_ASSIGNMENT_NOT_CONFIGURED.getCode(), assertThrows(ServiceException.class, () -> service.resolve(7L)).getCode());
    }
    @Test void emptyRelationshipHidesChoice() {
        when(sceneMapper.selectByCode(PARTNER_SCENE)).thenReturn(scene());
        when(relationMapper.selectListBySourceUserIds(PARTNER_SCENE, Set.of(7L))).thenReturn(List.of());
        assertFalse(service.options(7L).configured());
    }
    @Test void educationUsesExplicitIdentityAndPermission() {
        when(sceneMapper.selectByCode(PARTNER_SCENE)).thenReturn(scene());
        when(relationMapper.selectListBySourceUserIds(PARTNER_SCENE, Set.of(7L))).thenReturn(List.of(relation(8,"education")));
        enabled(8);
        assertTrue(service.options(7L).specifiedAvailable());
        assertEquals(new PartnerLeadAssignmentService.Target(8L,"education"), service.resolve(7L));
        assertNull(service.options(7L).reason());
    }
    @Test void multipleNeverSelectsFirst() {
        when(sceneMapper.selectByCode(PARTNER_SCENE)).thenReturn(scene());
        when(relationMapper.selectListBySourceUserIds(PARTNER_SCENE, Set.of(7L))).thenReturn(List.of(relation(8,"sales"),relation(9,"education")));
        assertFalse(service.options(7L).specifiedAvailable());
        assertEquals(PARTNER_ASSIGNMENT_MULTIPLE.getCode(),assertThrows(ServiceException.class,()->service.resolve(7L)).getCode());
        verifyNoInteractions(userApi);
    }
    @Test void revokedPermissionFailsWithoutFallback() {
        var user = new AdminUserRespDTO(); user.setStatus(0); when(userApi.getUser(8L)).thenReturn(user);
        assertEquals(PARTNER_ASSIGNMENT_UNAVAILABLE.getCode(),assertThrows(ServiceException.class,()->service.validateTarget(8L,"education")).getCode());
    }
    @Test void disabledTargetFailsWithoutPermissionLookup() {
        var user = new AdminUserRespDTO(); user.setStatus(1); when(userApi.getUser(8L)).thenReturn(user);
        assertThrows(ServiceException.class,()->service.validateTarget(8L,"sales")); verifyNoInteractions(permissionApi);
    }
    @Test void invalidIdentityNeverInferredFromUser() {
        assertEquals(PARTNER_ASSIGNMENT_IDENTITY_INVALID.getCode(),assertThrows(ServiceException.class,()->service.validateTarget(8L,null)).getCode());
        verifyNoInteractions(userApi);
    }
    @Test void reviewValidatesSnapshotWithoutResolvingNewRelationship() {
        enabled(8); service.validateTarget(8L,"education"); verifyNoInteractions(sceneMapper, relationMapper);
    }
    @Test void appendCannotCreateSecondReceiverAndLocksBeforeReading() {
        when(sceneMapper.lockByCode(PARTNER_SCENE)).thenReturn(scene());
        var p = new PartnerDO(); p.setStatus("enabled"); when(partnerMapper.selectById(7L)).thenReturn(p);
        when(relationMapper.selectListBySourceUserIds(PARTNER_SCENE, Set.of(7L))).thenReturn(List.of(relation(8,"sales")));
        var req = new UserRelationSaveReqVO(); req.setSourceUserIds(Set.of(7L)); req.setTargetUserIds(Set.of(9L)); req.setMode("append"); req.setOwnerIdentity("education");
        assertEquals(PARTNER_ASSIGNMENT_MULTIPLE.getCode(),assertThrows(ServiceException.class,()->service.save(req,1L)).getCode());
        var order = inOrder(sceneMapper,relationMapper); order.verify(sceneMapper).lockByCode(PARTNER_SCENE); order.verify(relationMapper).selectListBySourceUserIds(PARTNER_SCENE, Set.of(7L));
        verify(relationMapper,never()).insert(any(LeadAssignmentRelationDO.class));
    }
    @Test void replacePersistsEducationIdentity() {
        when(sceneMapper.lockByCode(PARTNER_SCENE)).thenReturn(scene());
        var p = new PartnerDO(); p.setStatus("enabled"); when(partnerMapper.selectById(7L)).thenReturn(p);
        enabled(8);
        var req = new UserRelationSaveReqVO(); req.setSourceUserIds(Set.of(7L)); req.setTargetUserIds(Set.of(8L)); req.setMode("replace"); req.setOwnerIdentity("education");
        service.save(req,1L);
        var capture=ArgumentCaptor.forClass(LeadAssignmentRelationDO.class); verify(relationMapper).insert(capture.capture());
        assertEquals("education",capture.getValue().getOwnerIdentity()); assertEquals(PARTNER_SCENE,capture.getValue().getScene());
    }
}
