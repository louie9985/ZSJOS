package cn.iocoder.yudao.module.eam.service.stock;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockCandidateRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockReserveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandItemDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockBalanceDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockMovementDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockReservationDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockHoldingDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockReminderDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockBalanceMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockMovementMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockReservationMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockHoldingMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockReminderMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamCustodyModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamDeliveryModeEnum;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.dao.DuplicateKeyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.RESERVATION_ACTIVE;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.RESERVATION_FULFILLED;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.HOLDING_PENDING_SIGN;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.EXPIRY_FIELD_KEY;

@Service
public class EamStockServiceImpl implements EamStockService {

    @Resource private EamStockBalanceMapper balanceMapper;
    @Resource private EamStockMovementMapper movementMapper;
    @Resource private EamStockReservationMapper reservationMapper;
    @Resource private EamAssetMapper assetMapper;
    @Resource private EamStockHoldingMapper holdingMapper;
    @Resource private EamAssetService assetService;
    @Resource private EamStockReminderMapper reminderMapper;
    @Resource private ObjectMapper objectMapper;

    @Override
    public List<EamStockCandidateRespVO> getCandidates(EamDemandItemDO demandItem) {
        String signature = signature(demandItem.getExtFields());
        List<EamStockCandidateRespVO> result = new ArrayList<>();
        if (EamManagementModeEnum.BATCH.getMode().equals(demandItem.getManagementMode())) {
            for (EamStockBalanceDO balance : balanceMapper.selectAvailableCandidates(demandItem.getCategoryId(),
                    demandItem.getUnit(), signature, demandItem.getManagementMode(), demandItem.getDeliveryMode(),
                    demandItem.getCustodyMode())) {
                EamStockCandidateRespVO item = new EamStockCandidateRespVO();
                item.setCandidateType("BATCH");
                item.setStockBalanceId(balance.getId());
                item.setName(balance.getName());
                item.setCategoryId(balance.getCategoryId());
                item.setAvailableQuantity(balance.getAvailableQuantity());
                item.setUnit(balance.getUnit());
                result.add(item);
            }
        } else if (EamManagementModeEnum.SERIALIZED.getMode().equals(demandItem.getManagementMode())) {
            for (EamAssetDO asset : assetMapper.selectIdleListByCategoryId(demandItem.getCategoryId())) {
                if (!Objects.equals(signature(asset.getExtFields()), signature)
                        || reservationMapper.selectActiveByAssetId(asset.getId()) != null
                        || holdingMapper.selectOpenByAssetId(asset.getId()) != null) {
                    continue;
                }
                EamStockCandidateRespVO item = new EamStockCandidateRespVO();
                item.setCandidateType("SERIALIZED");
                item.setAssetId(asset.getId());
                item.setAssetCode(asset.getAssetCode());
                item.setName(asset.getName());
                item.setCategoryId(asset.getCategoryId());
                item.setAvailableQuantity(1);
                item.setUnit(asset.getUnit());
                result.add(item);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void allocateReservation(Long reservationId, EamDemandItemDO demandItem, Long targetDeptId) {
        EamStockReservationDO reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null || !Objects.equals(reservation.getStatus(), RESERVATION_ACTIVE)
                || !Objects.equals(reservation.getDemandItemId(), demandItem.getId())) {
            throw exception(STOCK_CANDIDATE_INVALID);
        }
        if (reservation.getStockBalanceId() != null) {
            EamStockBalanceDO before = balanceMapper.selectByIdForUpdate(reservation.getStockBalanceId());
            if (before == null || balanceMapper.consumeReserved(before.getId(), reservation.getQuantity()) == 0) {
                throw exception(STOCK_INSUFFICIENT);
            }
            recordMovement(before, -reservation.getQuantity(), before.getOnHandQuantity() - reservation.getQuantity(),
                    2, "DEMAND_ALLOCATION", reservationId, "需求库存分配");
            if (EamCustodyModeEnum.RETURNABLE.getMode().equals(demandItem.getCustodyMode())) {
                createHolding(reservation, null, reservation.getStockBalanceId(), demandItem);
            }
        } else {
            EamAssetDO asset = assetMapper.selectByIdForUpdate(reservation.getAssetId());
            if (asset == null || !EamAssetStatusEnum.IDLE.getStatus().equals(asset.getStatus())) {
                throw exception(STOCK_CANDIDATE_INVALID);
            }
            if (EamCustodyModeEnum.RETURNABLE.getMode().equals(demandItem.getCustodyMode())) {
                createHolding(reservation, asset.getId(), null, demandItem);
                assetService.applyChange(asset.getId(), EamAssetStatusEnum.IN_USE.getStatus(),
                        reservation.getTargetEmployeeId(), targetDeptId, EamChangeTypeEnum.RECEIVE.getType(),
                        reservationId, "需求库存分配，待员工签收");
            } else {
                assetService.applyChange(asset.getId(), EamAssetStatusEnum.IN_USE.getStatus(),
                        reservation.getTargetEmployeeId(), targetDeptId, EamChangeTypeEnum.RECEIVE.getType(),
                        reservationId, "需求库存分配");
            }
        }
        reservationMapper.updateById(new EamStockReservationDO().setId(reservationId)
                .setStatus(RESERVATION_FULFILLED));
    }

    @Override
    public EamStockReservationDO getReservation(Long reservationId) {
        return reservationMapper.selectById(reservationId);
    }

    private void createHolding(EamStockReservationDO reservation, Long assetId, Long balanceId,
                               EamDemandItemDO item) {
        EamStockHoldingDO holding = new EamStockHoldingDO();
        holding.setEmployeeId(reservation.getTargetEmployeeId());
        holding.setAssetId(assetId);
        holding.setStockBalanceId(balanceId);
        holding.setNameSnapshot(item.getName());
        holding.setQuantity(reservation.getQuantity());
        holding.setCustodyMode(item.getCustodyMode());
        holding.setStatus(HOLDING_PENDING_SIGN);
        holdingMapper.insert(holding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reserve(EamDemandItemDO item, Long employeeId, EamStockReserveReqVO reqVO) {
        int quantity = reqVO.getQuantity() == null ? 1 : reqVO.getQuantity();
        EamStockReservationDO reservation = new EamStockReservationDO();
        reservation.setDemandItemId(item.getId());
        reservation.setTargetEmployeeId(employeeId);
        reservation.setQuantity(quantity);
        reservation.setStatus(RESERVATION_ACTIVE);
        if (EamManagementModeEnum.SERIALIZED.getMode().equals(item.getManagementMode())) {
            if (reqVO.getAssetId() == null || quantity != 1) {
                throw exception(STOCK_CANDIDATE_INVALID);
            }
            EamAssetDO asset = assetMapper.selectByIdForUpdate(reqVO.getAssetId());
            if (asset == null || !EamAssetStatusEnum.IDLE.getStatus().equals(asset.getStatus())
                    || !item.getCategoryId().equals(asset.getCategoryId())
                    || !signature(item.getExtFields()).equals(signature(asset.getExtFields()))
                    || reservationMapper.selectActiveByAssetId(asset.getId()) != null
                    || holdingMapper.selectOpenByAssetId(asset.getId()) != null) {
                throw exception(STOCK_CANDIDATE_INVALID);
            }
            reservation.setAssetId(asset.getId());
        } else {
            if (reqVO.getStockBalanceId() == null) {
                throw exception(STOCK_CANDIDATE_INVALID);
            }
            EamStockBalanceDO balance = balanceMapper.selectByIdForUpdate(reqVO.getStockBalanceId());
            if (balance == null || !item.getCategoryId().equals(balance.getCategoryId())
                    || !Objects.equals(item.getUnit(), balance.getUnit())
                    || !Objects.equals(item.getManagementMode(), balance.getManagementMode())
                    || !Objects.equals(item.getDeliveryMode(), balance.getDeliveryMode())
                    || !Objects.equals(item.getCustodyMode(), balance.getCustodyMode())
                    || !signature(item.getExtFields()).equals(balance.getAttributeSignature())) {
                throw exception(STOCK_CANDIDATE_INVALID);
            }
            if (balanceMapper.reserve(balance.getId(), quantity) == 0) {
                throw exception(STOCK_INSUFFICIENT);
            }
            reservation.setStockBalanceId(balance.getId());
        }
        reservationMapper.insert(reservation);
        return reservation.getId();
    }

    @Override
    public EamStockBalanceDO getOrCreateBalance(EamDemandItemDO item) {
        return getOrCreateBalance(item.getCategoryId(), item.getName(), item.getUnit(), item.getManagementMode(),
                item.getDeliveryMode(), item.getCustodyMode(), item.getExtFields(), item.getExtFieldLabels(),
                item.getExtFieldDictTypes());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EamStockBalanceDO getOrCreateBalance(Long categoryId, String name, String unit,
                                                 Integer managementMode, Integer deliveryMode,
                                                 Integer custodyMode, Map<String, Object> extFields,
                                                 Map<String, String> extFieldLabels,
                                                 Map<String, String> extFieldDictTypes) {
        String signature = signature(extFields);
        LocalDate expiryDate = EamDeliveryModeEnum.DIGITAL.getMode().equals(deliveryMode)
                ? expiryDate(extFields) : null;
        EamStockBalanceDO existing = balanceMapper.selectBySignature(categoryId, unit, signature,
                managementMode, deliveryMode, custodyMode);
        if (existing != null) {
            if (!Objects.equals(existing.getNextExpiryDate(), expiryDate)) {
                balanceMapper.updateById(new EamStockBalanceDO().setId(existing.getId())
                        .setNextExpiryDate(expiryDate));
                existing.setNextExpiryDate(expiryDate);
            }
            return existing;
        }
        EamStockBalanceDO balance = new EamStockBalanceDO();
        balance.setName(name);
        balance.setCategoryId(categoryId);
        balance.setManagementMode(managementMode);
        balance.setDeliveryMode(deliveryMode);
        balance.setCustodyMode(custodyMode);
        balance.setUnit(unit);
        balance.setAttributeSignature(signature);
        balance.setExtFields(extFields);
        balance.setExtFieldLabels(extFieldLabels);
        balance.setExtFieldDictTypes(extFieldDictTypes);
        balance.setOnHandQuantity(0);
        balance.setReservedQuantity(0);
        balance.setFrozenQuantity(0);
        balance.setMinimumQuantity(0);
        balance.setNextExpiryDate(expiryDate);
        balance.setVersion(0);
        try {
            balanceMapper.insert(balance);
            return balance;
        } catch (DuplicateKeyException ex) {
            EamStockBalanceDO concurrent = balanceMapper.selectBySignature(categoryId, unit, signature,
                    managementMode, deliveryMode, custodyMode);
            if (concurrent != null) {
                return concurrent;
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inbound(Long balanceId, Integer quantity, String businessType, Long businessId, String remark) {
        EamStockBalanceDO before = balanceMapper.selectByIdForUpdate(balanceId);
        if (before == null) throw exception(STOCK_NOT_EXISTS);
        balanceMapper.inbound(balanceId, quantity);
        recordMovement(before, quantity, before.getOnHandQuantity() + quantity, 1, businessType, businessId, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inboundFrozen(Long balanceId, Integer quantity, String businessType, Long businessId, String remark) {
        EamStockBalanceDO before = balanceMapper.selectByIdForUpdate(balanceId);
        if (before == null) throw exception(STOCK_NOT_EXISTS);
        balanceMapper.inboundFrozen(balanceId, quantity);
        recordMovement(before, quantity, before.getOnHandQuantity() + quantity, 1, businessType, businessId, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void outbound(Long balanceId, Integer quantity, String businessType, Long businessId, String remark) {
        EamStockBalanceDO before = balanceMapper.selectByIdForUpdate(balanceId);
        if (before == null) throw exception(STOCK_NOT_EXISTS);
        if (balanceMapper.outbound(balanceId, quantity) == 0) throw exception(STOCK_INSUFFICIENT);
        recordMovement(before, -quantity, before.getOnHandQuantity() - quantity, 2, businessType, businessId, remark);
    }

    @Override
    public List<EamStockBalanceDO> getBalanceList() {
        return balanceMapper.selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<EamStockBalanceDO>()
                .orderByDesc(EamStockBalanceDO::getId));
    }

    @Override
    public EamStockBalanceDO getBalance(Long balanceId) {
        return balanceMapper.selectById(balanceId);
    }

    @Override
    public void updateMinimum(Long balanceId, Integer minimumQuantity) {
        if (balanceMapper.selectById(balanceId) == null) throw exception(STOCK_NOT_EXISTS);
        balanceMapper.updateById(new EamStockBalanceDO().setId(balanceId).setMinimumQuantity(minimumQuantity));
    }

    @Override
    public int scanLowStock() {
        return balanceMapper.selectLowStockList().size();
    }

    @Override
    public int scanExpiring(int days) {
        LocalDate deadline = LocalDate.now().plusDays(days);
        return balanceMapper.selectExpiringList(deadline).size() + assetMapper.selectExpiringList(deadline).size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createReminderProjections(int days) {
        LocalDate today = LocalDate.now();
        int created = 0;
        for (EamStockBalanceDO balance : balanceMapper.selectLowStockList()) {
            created += createReminder("LOW_STOCK", "STOCK_BALANCE", balance.getId(), today, null,
                    "库存品项“" + balance.getName() + "”低于最低库存");
        }
        for (EamStockBalanceDO balance : balanceMapper.selectExpiringList(today.plusDays(days))) {
            created += createReminder("DIGITAL_EXPIRY", "STOCK_BALANCE", balance.getId(), today,
                    balance.getNextExpiryDate(),
                    "数字资产“" + balance.getName() + "”即将到期");
        }
        for (EamAssetDO asset : assetMapper.selectExpiringList(today.plusDays(days))) {
            created += createReminder("DIGITAL_EXPIRY", "ASSET", asset.getId(), today,
                    expiryDate(asset.getExtFields()), "数字资产“" + asset.getName() + "”即将到期");
        }
        return created;
    }

    private int createReminder(String scene, String businessType, Long businessId, LocalDate reminderDate,
                               LocalDate dueDate, String content) {
        if (reminderMapper.selectByKey(scene, businessType, businessId, reminderDate) != null) return 0;
        EamStockReminderDO reminder = new EamStockReminderDO();
        reminder.setScene(scene);
        reminder.setBusinessType(businessType);
        reminder.setBusinessId(businessId);
        reminder.setDueDate(dueDate);
        reminder.setReminderDate(reminderDate);
        reminder.setStatus(0);
        reminder.setContent(content);
        try {
            reminderMapper.insert(reminder);
            return 1;
        } catch (DuplicateKeyException ignored) {
            return 0;
        }
    }

    private void recordMovement(EamStockBalanceDO before, int delta, int after, int type,
                                String businessType, Long businessId, String remark) {
        EamStockMovementDO movement = new EamStockMovementDO();
        movement.setStockBalanceId(before.getId());
        movement.setType(type);
        movement.setQuantity(delta);
        movement.setBeforeQuantity(before.getOnHandQuantity());
        movement.setAfterQuantity(after);
        movement.setBusinessType(businessType);
        movement.setBusinessId(businessId);
        movement.setOperatorUserId(SecurityFrameworkUtils.getLoginUserId());
        movement.setOperateTime(LocalDateTime.now());
        movement.setRemark(remark);
        movementMapper.insert(movement);
    }

    private String signature(Map<String, Object> extFields) {
        try {
            String canonical = objectMapper.writeValueAsString(new TreeMap<>(extFields == null ? Map.of() : extFields));
            return DigestUtil.sha256Hex(canonical);
        } catch (JsonProcessingException e) {
            throw exception(FIELD_VALUE_INVALID, "自定义字段");
        }
    }

    private LocalDate expiryDate(Map<String, Object> extFields) {
        Object value = extFields == null ? null : extFields.get(EXPIRY_FIELD_KEY);
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (java.time.format.DateTimeParseException e) {
            throw exception(FIELD_VALUE_INVALID, "套餐到期日");
        }
    }
}
