package cn.iocoder.yudao.module.eam.service.employeeasset;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo.*;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamReturnInspectReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.employee.EamEmployeeAssetTaskDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.employee.EamEmployeeAssetTaskItemDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockBalanceDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockHoldingDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.employee.EamEmployeeAssetTaskItemMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.employee.EamEmployeeAssetTaskMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockBalanceMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockHoldingMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamCustodyModeEnum;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.stock.EamStockService;
import cn.iocoder.yudao.module.eam.service.procurement.EamDemandService;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEvent;
import cn.iocoder.yudao.module.hrm.api.employee.event.HrmEmployeeLifecycleEventType;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.*;

@Service
public class EamEmployeeAssetServiceImpl implements EamEmployeeAssetService {

    @Resource private EamStockHoldingMapper holdingMapper;
    @Resource private EamEmployeeAssetTaskMapper taskMapper;
    @Resource private EamEmployeeAssetTaskItemMapper taskItemMapper;
    @Resource private EamAssetMapper assetMapper;
    @Resource private EamStockBalanceMapper balanceMapper;
    @Resource private EamAssetService assetService;
    @Resource private EamStockService stockService;
    @Resource private HrmEmployeeApi employeeApi;
    @Resource private EamDemandService demandService;
    @Resource private EamApprovalService approvalService;

    @Override
    public EamEmployeeAssetSummaryRespVO getByEmployeeId(Long employeeId) {
        HrmEmployeeRespDTO employee = employeeApi.getEmployee(employeeId);
        if (employee == null) throw exception(EMPLOYEE_ASSET_TASK_NOT_EXISTS);
        return buildSummary(employee.getId());
    }

    @Override
    public EamEmployeeAssetSummaryRespVO getByUserId(Long userId) {
        HrmEmployeeRespDTO employee = employeeApi.getEmployeeByUserId(userId);
        if (employee == null) throw exception(EMPLOYEE_NOT_BOUND);
        return buildSummary(employee.getId());
    }

    @Override
    public Long createHolding(Long employeeId, Long assetId, Long stockBalanceId,
                              String name, String unit, Integer quantity, Integer custodyMode) {
        EamStockHoldingDO holding = new EamStockHoldingDO();
        holding.setEmployeeId(employeeId);
        holding.setAssetId(assetId);
        holding.setStockBalanceId(stockBalanceId);
        holding.setNameSnapshot(name);
        holding.setQuantity(quantity);
        holding.setCustodyMode(custodyMode);
        holding.setStatus(HOLDING_PENDING_SIGN);
        holdingMapper.insert(holding);
        return holding.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long holdingId, Long userId) {
        EamStockHoldingDO holding = validateHolding(holdingId, userId);
        if (!Objects.equals(holding.getStatus(), HOLDING_PENDING_SIGN)) throw exception(HOLDING_STATUS_INVALID);
        holdingMapper.updateById(new EamStockHoldingDO().setId(holdingId)
                .setStatus(HOLDING_ACTIVE).setSignedAt(LocalDateTime.now()));
        if (holding.getAssetId() != null) {
            HrmEmployeeRespDTO employee = employeeApi.getEmployee(holding.getEmployeeId());
            assetService.applyChange(holding.getAssetId(), EamAssetStatusEnum.IN_USE.getStatus(), holding.getEmployeeId(),
                    employee == null ? null : employee.getDeptId(), EamChangeTypeEnum.RECEIVE.getType(), holdingId,
                    "员工签收资产");
        }
    }

    @Override
    public void applyReturn(Long holdingId, Long userId, String remark) {
        EamStockHoldingDO holding = validateHolding(holdingId, userId);
        applyReturn(holding, holdingId, remark);
    }

    private void applyReturnForEmployee(Long holdingId, Long employeeId, String remark) {
        EamStockHoldingDO holding = validateHoldingForEmployee(holdingId, employeeId);
        applyReturn(holding, holdingId, remark);
    }

    private void applyReturn(EamStockHoldingDO holding, Long holdingId, String remark) {
        if (!Objects.equals(holding.getStatus(), HOLDING_ACTIVE)
                || !EamCustodyModeEnum.RETURNABLE.getMode().equals(holding.getCustodyMode())) {
            throw exception(HOLDING_STATUS_INVALID);
        }
        holdingMapper.updateById(new EamStockHoldingDO().setId(holdingId)
                .setStatus(HOLDING_RETURN_PENDING).setReturnAppliedAt(LocalDateTime.now()).setReturnRemark(remark));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inspectReturn(Long holdingId, EamReturnInspectReqVO reqVO) {
        EamStockHoldingDO holding = validateHolding(holdingId, null);
        if (!Objects.equals(holding.getStatus(), HOLDING_RETURN_PENDING)) throw exception(HOLDING_STATUS_INVALID);
        if (reqVO.getResult() < 1 || reqVO.getResult() > 4) throw exception(RETURN_RESULT_INVALID);
        if (reqVO.getResult() == 4) {
            holdingMapper.updateById(new EamStockHoldingDO().setId(holdingId)
                    .setStatus(HOLDING_ACTIVE).setReturnResult(reqVO.getResult()).setReturnRemark(reqVO.getRemark()));
            return;
        }
        int status = reqVO.getResult() == 3 ? HOLDING_LOST : HOLDING_RETURNED;
        holdingMapper.updateById(new EamStockHoldingDO().setId(holdingId).setStatus(status)
                .setReturnResult(reqVO.getResult()).setReturnRemark(reqVO.getRemark())
                .setReturnInspectedAt(LocalDateTime.now()));
        if (holding.getStockBalanceId() != null) {
            if (reqVO.getResult() == 1) {
                stockService.inbound(holding.getStockBalanceId(), holding.getQuantity(),
                        "EMPLOYEE_RETURN", holdingId, reqVO.getRemark());
            } else if (reqVO.getResult() == 2) {
                stockService.inboundFrozen(holding.getStockBalanceId(), holding.getQuantity(),
                        "EMPLOYEE_RETURN_DAMAGED", holdingId, reqVO.getRemark());
            }
        } else if (holding.getAssetId() != null) {
            if (reqVO.getResult() == 3) {
                assetService.applyChange(holding.getAssetId(), EamAssetStatusEnum.LOST.getStatus(), 0L, 0L,
                        EamChangeTypeEnum.LOST.getType(), holdingId, "员工退还验收：缺件或遗失");
            } else {
                assetService.applyChange(holding.getAssetId(), EamAssetStatusEnum.IDLE.getStatus(), 0L, 0L,
                        EamChangeTypeEnum.RETURN.getType(), holdingId, "员工资产退还验收");
                if (reqVO.getResult() == 2) {
                    assetService.freeze(holding.getAssetId(), "员工退还验收损坏，等待后续处理");
                }
            }
        }
        completeReturnTaskItems(holdingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleLifecycleEvent(HrmEmployeeLifecycleEvent event) {
        if (taskMapper.selectByEventKey(event.getEventKey()) != null || event.getEmployeeId() == null) return;
        HrmEmployeeRespDTO employee = event.getAfter() != null ? event.getAfter() : event.getBefore();
        if (employee == null) return;
        if (event.getType() == HrmEmployeeLifecycleEventType.QUIT_CANCELLED) {
            cancelOpenOffboarding(employee, event.getEventKey());
            return;
        }
        Integer taskType = resolveTaskType(event.getType());
        if (taskType == null || (taskType == TASK_PROVISIONING && employee.getUserId() == null)) return;
        EamEmployeeAssetTaskDO openTask = taskMapper.selectOpenByEmployeeIdAndType(employee.getId(), taskType);
        if (openTask != null && event.getType() != HrmEmployeeLifecycleEventType.REHIRED) {
            taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(openTask.getId())
                    .setLatestEventKey(event.getEventKey())
                    .setLeaderUserId(employee.getLeaderUserId()).setDeptIdSnapshot(employee.getDeptId())
                    .setPlannedLeaveTime(employee.getLeaveTime()));
            return;
        }
        EamEmployeeAssetTaskDO task = new EamEmployeeAssetTaskDO();
        task.setEventKey(event.getEventKey());
        task.setLatestEventKey(event.getEventKey());
        task.setType(taskType);
        task.setStatus(STATUS_DRAFT);
        task.setEmployeeId(employee.getId());
        task.setLeaderUserId(employee.getLeaderUserId());
        task.setEmployeeNameSnapshot(employee.getName());
        task.setDeptIdSnapshot(employee.getDeptId());
        task.setPlannedLeaveTime(employee.getLeaveTime());
        taskMapper.insert(task);
        if (taskType != TASK_PROVISIONING) snapshotTaskItems(task, employee.getId());
    }

    @Override
    public EamEmployeeAssetTaskRespVO getTask(Long taskId) {
        EamEmployeeAssetTaskDO task = taskMapper.selectById(taskId);
        if (task == null) throw exception(EMPLOYEE_ASSET_TASK_NOT_EXISTS);
        EamEmployeeAssetTaskRespVO result = BeanUtils.toBean(task, EamEmployeeAssetTaskRespVO.class);
        result.setItems(BeanUtils.toBean(taskItemMapper.selectListByTaskId(taskId),
                EamEmployeeAssetTaskItemRespVO.class));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitProvisioning(Long taskId, EamEmployeeAssetTaskSubmitReqVO reqVO, Long applicantUserId) {
        EamEmployeeAssetTaskDO task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) throw exception(EMPLOYEE_ASSET_TASK_NOT_EXISTS);
        if (!Objects.equals(task.getType(), TASK_PROVISIONING) || !Objects.equals(task.getStatus(), STATUS_DRAFT)
                || reqVO.getDemand() == null) throw exception(EMPLOYEE_ASSET_TASK_STATUS_INVALID);
        Long demandId = demandService.createTaskDemand(reqVO.getDemand(), task.getEmployeeId(), applicantUserId,
                EMPLOYEE_REVIEW_PROCESS_KEY, taskId);
        String processId = demandService.getDemand(demandId).getProcessInstanceId();
        taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(taskId).setDemandId(demandId)
                .setProcessInstanceId(processId).setStatus(STATUS_APPROVING).setRemark(reqVO.getRemark()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long taskId, EamEmployeeAssetTaskActionReqVO reqVO) {
        EamEmployeeAssetTaskDO task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) throw exception(EMPLOYEE_ASSET_TASK_NOT_EXISTS);
        if (Objects.equals(task.getType(), TASK_PROVISIONING) || !Objects.equals(task.getStatus(), STATUS_DRAFT)) {
            throw exception(EMPLOYEE_ASSET_TASK_STATUS_INVALID);
        }
        List<EamEmployeeAssetTaskItemDO> items = taskItemMapper.selectListByTaskId(taskId);
        Map<Long, EamEmployeeAssetTaskActionReqVO.Item> requested = reqVO.getItems().stream()
                .collect(Collectors.toMap(EamEmployeeAssetTaskActionReqVO.Item::getId, Function.identity()));
        if (requested.size() != items.size() || items.stream().anyMatch(item -> !requested.containsKey(item.getId()))) {
            throw exception(EMPLOYEE_ASSET_TASK_STATUS_INVALID);
        }
        for (EamEmployeeAssetTaskItemDO item : items) {
            EamEmployeeAssetTaskActionReqVO.Item input = requested.get(item.getId());
            if (!Set.of(TASK_ACTION_FOLLOW, TASK_ACTION_RETURN, TASK_ACTION_TRANSFER, TASK_ACTION_NO_CHANGE)
                    .contains(input.getAction())
                    || Objects.equals(input.getAction(), TASK_ACTION_TRANSFER) && input.getTransferToEmployeeId() == null) {
                throw exception(EMPLOYEE_ASSET_TASK_STATUS_INVALID);
            }
            taskItemMapper.updateById(new EamEmployeeAssetTaskItemDO().setId(item.getId())
                    .setAction(input.getAction()).setTransferToEmployeeId(input.getTransferToEmployeeId())
                    .setRemark(input.getRemark()));
        }
        String processId = approvalService.start(EMPLOYEE_REVIEW_PROCESS_KEY, String.valueOf(taskId),
                "员工资产复核 " + task.getEmployeeNameSnapshot());
        taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(taskId).setProcessInstanceId(processId)
                .setStatus(STATUS_APPROVING).setRemark(reqVO.getRemark()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReviewProcessResult(Long taskId, Integer bpmStatus, String reason) {
        EamEmployeeAssetTaskDO task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) throw exception(EMPLOYEE_ASSET_TASK_NOT_EXISTS);
        if (!Objects.equals(task.getStatus(), STATUS_APPROVING)) return;
        if (cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(bpmStatus)) {
            if (Objects.equals(task.getType(), TASK_PROVISIONING)) {
                demandService.handleProcessResult(task.getDemandId(), bpmStatus, reason);
                taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(taskId).setStatus(STATUS_APPROVED));
            } else {
                boolean awaitingInspection = applyTaskActions(task);
                taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(taskId)
                        .setStatus(awaitingInspection ? STATUS_FULFILLING : STATUS_COMPLETED));
            }
        } else if (cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(bpmStatus)) {
            if (task.getDemandId() != null) demandService.handleProcessResult(task.getDemandId(), bpmStatus, reason);
            taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(taskId).setStatus(STATUS_REJECTED).setRemark(reason));
        } else if (cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(bpmStatus)) {
            if (task.getDemandId() != null) demandService.handleProcessResult(task.getDemandId(), bpmStatus, reason);
            taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(taskId).setStatus(STATUS_CANCELLED).setRemark(reason));
        }
    }

    private boolean applyTaskActions(EamEmployeeAssetTaskDO task) {
        boolean awaitingInspection = false;
        for (EamEmployeeAssetTaskItemDO item : taskItemMapper.selectListByTaskId(task.getId())) {
            EamStockHoldingDO holding = item.getHoldingId() == null ? null : holdingMapper.selectById(item.getHoldingId());
            if (item.getHoldingId() != null && holding == null) throw exception(HOLDING_NOT_EXISTS);
            Long assetId = item.getAssetId() != null ? item.getAssetId()
                    : holding == null ? null : holding.getAssetId();
            if (Objects.equals(item.getAction(), TASK_ACTION_RETURN)) {
                if (holding != null) {
                    applyReturnForEmployee(holding.getId(), task.getEmployeeId(), item.getRemark());
                    awaitingInspection = true;
                } else if (assetId != null) {
                    assetService.applyChange(assetId, EamAssetStatusEnum.IDLE.getStatus(), 0L, 0L,
                            EamChangeTypeEnum.RETURN.getType(), task.getId(), "员工异动资产退回");
                }
            } else if (Objects.equals(item.getAction(), TASK_ACTION_TRANSFER)) {
                HrmEmployeeRespDTO target = employeeApi.getEmployee(item.getTransferToEmployeeId());
                if (target == null) throw exception(EMPLOYEE_NOT_BOUND);
                if (holding != null) {
                    holdingMapper.updateById(new EamStockHoldingDO().setId(holding.getId())
                            .setEmployeeId(target.getId())
                            .setStatus(HOLDING_PENDING_SIGN).setSignedAt(null));
                }
                if (assetId != null) {
                    assetService.applyChange(assetId, EamAssetStatusEnum.IN_USE.getStatus(),
                            target.getId(), target.getDeptId(), EamChangeTypeEnum.TRANSFER.getType(),
                            task.getId(), "员工异动资产转交");
                }
            } else if (Objects.equals(item.getAction(), TASK_ACTION_FOLLOW) && assetId != null) {
                assetService.applyChange(assetId, EamAssetStatusEnum.IN_USE.getStatus(), task.getEmployeeId(),
                        task.getDeptIdSnapshot(), EamChangeTypeEnum.TRANSFER.getType(), task.getId(),
                        "员工异动资产随人");
            }
            taskItemMapper.updateById(new EamEmployeeAssetTaskItemDO().setId(item.getId())
                    .setStatus(Objects.equals(item.getAction(), TASK_ACTION_RETURN) && holding != null
                            ? STATUS_FULFILLING : STATUS_COMPLETED));
        }
        return awaitingInspection;
    }

    private void completeReturnTaskItems(Long holdingId) {
        Set<Long> taskIds = taskItemMapper.selectListByHoldingId(holdingId).stream()
                .filter(item -> Objects.equals(item.getAction(), TASK_ACTION_RETURN)
                        && Objects.equals(item.getStatus(), STATUS_FULFILLING))
                .peek(item -> taskItemMapper.updateById(new EamEmployeeAssetTaskItemDO().setId(item.getId())
                        .setStatus(STATUS_COMPLETED)))
                .map(EamEmployeeAssetTaskItemDO::getTaskId)
                .collect(Collectors.toSet());
        for (Long taskId : taskIds) {
            EamEmployeeAssetTaskDO task = taskMapper.selectByIdForUpdate(taskId);
            if (task != null && Objects.equals(task.getStatus(), STATUS_FULFILLING)
                    && taskItemMapper.selectListByTaskId(taskId).stream()
                    .allMatch(item -> Objects.equals(item.getStatus(), STATUS_COMPLETED))) {
                taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(taskId).setStatus(STATUS_COMPLETED));
            }
        }
    }

    private void snapshotTaskItems(EamEmployeeAssetTaskDO task, Long employeeId) {
        if (employeeId == null) return;
        List<EamStockHoldingDO> holdings = holdingMapper.selectListByEmployeeId(employeeId);
        Set<Long> heldAssetIds = holdings.stream().map(EamStockHoldingDO::getAssetId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (EamAssetDO asset : assetMapper.selectListByUseEmployeeId(employeeId)) {
            if (heldAssetIds.contains(asset.getId())) continue;
            EamEmployeeAssetTaskItemDO item = new EamEmployeeAssetTaskItemDO();
            item.setTaskId(task.getId()); item.setAssetId(asset.getId());
            item.setAssetNameSnapshot(asset.getName()); item.setStatus(STATUS_DRAFT);
            taskItemMapper.insert(item);
        }
        for (EamStockHoldingDO holding : holdings) {
            if (Objects.equals(holding.getStatus(), HOLDING_RETURNED)) continue;
            EamEmployeeAssetTaskItemDO item = new EamEmployeeAssetTaskItemDO();
            item.setTaskId(task.getId()); item.setHoldingId(holding.getId());
            item.setAssetNameSnapshot(holding.getNameSnapshot()); item.setStatus(STATUS_DRAFT);
            taskItemMapper.insert(item);
        }
    }

    private void cancelOpenOffboarding(HrmEmployeeRespDTO employee, String eventKey) {
        EamEmployeeAssetTaskDO open = taskMapper.selectOpenByEmployeeIdAndType(employee.getId(), TASK_OFFBOARDING);
        if (open != null) {
            if (Objects.equals(open.getStatus(), STATUS_APPROVING)
                    && open.getProcessInstanceId() != null && !open.getProcessInstanceId().isBlank()) {
                approvalService.terminate(open.getProcessInstanceId(), "HRM 已取消员工离职");
            }
            taskMapper.updateById(new EamEmployeeAssetTaskDO().setId(open.getId()).setStatus(TASK_CANCELLED));
        }
        EamEmployeeAssetTaskDO audit = new EamEmployeeAssetTaskDO();
        audit.setEventKey(eventKey); audit.setLatestEventKey(eventKey); audit.setType(TASK_CANCELLED);
        audit.setStatus(TASK_CANCELLED); audit.setEmployeeId(employee.getId());
        audit.setLeaderUserId(employee.getLeaderUserId()); audit.setEmployeeNameSnapshot(employee.getName());
        audit.setDeptIdSnapshot(employee.getDeptId()); audit.setPlannedLeaveTime(employee.getLeaveTime());
        taskMapper.insert(audit);
    }

    private Integer resolveTaskType(HrmEmployeeLifecycleEventType type) {
        return switch (type) {
            case ACCOUNT_BOUND, ENTRY_CONFIRMED, REHIRED -> TASK_PROVISIONING;
            case CHANGE_EFFECTIVE -> TASK_CHANGE_REVIEW;
            case QUIT_PLANNED, LEFT -> TASK_OFFBOARDING;
            default -> null;
        };
    }

    private EamEmployeeAssetSummaryRespVO buildSummary(Long employeeId) {
        List<EamEmployeeAssetItemRespVO> items = new ArrayList<>();
        List<EamStockHoldingDO> holdings = holdingMapper.selectListByEmployeeId(employeeId);
        Set<Long> heldAssetIds = holdings.stream().map(EamStockHoldingDO::getAssetId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (EamAssetDO asset : assetMapper.selectListByUseEmployeeId(employeeId)) {
                if (heldAssetIds.contains(asset.getId())) continue;
                EamEmployeeAssetItemRespVO item = new EamEmployeeAssetItemRespVO();
                item.setItemType("SERIALIZED_ASSET");
                item.setAssetId(asset.getId()); item.setAssetCode(asset.getAssetCode()); item.setName(asset.getName());
                item.setQuantity(asset.getQuantity()); item.setUnit(asset.getUnit()); item.setStatus(asset.getStatus());
                items.add(item);
        }
        for (EamStockHoldingDO holding : holdings) {
            EamEmployeeAssetItemRespVO item = BeanUtils.toBean(holding, EamEmployeeAssetItemRespVO.class);
            item.setItemType(holding.getAssetId() == null ? "BATCH_HOLDING" : "SERIALIZED_HOLDING");
            item.setHoldingId(holding.getId()); item.setName(holding.getNameSnapshot());
            if (holding.getAssetId() != null) {
                EamAssetDO asset = assetMapper.selectById(holding.getAssetId());
                if (asset != null) {
                    item.setAssetCode(asset.getAssetCode());
                    item.setUnit(asset.getUnit());
                }
            } else if (holding.getStockBalanceId() != null) {
                EamStockBalanceDO balance = balanceMapper.selectById(holding.getStockBalanceId());
                if (balance != null) item.setUnit(balance.getUnit());
            }
            items.add(item);
        }
        List<EamEmployeeAssetTaskRespVO> tasks = taskMapper.selectListByEmployeeId(employeeId).stream()
                .map(task -> getTask(task.getId())).toList();
        EamEmployeeAssetSummaryRespVO result = new EamEmployeeAssetSummaryRespVO();
        result.setEmployeeId(employeeId); result.setItems(items); result.setTasks(tasks);
        result.setPendingSignCount((int) items.stream().filter(i -> i.getItemType().endsWith("HOLDING")
                && Objects.equals(i.getStatus(), HOLDING_PENDING_SIGN)).count());
        result.setPendingReturnCount((int) items.stream().filter(i -> i.getItemType().endsWith("HOLDING")
                && Objects.equals(i.getStatus(), HOLDING_RETURN_PENDING)).count());
        result.setOffboardingUncleared(tasks.stream().anyMatch(t -> Objects.equals(t.getType(), TASK_OFFBOARDING)
                && !Objects.equals(t.getStatus(), STATUS_COMPLETED) && !Objects.equals(t.getStatus(), TASK_CANCELLED)));
        return result;
    }

    private EamStockHoldingDO validateHolding(Long id, Long userId) {
        EamStockHoldingDO holding = holdingMapper.selectById(id);
        if (holding == null) throw exception(HOLDING_NOT_EXISTS);
        if (userId != null) {
            HrmEmployeeRespDTO employee = employeeApi.getEmployeeByUserId(userId);
            if (employee == null || !Objects.equals(holding.getEmployeeId(), employee.getId())) {
                throw exception(HOLDING_NOT_EXISTS);
            }
        }
        return holding;
    }

    private EamStockHoldingDO validateHoldingForEmployee(Long id, Long employeeId) {
        EamStockHoldingDO holding = holdingMapper.selectById(id);
        if (holding == null || !Objects.equals(holding.getEmployeeId(), employeeId)) {
            throw exception(HOLDING_NOT_EXISTS);
        }
        return holding;
    }
}

@Component
class EamEmployeeLifecycleEventListener implements ApplicationListener<HrmEmployeeLifecycleEvent> {
    @Resource private EamEmployeeAssetService service;
    @Override public void onApplicationEvent(HrmEmployeeLifecycleEvent event) { service.handleLifecycleEvent(event); }
}
