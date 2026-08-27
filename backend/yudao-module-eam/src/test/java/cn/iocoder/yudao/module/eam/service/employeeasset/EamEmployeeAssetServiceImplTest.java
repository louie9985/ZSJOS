package cn.iocoder.yudao.module.eam.service.employeeasset;

import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamReturnInspectReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.employee.EamEmployeeAssetTaskDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.employee.EamEmployeeAssetTaskItemDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockHoldingDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.employee.EamEmployeeAssetTaskItemMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.employee.EamEmployeeAssetTaskMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockBalanceMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockHoldingMapper;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamCustodyModeEnum;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.procurement.EamDemandService;
import cn.iocoder.yudao.module.eam.service.stock.EamStockService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEvent;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE;

@ExtendWith(MockitoExtension.class)
class EamEmployeeAssetServiceImplTest {

    @InjectMocks
    private EamEmployeeAssetServiceImpl service;
    @Mock
    private EamStockHoldingMapper holdingMapper;
    @Mock
    private EamEmployeeAssetTaskMapper taskMapper;
    @Mock
    private EamEmployeeAssetTaskItemMapper taskItemMapper;
    @Mock
    private EamAssetMapper assetMapper;
    @Mock
    private EamStockBalanceMapper balanceMapper;
    @Mock
    private EamAssetService assetService;
    @Mock
    private EamStockService stockService;
    @Mock
    private HrmEmployeeApi employeeApi;
    @Mock
    private EamDemandService demandService;
    @Mock
    private EamApprovalService approvalService;

    @Test
    void formalLeave_shouldUpdateOpenPlannedLeaveTaskInsteadOfCreatingAnotherTask() {
        HrmEmployeeRespDTO employee = employee();
        EamEmployeeAssetTaskDO open = new EamEmployeeAssetTaskDO();
        open.setId(100L);
        open.setType(TASK_OFFBOARDING);
        open.setStatus(STATUS_DRAFT);
        when(taskMapper.selectOpenByEmployeeIdAndType(1L, TASK_OFFBOARDING)).thenReturn(open);

        service.handleLifecycleEvent(event(HrmEmployeeLifecycleEventType.LEFT, "hrm:left:20:1", employee));

        ArgumentCaptor<EamEmployeeAssetTaskDO> captor = ArgumentCaptor.forClass(EamEmployeeAssetTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(100L, captor.getValue().getId());
        assertEquals("hrm:left:20:1", captor.getValue().getLatestEventKey());
        verify(taskMapper, never()).insert(any(EamEmployeeAssetTaskDO.class));
    }

    @Test
    void rehire_shouldCreateNewProvisioningTaskEvenWhenHistoricalTaskIsStillOpen() {
        HrmEmployeeRespDTO employee = employee();
        when(taskMapper.selectOpenByEmployeeIdAndType(1L, TASK_PROVISIONING))
                .thenReturn(new EamEmployeeAssetTaskDO().setId(99L));

        service.handleLifecycleEvent(event(HrmEmployeeLifecycleEventType.REHIRED, "hrm:rehired:30:1", employee));

        ArgumentCaptor<EamEmployeeAssetTaskDO> captor = ArgumentCaptor.forClass(EamEmployeeAssetTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(TASK_PROVISIONING, captor.getValue().getType());
        assertEquals("hrm:rehired:30:1", captor.getValue().getEventKey());
    }

    @Test
    void cancelLeave_shouldCancelOpenTaskAndPersistIdempotentAuditSnapshot() {
        HrmEmployeeRespDTO employee = employee();
        EamEmployeeAssetTaskDO open = new EamEmployeeAssetTaskDO().setId(100L).setStatus(STATUS_DRAFT);
        when(taskMapper.selectOpenByEmployeeIdAndType(1L, TASK_OFFBOARDING)).thenReturn(open);

        service.handleLifecycleEvent(event(HrmEmployeeLifecycleEventType.QUIT_CANCELLED,
                "hrm:quit_cancelled:20:1", employee));

        ArgumentCaptor<EamEmployeeAssetTaskDO> captor = ArgumentCaptor.forClass(EamEmployeeAssetTaskDO.class);
        verify(taskMapper).updateById(any(EamEmployeeAssetTaskDO.class));
        verify(taskMapper).insert(captor.capture());
        assertEquals("员工甲", captor.getValue().getEmployeeNameSnapshot());
        assertEquals("hrm:quit_cancelled:20:1", captor.getValue().getLatestEventKey());
    }

    @Test
    void inspectDamagedBatchReturn_shouldIncreaseFrozenStockInsteadOfAvailableStock() {
        EamStockHoldingDO holding = new EamStockHoldingDO();
        holding.setId(80L);
        holding.setStockBalanceId(81L);
        holding.setQuantity(2);
        holding.setStatus(HOLDING_RETURN_PENDING);
        when(holdingMapper.selectById(80L)).thenReturn(holding);
        EamReturnInspectReqVO request = new EamReturnInspectReqVO();
        request.setResult(2);
        request.setRemark("外壳破损");

        service.inspectReturn(80L, request);

        verify(stockService).inboundFrozen(81L, 2, "EMPLOYEE_RETURN_DAMAGED", 80L, "外壳破损");
        verify(stockService, never()).inbound(anyLong(), anyInt(), anyString(), anyLong(), any());
    }

    @Test
    void cancelLeave_shouldTerminateRunningReviewProcess() {
        HrmEmployeeRespDTO employee = employee();
        EamEmployeeAssetTaskDO open = new EamEmployeeAssetTaskDO().setId(100L)
                .setStatus(STATUS_APPROVING).setProcessInstanceId("process-100");
        when(taskMapper.selectOpenByEmployeeIdAndType(1L, TASK_OFFBOARDING)).thenReturn(open);

        service.handleLifecycleEvent(event(HrmEmployeeLifecycleEventType.QUIT_CANCELLED,
                "hrm:quit_cancelled:20:2", employee));

        verify(approvalService).terminate("process-100", "HRM 已取消员工离职");
    }

    @Test
    void approvedTransfer_shouldMoveSerializedHoldingAndRequireNewSignature() {
        EamEmployeeAssetTaskDO task = new EamEmployeeAssetTaskDO().setId(100L)
                .setType(TASK_CHANGE_REVIEW).setStatus(STATUS_APPROVING).setEmployeeId(1L);
        EamEmployeeAssetTaskItemDO item = new EamEmployeeAssetTaskItemDO();
        item.setId(101L);
        item.setHoldingId(80L);
        item.setAction(TASK_ACTION_TRANSFER);
        item.setTransferToEmployeeId(21L);
        EamStockHoldingDO holding = new EamStockHoldingDO();
        holding.setId(80L);
        holding.setAssetId(81L);
        holding.setStatus(HOLDING_ACTIVE);
        HrmEmployeeRespDTO target = new HrmEmployeeRespDTO();
        target.setId(21L);
        target.setUserId(22L);
        target.setDeptId(23L);
        when(taskMapper.selectByIdForUpdate(100L)).thenReturn(task);
        when(taskItemMapper.selectListByTaskId(100L)).thenReturn(java.util.List.of(item));
        when(holdingMapper.selectById(80L)).thenReturn(holding);
        when(employeeApi.getEmployee(21L)).thenReturn(target);

        service.handleReviewProcessResult(100L, APPROVE.getStatus(), null);

        ArgumentCaptor<EamStockHoldingDO> captor = ArgumentCaptor.forClass(EamStockHoldingDO.class);
        verify(holdingMapper).updateById(captor.capture());
        assertEquals(21L, captor.getValue().getEmployeeId());
        assertEquals(HOLDING_PENDING_SIGN, captor.getValue().getStatus());
        verify(assetService).applyChange(eq(81L), eq(EamAssetStatusEnum.IN_USE.getStatus()), eq(21L), eq(23L),
                eq(cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum.TRANSFER.getType()),
                eq(100L), anyString());
    }

    @Test
    void approvedOffboardingReturn_shouldRemainFulfillingUntilInspection() {
        EamEmployeeAssetTaskDO task = new EamEmployeeAssetTaskDO().setId(100L)
                .setType(TASK_OFFBOARDING).setStatus(STATUS_APPROVING).setEmployeeId(1L);
        EamEmployeeAssetTaskItemDO item = new EamEmployeeAssetTaskItemDO();
        item.setId(101L);
        item.setTaskId(100L);
        item.setHoldingId(80L);
        item.setAction(TASK_ACTION_RETURN);
        item.setStatus(STATUS_DRAFT);
        EamStockHoldingDO holding = new EamStockHoldingDO();
        holding.setId(80L);
        holding.setEmployeeId(1L);
        holding.setCustodyMode(EamCustodyModeEnum.RETURNABLE.getMode());
        holding.setStatus(HOLDING_ACTIVE);
        when(taskMapper.selectByIdForUpdate(100L)).thenReturn(task);
        when(taskItemMapper.selectListByTaskId(100L)).thenReturn(java.util.List.of(item));
        when(holdingMapper.selectById(80L)).thenReturn(holding);

        service.handleReviewProcessResult(100L, APPROVE.getStatus(), null);

        verify(taskItemMapper).updateById(argThat((EamEmployeeAssetTaskItemDO update) ->
                Objects.equals(update.getStatus(), STATUS_FULFILLING)));
        verify(taskMapper).updateById(argThat((EamEmployeeAssetTaskDO update) ->
                Objects.equals(update.getStatus(), STATUS_FULFILLING)));
        verify(holdingMapper).updateById(argThat((EamStockHoldingDO update) ->
                Objects.equals(update.getStatus(), HOLDING_RETURN_PENDING)));
    }

    @Test
    void successfulReturnInspection_shouldCompleteOffboardingTask() {
        EamStockHoldingDO holding = new EamStockHoldingDO();
        holding.setId(80L);
        holding.setStockBalanceId(81L);
        holding.setQuantity(1);
        holding.setStatus(HOLDING_RETURN_PENDING);
        EamEmployeeAssetTaskItemDO fulfillingItem = new EamEmployeeAssetTaskItemDO();
        fulfillingItem.setId(101L);
        fulfillingItem.setTaskId(100L);
        fulfillingItem.setHoldingId(80L);
        fulfillingItem.setAction(TASK_ACTION_RETURN);
        fulfillingItem.setStatus(STATUS_FULFILLING);
        EamEmployeeAssetTaskItemDO completedItem = new EamEmployeeAssetTaskItemDO();
        completedItem.setId(101L);
        completedItem.setTaskId(100L);
        completedItem.setStatus(STATUS_COMPLETED);
        EamEmployeeAssetTaskDO task = new EamEmployeeAssetTaskDO().setId(100L)
                .setType(TASK_OFFBOARDING).setStatus(STATUS_FULFILLING);
        when(holdingMapper.selectById(80L)).thenReturn(holding);
        when(taskItemMapper.selectListByHoldingId(80L)).thenReturn(java.util.List.of(fulfillingItem));
        when(taskMapper.selectByIdForUpdate(100L)).thenReturn(task);
        when(taskItemMapper.selectListByTaskId(100L)).thenReturn(java.util.List.of(completedItem));
        EamReturnInspectReqVO request = new EamReturnInspectReqVO();
        request.setResult(1);

        service.inspectReturn(80L, request);

        verify(taskMapper).updateById(argThat((EamEmployeeAssetTaskDO update) ->
                Objects.equals(update.getStatus(), STATUS_COMPLETED)));
    }

    @Test
    void employeeSummary_shouldEnrichSerializedHoldingWithAssetCodeAndUnit() {
        HrmEmployeeRespDTO employee = employee();
        EamStockHoldingDO holding = new EamStockHoldingDO();
        holding.setId(80L);
        holding.setEmployeeId(1L);
        holding.setAssetId(81L);
        holding.setNameSnapshot("笔记本电脑");
        holding.setQuantity(1);
        holding.setStatus(HOLDING_ACTIVE);
        EamAssetDO asset = new EamAssetDO();
        asset.setId(81L);
        asset.setAssetCode("EAM-0001");
        asset.setUnit("台");
        when(employeeApi.getEmployee(1L)).thenReturn(employee);
        when(holdingMapper.selectListByEmployeeId(1L)).thenReturn(java.util.List.of(holding));
        when(assetMapper.selectListByUseEmployeeId(1L)).thenReturn(java.util.List.of());
        when(assetMapper.selectById(81L)).thenReturn(asset);
        when(taskMapper.selectListByEmployeeId(1L)).thenReturn(java.util.List.of());

        var summary = service.getByEmployeeId(1L);

        assertEquals("EAM-0001", summary.getItems().getFirst().getAssetCode());
        assertEquals("台", summary.getItems().getFirst().getUnit());
        assertEquals("笔记本电脑", summary.getItems().getFirst().getName());
    }

    @Test
    void inspectRejectedReturn_shouldRestoreHoldingWithoutChangingStock() {
        EamStockHoldingDO holding = new EamStockHoldingDO();
        holding.setId(80L);
        holding.setStockBalanceId(81L);
        holding.setQuantity(2);
        holding.setStatus(HOLDING_RETURN_PENDING);
        when(holdingMapper.selectById(80L)).thenReturn(holding);
        EamReturnInspectReqVO request = new EamReturnInspectReqVO();
        request.setResult(4);
        request.setRemark("实物不符");

        service.inspectReturn(80L, request);

        verify(stockService, never()).inbound(anyLong(), anyInt(), anyString(), anyLong(), any());
        verify(stockService, never()).inboundFrozen(anyLong(), anyInt(), anyString(), anyLong(), any());
        verify(holdingMapper).updateById(argThat((EamStockHoldingDO update) ->
                Objects.equals(update.getStatus(), HOLDING_ACTIVE)));
    }

    private HrmEmployeeLifecycleEvent event(HrmEmployeeLifecycleEventType type, String key,
                                            HrmEmployeeRespDTO employee) {
        return new HrmEmployeeLifecycleEvent(this, 1L, key, type, 20L, employee, employee);
    }

    private HrmEmployeeRespDTO employee() {
        HrmEmployeeRespDTO employee = new HrmEmployeeRespDTO();
        employee.setId(1L);
        employee.setUserId(2L);
        employee.setName("员工甲");
        employee.setDeptId(3L);
        employee.setLeaderUserId(4L);
        return employee;
    }
}
