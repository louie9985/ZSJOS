package cn.iocoder.yudao.module.eam.service.procurement;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.*;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandItemDO;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamDemandItemMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamDemandMapper;
import cn.iocoder.yudao.module.eam.enums.category.EamCustodyModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamDeliveryModeEnum;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryPolicy;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.stock.EamStockService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.hutool.core.util.StrUtil.format;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.*;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.*;

@Service
public class EamDemandServiceImpl implements EamDemandService {

    @Resource private EamDemandMapper demandMapper;
    @Resource private EamDemandItemMapper itemMapper;
    @Resource private EamCategoryService categoryService;
    @Resource private EamCategoryFieldService fieldService;
    @Resource private EamStockService stockService;
    @Resource private EamApprovalService approvalService;
    @Resource private HrmEmployeeApi employeeApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDemand(EamDemandCreateReqVO reqVO, Long applicantUserId) {
        HrmEmployeeRespDTO employee = reqVO.getEmployeeId() == null
                ? employeeApi.getEmployeeByUserId(applicantUserId) : employeeApi.getEmployee(reqVO.getEmployeeId());
        return createDemand(reqVO, employee, applicantUserId, DEMAND_PROCESS_KEY, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTaskDemand(EamDemandCreateReqVO reqVO, Long employeeId, Long applicantUserId,
                                 String processKey, Long businessId) {
        return createDemand(reqVO, employeeApi.getEmployee(employeeId), applicantUserId, processKey, businessId);
    }

    private Long createDemand(EamDemandCreateReqVO reqVO, HrmEmployeeRespDTO employee, Long applicantUserId,
                              String processKey, Long businessId) {
        if (employee == null || employee.getUserId() == null) {
            throw exception(EMPLOYEE_NOT_BOUND);
        }
        EamDemandDO demand = new EamDemandDO();
        demand.setNo("DM-" + System.currentTimeMillis());
        demand.setEmployeeId(employee.getId());
        demand.setApplicantUserId(applicantUserId);
        demand.setApplicantDeptId(employee.getDeptId());
        demand.setStatus(STATUS_APPROVING);
        demand.setReason(reqVO.getReason());
        demandMapper.insert(demand);
        for (EamDemandItemReqVO input : reqVO.getItems()) {
            EamCategoryDO category = categoryService.validateCategoryExists(input.getCategoryId());
            EamCategoryPolicy policy = categoryService.getEffectivePolicy(input.getCategoryId());
            EamCategoryFieldService.NormalizedExtFields normalized = fieldService
                    .validateAndNormalizeExtFieldsWithSnapshots(input.getCategoryId(), input.getExtFields());
            EamDemandItemDO item = new EamDemandItemDO();
            item.setDemandId(demand.getId());
            item.setName(input.getName());
            item.setCategoryId(input.getCategoryId());
            item.setManagementMode(category.getManagementMode());
            item.setDeliveryMode(policy.deliveryMode());
            item.setDeliveryModeLabelSnapshot(deliveryLabel(policy.deliveryMode()));
            item.setCustodyMode(policy.custodyMode());
            item.setCustodyModeLabelSnapshot(custodyLabel(policy.custodyMode()));
            item.setQuantity(input.getQuantity());
            item.setUnit(input.getUnit() == null || input.getUnit().isBlank() ? category.getUnit() : input.getUnit());
            item.setExtFields(normalized.values());
            item.setExtFieldLabels(normalized.labels());
            item.setExtFieldDictTypes(normalized.dictTypes());
            item.setReservedQuantity(0);
            item.setPurchasedQuantity(0);
            item.setFulfilledQuantity(0);
            item.setClosedQuantity(0);
            itemMapper.insert(item);
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("employeeId", employee.getId());
        variables.put("employeeUserId", employee.getUserId());
        if (employee.getDeptId() != null) variables.put("employeeDeptId", employee.getDeptId());
        if (employee.getLeaderUserId() != null) variables.put("leaderUserId", employee.getLeaderUserId());
        String processId = approvalService.start(processKey,
                String.valueOf(businessId == null ? demand.getId() : businessId),
                format("资产需求 {}", demand.getNo()), variables);
        demandMapper.updateById(new EamDemandDO().setId(demand.getId()).setProcessInstanceId(processId));
        return demand.getId();
    }

    @Override
    public EamDemandRespVO getDemand(Long id) {
        EamDemandDO demand = demandMapper.selectById(id);
        if (demand == null) throw exception(DEMAND_NOT_EXISTS);
        EamDemandRespVO result = BeanUtils.toBean(demand, EamDemandRespVO.class);
        result.setItems(BeanUtils.toBean(itemMapper.selectListByDemandId(id), EamDemandItemRespVO.class));
        return result;
    }

    @Override
    public List<EamDemandRespVO> getMyDemands(Long userId) {
        return demandMapper.selectListByApplicantUserId(userId).stream().map(item -> getDemand(item.getId())).toList();
    }

    @Override
    public List<EamDemandRespVO> getDemandList() {
        return demandMapper.selectListOrderByIdDesc().stream().map(item -> getDemand(item.getId())).toList();
    }

    @Override
    public List<EamStockCandidateRespVO> getCandidates(Long demandItemId) {
        EamDemandItemDO item = itemMapper.selectById(demandItemId);
        if (item == null) throw exception(DEMAND_ITEM_NOT_EXISTS);
        return stockService.getCandidates(item);
    }

    @Override
    public List<EamStockCandidateRespVO> previewCandidates(EamDemandItemReqVO input) {
        EamCategoryDO category = categoryService.validateCategoryExists(input.getCategoryId());
        EamCategoryPolicy policy = categoryService.getEffectivePolicy(input.getCategoryId());
        EamCategoryFieldService.NormalizedExtFields normalized = fieldService
                .validateAndNormalizeExtFieldsWithSnapshots(input.getCategoryId(), input.getExtFields());
        EamDemandItemDO item = new EamDemandItemDO();
        item.setName(input.getName());
        item.setCategoryId(input.getCategoryId());
        item.setManagementMode(category.getManagementMode());
        item.setDeliveryMode(policy.deliveryMode());
        item.setCustodyMode(policy.custodyMode());
        item.setQuantity(input.getQuantity());
        item.setUnit(input.getUnit() == null || input.getUnit().isBlank() ? category.getUnit() : input.getUnit());
        item.setExtFields(normalized.values());
        return stockService.getCandidates(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reserve(EamStockReserveReqVO reqVO) {
        EamDemandItemDO item = itemMapper.selectByIdForUpdate(reqVO.getDemandItemId());
        if (item == null) throw exception(DEMAND_ITEM_NOT_EXISTS);
        EamDemandDO demand = demandMapper.selectByIdForUpdate(item.getDemandId());
        if (demand == null || !Objects.equals(STATUS_APPROVED, demand.getStatus())) throw exception(DEMAND_STATUS_INVALID);
        int remaining = item.getQuantity() - value(item.getReservedQuantity()) - value(item.getPurchasedQuantity());
        int quantity = reqVO.getQuantity() == null ? 1 : reqVO.getQuantity();
        if (quantity > remaining) throw exception(PURCHASE_QUANTITY_INVALID);
        Long reservationId = stockService.reserve(item, demand.getEmployeeId(), reqVO);
        itemMapper.updateById(new EamDemandItemDO().setId(item.getId())
                .setReservedQuantity(value(item.getReservedQuantity()) + quantity));
        return reservationId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reserveAndAllocate(EamStockReserveReqVO reqVO) {
        Long reservationId = reserve(reqVO);
        EamStockAllocateReqVO allocateReqVO = new EamStockAllocateReqVO();
        allocateReqVO.setReservationId(reservationId);
        allocate(allocateReqVO);
        return reservationId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void allocate(EamStockAllocateReqVO reqVO) {
        cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockReservationDO reservation =
                stockService.getReservation(reqVO.getReservationId());
        if (reservation == null) throw exception(STOCK_CANDIDATE_INVALID);
        EamDemandItemDO item = itemMapper.selectByIdForUpdate(reservation.getDemandItemId());
        if (item == null) throw exception(DEMAND_ITEM_NOT_EXISTS);
        EamDemandDO demand = demandMapper.selectByIdForUpdate(item.getDemandId());
        if (demand == null || !Objects.equals(demand.getStatus(), STATUS_APPROVED)) {
            throw exception(DEMAND_STATUS_INVALID);
        }
        HrmEmployeeRespDTO employee = employeeApi.getEmployee(demand.getEmployeeId());
        stockService.allocateReservation(reservation.getId(), item, employee == null ? null : employee.getDeptId());
        itemMapper.updateById(new EamDemandItemDO().setId(item.getId())
                .setFulfilledQuantity(value(item.getFulfilledQuantity()) + reservation.getQuantity()));
        completeDemandIfResolved(demand.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFulfilledQuantity(Long demandItemId, int quantity) {
        updateResolvedQuantity(demandItemId, quantity, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addClosedQuantity(Long demandItemId, int quantity) {
        updateResolvedQuantity(demandItemId, quantity, true);
    }

    private void updateResolvedQuantity(Long demandItemId, int quantity, boolean closed) {
        EamDemandItemDO item = itemMapper.selectByIdForUpdate(demandItemId);
        if (item == null) throw exception(DEMAND_ITEM_NOT_EXISTS);
        EamDemandItemDO update = new EamDemandItemDO().setId(item.getId());
        if (closed) update.setClosedQuantity(value(item.getClosedQuantity()) + quantity);
        else update.setFulfilledQuantity(value(item.getFulfilledQuantity()) + quantity);
        itemMapper.updateById(update);
        completeDemandIfResolved(item.getDemandId());
    }

    private void completeDemandIfResolved(Long demandId) {
        boolean completed = itemMapper.selectListByDemandId(demandId).stream().allMatch(item ->
                value(item.getFulfilledQuantity()) + value(item.getClosedQuantity()) >= item.getQuantity());
        if (completed) demandMapper.updateById(new EamDemandDO().setId(demandId).setStatus(STATUS_COMPLETED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(Long demandId, Integer bpmStatus, String reason) {
        EamDemandDO demand = demandMapper.selectByIdForUpdate(demandId);
        if (demand == null) throw exception(DEMAND_NOT_EXISTS);
        if (!Objects.equals(STATUS_APPROVING, demand.getStatus())) return;
        if (APPROVE.getStatus().equals(bpmStatus)) {
            demandMapper.updateById(new EamDemandDO().setId(demandId).setStatus(STATUS_APPROVED));
        } else if (REJECT.getStatus().equals(bpmStatus)) {
            demandMapper.updateById(new EamDemandDO().setId(demandId).setStatus(STATUS_REJECTED).setReason(reason));
        } else if (CANCEL.getStatus().equals(bpmStatus)) {
            demandMapper.updateById(new EamDemandDO().setId(demandId).setStatus(STATUS_CANCELLED).setReason(reason));
        }
    }

    private String deliveryLabel(Integer mode) {
        return EamDeliveryModeEnum.PHYSICAL.getMode().equals(mode) ? "实物入库" : "数字交付";
    }
    private String custodyLabel(Integer mode) {
        return EamCustodyModeEnum.CONSUMABLE.getMode().equals(mode) ? "消耗型" : "需归还型";
    }
    private int value(Integer value) { return value == null ? 0 : value; }
}
