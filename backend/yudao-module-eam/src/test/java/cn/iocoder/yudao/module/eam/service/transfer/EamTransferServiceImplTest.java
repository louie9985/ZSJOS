package cn.iocoder.yudao.module.eam.service.transfer;

import cn.iocoder.yudao.module.eam.controller.admin.transfer.vo.EamTransferInspectReqVO;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.transfer.EamTransferMapper;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE;
import static cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum.IN_USE;
import static cn.iocoder.yudao.module.eam.enums.transfer.EamTransferStatusEnum.*;
import static cn.iocoder.yudao.module.eam.enums.transfer.EamTransferTypeEnum.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EamTransferServiceImplTest {
    @InjectMocks private EamTransferServiceImpl service;
    @Mock private EamTransferMapper transferMapper;
    @Mock private EamAssetMapper assetMapper;
    @Mock private EamAssetService assetService;
    @Mock private EamApprovalService approvalService;
    @Mock private HrmEmployeeApi employeeApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private PermissionApi permissionApi;

    @Test
    void duplicateApprovedEventShouldNotApplyAssetChangeAgain() {
        when(transferMapper.selectByIdForUpdate(10L))
                .thenReturn(new EamTransferDO().setId(10L).setStatus(APPROVED.getStatus()));

        service.handleProcessResult(10L, APPROVE.getStatus(), null);

        verifyNoInteractions(assetMapper, assetService);
        verify(transferMapper, never()).updateById(any(EamTransferDO.class));
    }

    @Test
    void damagedReturnInspectionShouldReturnAndFreezeAsset() {
        EamTransferDO transfer = new EamTransferDO().setId(10L).setType(RETURN.getType())
                .setAssetId(20L).setNo("TR2-1").setStatus(PENDING_INSPECTION.getStatus());
        EamAssetDO asset = new EamAssetDO().setId(20L).setStatus(IN_USE.getStatus());
        when(transferMapper.selectByIdForUpdate(10L)).thenReturn(transfer);
        when(assetMapper.selectByIdForUpdate(20L)).thenReturn(asset);
        when(permissionApi.getEnabledUserIdsByPermission("eam:transfer:inspect")).thenReturn(java.util.Set.of(30L));
        when(permissionApi.getDeptDataPermission(30L)).thenReturn(new DeptDataPermissionRespDTO().setAll(true));
        EamTransferInspectReqVO request = new EamTransferInspectReqVO();
        request.setResult(2);
        request.setRemark("外壳损坏");

        service.inspectTransfer(10L, request, 30L);

        verify(assetService).validateStatusTransition(eq(asset), anySet());
        verify(assetService).applyChange(eq(20L), anyInt(), eq(0L), eq(0L), anyInt(), eq(10L), anyString());
        verify(assetService).freeze(20L, "资产归还验收：损坏");
        ArgumentCaptor<EamTransferDO> update = ArgumentCaptor.forClass(EamTransferDO.class);
        verify(transferMapper).updateById(update.capture());
        assertEquals(EXCEPTION.getStatus(), update.getValue().getStatus());
        assertEquals(2, update.getValue().getInspectionResult());
        assertEquals(30L, update.getValue().getInspectedByUserId());
        assertEquals(LocalDate.now(), update.getValue().getActualReturnDate());
    }

    @Test
    void inspectionOutsideDataScopeShouldBeRejected() {
        EamTransferDO transfer = new EamTransferDO().setId(10L).setType(RETURN.getType())
                .setAssetId(20L).setFromDeptId(100L).setStatus(PENDING_INSPECTION.getStatus());
        when(transferMapper.selectByIdForUpdate(10L)).thenReturn(transfer);
        when(permissionApi.getEnabledUserIdsByPermission("eam:transfer:inspect")).thenReturn(java.util.Set.of(30L));
        when(permissionApi.getDeptDataPermission(30L)).thenReturn(new DeptDataPermissionRespDTO());

        EamTransferInspectReqVO request = new EamTransferInspectReqVO();
        request.setResult(1);
        assertThrows(RuntimeException.class, () -> service.inspectTransfer(10L, request, 30L));
        verifyNoInteractions(assetMapper, assetService);
    }
}
