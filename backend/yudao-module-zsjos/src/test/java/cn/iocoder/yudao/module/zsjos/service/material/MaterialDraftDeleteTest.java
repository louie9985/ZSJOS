package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.MaterialController;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialApprovalRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialVersionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialDraftDeleteTest {
    @Mock MaterialMapper materialMapper;
    @Mock MaterialVersionMapper versionMapper;
    @Mock MaterialApprovalRoundMapper approvalRoundMapper;
    @Mock PermissionApi permissionApi;
    @InjectMocks MaterialService service;
    @InjectMocks MaterialObjectPermissionProvider provider;
    MaterialDO material;
    MaterialVersionDO draft;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        material = new MaterialDO().setId(1L).setOwnerUserId(9L).setVersion(4)
                .setCurrentDraftVersionId(3L).setStatus("DRAFT");
        draft = new MaterialVersionDO().setId(3L).setMaterialId(1L).setStatus("DRAFT");
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    void authorized() {
        when(materialMapper.selectByIdForUpdate(1L, 1L)).thenReturn(material);
        when(permissionApi.hasAnyPermissions(9L, "zsjos:material:delete-draft")).thenReturn(true);
    }
    void error(int expected, Runnable action) {
        assertEquals(expected, assertThrows(ServiceException.class, action::run).getCode());
        verify(materialMapper, never()).deleteDraft(anyLong(), anyInt());
    }

    @Test void deletesOnlyLiveOwnedUnsubmittedDraftWithExpectedVersion() {
        authorized();
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        when(materialMapper.deleteDraft(1L, 4)).thenReturn(1);
        service.deleteDraft(1L, 4, 9L);
        verify(materialMapper).deleteDraft(1L, 4);
        verifyNoMoreInteractions(versionMapper);
    }
    @Test void refusesOwnerWithoutConfiguredPermission() {
        when(materialMapper.selectByIdForUpdate(1L, 1L)).thenReturn(material);
        error(MATERIAL_PERMISSION_DENIED.getCode(), () -> service.deleteDraft(1L, 4, 9L));
    }
    @Test void refusesForeignOwnerEvenForManager() {
        material.setOwnerUserId(20L);
        when(materialMapper.selectByIdForUpdate(1L, 1L)).thenReturn(material);
        error(MATERIAL_PERMISSION_DENIED.getCode(), () -> service.deleteDraft(1L, 4, 9L));
        when(materialMapper.selectById(1L)).thenReturn(material);
        when(permissionApi.hasAnyPermissions(9L, "zsjos:material:manage")).thenReturn(true);
        assertFalse(provider.hasPermission(1L, "delete-draft", 9L));
    }
    @Test void refusesStaleVersionBeforeDeletion() {
        authorized();
        error(MATERIAL_VERSION_CONFLICT.getCode(), () -> service.deleteDraft(1L, 3, 9L));
    }
    @ParameterizedTest @ValueSource(strings = {"IN_APPROVAL", "REJECTED", "EFFECTIVE", "DISABLED"})
    void refusesNonDraftMaterial(String status) {
        authorized(); material.setStatus(status);
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        error(MATERIAL_STATE_INVALID.getCode(), () -> service.deleteDraft(1L, 4, 9L));
    }
    @Test void refusesEffectiveMaterialRevision() {
        authorized(); material.setCurrentEffectiveVersionId(2L);
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        error(MATERIAL_STATE_INVALID.getCode(), () -> service.deleteDraft(1L, 4, 9L));
    }
    @ParameterizedTest @ValueSource(strings = {"IN_APPROVAL", "REJECTED", "EFFECTIVE"})
    void refusesChangedVersionState(String status) {
        authorized(); draft.setStatus(status);
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        error(MATERIAL_STATE_INVALID.getCode(), () -> service.deleteDraft(1L, 4, 9L));
    }
    @Test void refusesDraftWithWorkflowHistory() {
        authorized(); draft.setProcessInstanceId("existing-workflow");
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        error(MATERIAL_STATE_INVALID.getCode(), () -> service.deleteDraft(1L, 4, 9L));
    }
    @Test void deletesWithdrawnDraftAndAdvertisesActionWithoutClearingHistory() {
        authorized(); draft.setProcessInstanceId("cancelled-workflow");
        when(permissionApi.hasAnyPermissions(9L, "zsjos:material:update")).thenReturn(false);
        when(permissionApi.hasAnyPermissions(9L, "zsjos:material:submit")).thenReturn(false);
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        when(versionMapper.selectById(3L)).thenReturn(draft);
        when(approvalRoundMapper.selectByProcessInstanceId("cancelled-workflow"))
                .thenReturn(new MaterialApprovalRoundDO().setMaterialVersionId(3L).setStatus("CANCELLED"));
        when(materialMapper.deleteDraft(1L, 4)).thenReturn(1);
        assertTrue(service.availableActions(material, draft, 9L, false).contains("DELETE_DRAFT"));
        service.deleteDraft(1L, 4, 9L);
        assertEquals("cancelled-workflow", draft.getProcessInstanceId());
        verify(materialMapper).deleteDraft(1L, 4);
        verify(approvalRoundMapper, never()).updateById(any(MaterialApprovalRoundDO.class));
    }
    @ParameterizedTest @ValueSource(strings = {"IN_APPROVAL", "EFFECTIVE", "REJECTED"})
    void refusesUncancelledProcessEvenWhenVersionClaimsDraft(String status) {
        authorized(); draft.setProcessInstanceId("workflow");
        when(permissionApi.hasAnyPermissions(9L, "zsjos:material:update")).thenReturn(false);
        when(permissionApi.hasAnyPermissions(9L, "zsjos:material:submit")).thenReturn(false);
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        when(versionMapper.selectById(3L)).thenReturn(draft);
        when(approvalRoundMapper.selectByProcessInstanceId("workflow"))
                .thenReturn(new MaterialApprovalRoundDO().setMaterialVersionId(3L).setStatus(status));
        assertFalse(service.availableActions(material, draft, 9L, false).contains("DELETE_DRAFT"));
        error(MATERIAL_STATE_INVALID.getCode(), () -> service.deleteDraft(1L, 4, 9L));
    }
    @Test void refusesCancelledRoundBelongingToAnotherVersion() {
        authorized(); draft.setProcessInstanceId("workflow");
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        when(approvalRoundMapper.selectByProcessInstanceId("workflow"))
                .thenReturn(new MaterialApprovalRoundDO().setMaterialVersionId(99L).setStatus("CANCELLED"));
        error(MATERIAL_STATE_INVALID.getCode(), () -> service.deleteDraft(1L, 4, 9L));
    }
    @Test void refusesCrossTenantOrMissingRecord() {
        TenantContextHolder.setTenantId(2L);
        error(MATERIAL_NOT_EXISTS.getCode(), () -> service.deleteDraft(1L, 4, 9L));
        verify(materialMapper).selectByIdForUpdate(1L, 2L);
    }
    @Test void detectsConditionalDeleteConflict() {
        authorized();
        when(versionMapper.selectByIdForUpdate(3L, 1L)).thenReturn(draft);
        assertEquals(MATERIAL_VERSION_CONFLICT.getCode(),
                assertThrows(ServiceException.class, () -> service.deleteDraft(1L, 4, 9L)).getCode());
    }
    @Test void advertisesDeleteOnlyToConfiguredOwnerWithDraftState() {
        when(versionMapper.selectById(3L)).thenReturn(draft);
        assertFalse(service.availableActions(material, draft, 9L, false).contains("DELETE_DRAFT"));
        when(permissionApi.hasAnyPermissions(9L, "zsjos:material:delete-draft")).thenReturn(true);
        assertTrue(service.availableActions(material, draft, 9L, false).contains("DELETE_DRAFT"));
        assertFalse(service.availableActions(material, draft, 20L, true).contains("DELETE_DRAFT"));
        material.setCurrentEffectiveVersionId(2L);
        assertFalse(service.availableActions(material, draft, 9L, false).contains("DELETE_DRAFT"));
    }
    @Test void declaresBothFeatureAndObjectPermissionBoundaries() throws Exception {
        assertEquals("@ss.hasPermission('zsjos:material:delete-draft')", MaterialController.class
                .getMethod("deleteDraft", Long.class, Integer.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("delete-draft", MaterialService.class.getMethod("deleteDraft", Long.class, Integer.class, Long.class)
                .getAnnotation(ZsjosPermission.class).action());
    }
}
