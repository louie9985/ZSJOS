package cn.iocoder.yudao.module.eam.service.procurement;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetSaveReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.*;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.*;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockBalanceDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.*;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockHoldingMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockReservationMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamCustodyModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamDeliveryModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryPolicy;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.employeeasset.EamEmployeeAssetService;
import cn.iocoder.yudao.module.eam.service.stock.EamStockService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.hutool.core.util.StrUtil.format;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.*;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.*;

@Service
public class EamPurchaseServiceImpl implements EamPurchaseService {

    private static final String PAYMENT_MODE_DICT = "eam_purchase_payment_mode";

    @Resource private EamPurchaseMapper purchaseMapper;
    @Resource private EamPurchaseItemMapper purchaseItemMapper;
    @Resource private EamPurchaseSourceMapper sourceMapper;
    @Resource private EamReceiptMapper receiptMapper;
    @Resource private EamReceiptItemMapper receiptItemMapper;
    @Resource private EamDemandMapper demandMapper;
    @Resource private EamDemandItemMapper demandItemMapper;
    @Resource private EamAssetMapper assetMapper;
    @Resource private EamStockHoldingMapper holdingMapper;
    @Resource private EamStockReservationMapper reservationMapper;
    @Resource private EamCategoryService categoryService;
    @Resource private EamCategoryFieldService categoryFieldService;
    @Resource private EamStockService stockService;
    @Resource private EamAssetService assetService;
    @Resource private EamEmployeeAssetService employeeAssetService;
    @Resource private EamDemandService demandService;
    @Resource private HrmEmployeeApi employeeApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private EamApprovalService approvalService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchase(EamPurchaseCreateReqVO reqVO, Long applicantUserId) {
        EamPurchaseDO purchase = new EamPurchaseDO();
        purchase.setNo("PO-" + System.currentTimeMillis());
        purchase.setStatus(STATUS_APPROVING);
        purchase.setPaymentMode(reqVO.getPaymentMode());
        purchase.setPaymentModeLabelSnapshot(resolvePaymentModeLabel(reqVO.getPaymentMode()));
        purchase.setSupplierNameSnapshot(reqVO.getSupplierName());
        purchase.setSupplierContactSnapshot(reqVO.getSupplierContact());
        purchase.setEstimatedAmount(reqVO.getEstimatedAmount());
        purchase.setExpectedArrivalDate(reqVO.getExpectedArrivalDate());
        purchase.setExpenseStatus(EXPENSE_NOT_SUBMITTED);
        purchase.setApplicantUserId(applicantUserId);
        purchase.setFileUrls(reqVO.getFileUrls());
        purchase.setRemark(reqVO.getRemark());
        purchaseMapper.insert(purchase);

        for (EamPurchaseItemReqVO input : reqVO.getItems()) {
            createPurchaseItem(purchase.getId(), input);
        }
        String processId = approvalService.start(PURCHASE_PROCESS_KEY, String.valueOf(purchase.getId()),
                format("办公采购 {}", purchase.getNo()));
        purchaseMapper.updateById(new EamPurchaseDO().setId(purchase.getId()).setProcessInstanceId(processId));
        return purchase.getId();
    }

    private void createPurchaseItem(Long purchaseId, EamPurchaseItemReqVO input) {
        EamDemandItemDO demandItem = input.getDemandItemId() == null
                ? null : demandItemMapper.selectByIdForUpdate(input.getDemandItemId());
        EamDemandDO demand = demandItem == null ? null : demandMapper.selectByIdForUpdate(demandItem.getDemandId());
        if (input.getDemandItemId() != null && (demandItem == null || demand == null)) {
            throw exception(DEMAND_ITEM_NOT_EXISTS);
        }
        if (demand != null && !Objects.equals(demand.getStatus(), STATUS_APPROVED)) {
            throw exception(DEMAND_STATUS_INVALID);
        }
        if (demandItem != null) {
            int remaining = demandItem.getQuantity() - value(demandItem.getReservedQuantity())
                    - value(demandItem.getPurchasedQuantity());
            if (input.getQuantity() > remaining) throw exception(PURCHASE_QUANTITY_INVALID);
        }

        Long categoryId = demandItem == null ? input.getCategoryId() : demandItem.getCategoryId();
        EamCategoryDO category = categoryService.validateCategoryExists(categoryId);
        EamCategoryPolicy policy = categoryService.getEffectivePolicy(categoryId);
        EamCategoryFieldService.NormalizedExtFields ext = demandItem == null
                ? categoryFieldService.validateAndNormalizeExtFieldsWithSnapshots(categoryId, input.getExtFields())
                : new EamCategoryFieldService.NormalizedExtFields(
                        demandItem.getExtFields(), demandItem.getExtFieldLabels(), demandItem.getExtFieldDictTypes());

        EamPurchaseItemDO item = new EamPurchaseItemDO();
        item.setPurchaseId(purchaseId);
        item.setName(demandItem == null ? input.getName() : demandItem.getName());
        item.setCategoryId(categoryId);
        item.setManagementMode(demandItem == null ? category.getManagementMode() : demandItem.getManagementMode());
        item.setDeliveryMode(demandItem == null ? policy.deliveryMode() : demandItem.getDeliveryMode());
        item.setDeliveryModeLabelSnapshot(deliveryLabel(item.getDeliveryMode()));
        item.setCustodyMode(demandItem == null ? policy.custodyMode() : demandItem.getCustodyMode());
        item.setCustodyModeLabelSnapshot(custodyLabel(item.getCustodyMode()));
        item.setQuantity(input.getQuantity());
        item.setReceivedQuantity(0);
        item.setReturnedQuantity(0);
        item.setShortClosedQuantity(0);
        item.setUnit(demandItem == null ? StrUtil.blankToDefault(input.getUnit(), category.getUnit()) : demandItem.getUnit());
        item.setUnitPrice(input.getUnitPrice());
        item.setExtFields(ext.values());
        item.setExtFieldLabels(ext.labels());
        item.setExtFieldDictTypes(ext.dictTypes());
        purchaseItemMapper.insert(item);

        HrmEmployeeRespDTO target = demand != null ? employeeApi.getEmployee(demand.getEmployeeId())
                : input.getTargetEmployeeId() == null ? null : employeeApi.getEmployee(input.getTargetEmployeeId());
        if ((demand != null || input.getTargetEmployeeId() != null) && target == null) {
            throw exception(EMPLOYEE_NOT_BOUND);
        }
        if (target != null) {
            EamPurchaseSourceDO source = new EamPurchaseSourceDO();
            source.setPurchaseItemId(item.getId());
            source.setDemandItemId(demandItem == null ? null : demandItem.getId());
            source.setQuantity(input.getQuantity());
            source.setFulfilledQuantity(0);
            source.setClosedQuantity(0);
            source.setTargetEmployeeId(target.getId());
            source.setTargetDeptId(target.getDeptId());
            sourceMapper.insert(source);
        }
        if (demandItem != null) {
            demandItemMapper.updateById(new EamDemandItemDO().setId(demandItem.getId())
                    .setPurchasedQuantity(value(demandItem.getPurchasedQuantity()) + input.getQuantity()));
        }
    }

    @Override
    public EamPurchaseRespVO getPurchase(Long id) {
        EamPurchaseDO purchase = purchaseMapper.selectById(id);
        if (purchase == null) throw exception(PURCHASE_NOT_EXISTS);
        EamPurchaseRespVO result = BeanUtils.toBean(purchase, EamPurchaseRespVO.class);
        result.setItems(BeanUtils.toBean(purchaseItemMapper.selectListByPurchaseId(id), EamPurchaseItemRespVO.class));
        return result;
    }

    @Override
    public List<EamPurchaseRespVO> getPurchaseList() {
        return purchaseMapper.selectListOrderByIdDesc().stream().map(item -> getPurchase(item.getId())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long receive(Long purchaseId, EamReceiptCreateReqVO reqVO) {
        EamPurchaseDO purchase = validateReceivablePurchase(purchaseId);
        EamReceiptDO receipt = createReceipt(purchaseId, RECEIPT_INBOUND, reqVO);
        BigDecimal amountDelta = BigDecimal.ZERO;
        for (EamReceiptItemReqVO input : reqVO.getItems()) {
            EamPurchaseItemDO item = purchaseItemMapper.selectByIdForUpdate(input.getPurchaseItemId());
            validateReceiptItem(purchaseId, item, input.getQuantity());
            EamCategoryFieldService.NormalizedExtFields actualExtFields = normalizeActualExtFields(item, input);
            if (EamManagementModeEnum.SERIALIZED.getMode().equals(item.getManagementMode())) {
                receiveSerialized(receipt, item, input, actualExtFields);
            } else {
                receiveBatch(receipt, item, input, actualExtFields);
            }
            int received = value(item.getReceivedQuantity()) + input.getQuantity();
            purchaseItemMapper.updateById(new EamPurchaseItemDO().setId(item.getId()).setReceivedQuantity(received));
            BigDecimal price = input.getUnitPrice() == null ? item.getUnitPrice() : input.getUnitPrice();
            if (price != null) amountDelta = amountDelta.add(price.multiply(BigDecimal.valueOf(input.getQuantity())));
        }
        purchaseMapper.updateById(new EamPurchaseDO().setId(purchaseId)
                .setActualAmount(value(purchase.getActualAmount()).add(amountDelta))
                .setStatus(isPurchaseResolved(purchaseId) ? STATUS_COMPLETED : STATUS_FULFILLING));
        return receipt.getId();
    }

    private void receiveSerialized(EamReceiptDO receipt, EamPurchaseItemDO item, EamReceiptItemReqVO input,
                                   EamCategoryFieldService.NormalizedExtFields actualExtFields) {
        List<String> serials = normalizeSerials(item, input);
        List<String> receivedIdentities = new ArrayList<>();
        List<EamPurchaseSourceDO> sources = sourceMapper.selectListByPurchaseItemId(item.getId());
        for (int i = 0; i < input.getQuantity(); i++) {
            String sn = serials.isEmpty() ? null : serials.get(i);
            if (sn != null && assetMapper.selectBySnAndCategoryId(sn, item.getCategoryId()) != null) {
                throw exception(PURCHASE_SERIAL_NUMBER_INVALID);
            }
            EamAssetSaveReqVO assetReq = new EamAssetSaveReqVO();
            assetReq.setName(item.getName());
            assetReq.setCategoryId(item.getCategoryId());
            assetReq.setQuantity(1);
            assetReq.setSn(sn);
            assetReq.setOriginalValue(input.getUnitPrice() == null ? item.getUnitPrice() : input.getUnitPrice());
            assetReq.setPurchaseDate(LocalDate.now());
            assetReq.setExtFields(actualExtFields.values());
            Long assetId = assetService.createAsset(assetReq);
            EamAssetDO createdAsset = assetMapper.selectById(assetId);
            receivedIdentities.add(sn == null ? createdAsset.getAssetCode() : sn);
            EamPurchaseSourceDO source = nextOpenSource(sources);
            if (source != null) {
                allocateSerialized(assetId, item, source);
                fulfillSource(source, 1);
            }
        }
        insertReceiptItem(receipt.getId(), item, input, null, receivedIdentities, actualExtFields);
    }

    private void receiveBatch(EamReceiptDO receipt, EamPurchaseItemDO item, EamReceiptItemReqVO input,
                              EamCategoryFieldService.NormalizedExtFields actualExtFields) {
        EamStockBalanceDO balance = stockService.getOrCreateBalance(item.getCategoryId(), item.getName(), item.getUnit(),
                item.getManagementMode(), item.getDeliveryMode(), item.getCustodyMode(), actualExtFields.values(),
                actualExtFields.labels(), actualExtFields.dictTypes());
        stockService.inbound(balance.getId(), input.getQuantity(), "PURCHASE_RECEIPT", receipt.getId(), receipt.getRemark());
        int remaining = input.getQuantity();
        for (EamPurchaseSourceDO source : sourceMapper.selectListByPurchaseItemId(item.getId())) {
            int allocated = Math.min(remaining, sourceRemaining(source));
            if (allocated <= 0) continue;
            stockService.outbound(balance.getId(), allocated, "PURCHASE_ALLOCATION", receipt.getId(), "采购定向分配");
            if (EamCustodyModeEnum.RETURNABLE.getMode().equals(item.getCustodyMode())) {
                employeeAssetService.createHolding(source.getTargetEmployeeId(), null,
                        balance.getId(), item.getName(), item.getUnit(), allocated, item.getCustodyMode());
            }
            fulfillSource(source, allocated);
            remaining -= allocated;
            if (remaining == 0) break;
        }
        insertReceiptItem(receipt.getId(), item, input, balance.getId(), List.of(), actualExtFields);
    }

    private void allocateSerialized(Long assetId, EamPurchaseItemDO item, EamPurchaseSourceDO source) {
        if (EamCustodyModeEnum.RETURNABLE.getMode().equals(item.getCustodyMode())) {
            employeeAssetService.createHolding(source.getTargetEmployeeId(), assetId,
                    null, item.getName(), item.getUnit(), 1, item.getCustodyMode());
            assetService.applyChange(assetId, EamAssetStatusEnum.IN_USE.getStatus(), source.getTargetEmployeeId(),
                    source.getTargetDeptId(), EamChangeTypeEnum.RECEIVE.getType(), source.getId(),
                    "采购定向分配，待员工签收");
        } else {
            assetService.applyChange(assetId, EamAssetStatusEnum.IN_USE.getStatus(), source.getTargetEmployeeId(),
                    source.getTargetDeptId(), EamChangeTypeEnum.RECEIVE.getType(), source.getId(), "采购定向分配");
        }
    }

    private void fulfillSource(EamPurchaseSourceDO source, int quantity) {
        sourceMapper.updateById(new EamPurchaseSourceDO().setId(source.getId())
                .setFulfilledQuantity(value(source.getFulfilledQuantity()) + quantity));
        source.setFulfilledQuantity(value(source.getFulfilledQuantity()) + quantity);
        if (source.getDemandItemId() != null) demandService.addFulfilledQuantity(source.getDemandItemId(), quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long returnToSupplier(Long purchaseId, EamReceiptCreateReqVO reqVO) {
        EamPurchaseDO purchase = purchaseMapper.selectByIdForUpdate(purchaseId);
        if (purchase == null) throw exception(PURCHASE_NOT_EXISTS);
        if (!Objects.equals(purchase.getStatus(), STATUS_FULFILLING)
                && !Objects.equals(purchase.getStatus(), STATUS_COMPLETED)) throw exception(PURCHASE_STATUS_INVALID);
        EamReceiptDO receipt = createReceipt(purchaseId, RECEIPT_RETURN, reqVO);
        BigDecimal amountDelta = BigDecimal.ZERO;
        for (EamReceiptItemReqVO input : reqVO.getItems()) {
            EamPurchaseItemDO item = purchaseItemMapper.selectByIdForUpdate(input.getPurchaseItemId());
            if (item == null || !Objects.equals(item.getPurchaseId(), purchaseId)
                    || input.getQuantity() > value(item.getReceivedQuantity()) - value(item.getReturnedQuantity())) {
                throw exception(PURCHASE_QUANTITY_INVALID);
            }
            Long balanceId = null;
            List<String> serials = List.of();
            EamCategoryFieldService.NormalizedExtFields actualExtFields = normalizeActualExtFields(item, input);
            if (EamManagementModeEnum.SERIALIZED.getMode().equals(item.getManagementMode())) {
                serials = normalizeReturnIdentities(input);
                validateReturnIdentities(purchaseId, item.getId(), serials);
                for (String identity : serials) {
                    EamAssetDO asset = assetMapper.selectByIdentityAndCategoryId(identity, item.getCategoryId());
                    if (asset == null || !EamAssetStatusEnum.IDLE.getStatus().equals(asset.getStatus())
                            || holdingMapper.selectOpenByAssetId(asset.getId()) != null
                            || reservationMapper.selectActiveByAssetId(asset.getId()) != null) {
                        throw exception(PURCHASE_RETURN_SOURCE_INVALID);
                    }
                    assetService.applyChange(asset.getId(), EamAssetStatusEnum.RETURNED_TO_SUPPLIER.getStatus(),
                            null, null, EamChangeTypeEnum.SUPPLIER_RETURN.getType(), receipt.getId(), "供应商退货");
                }
            } else {
                EamStockBalanceDO balance = validateReturnBalance(purchaseId, item, input);
                balanceId = balance.getId();
                stockService.outbound(balanceId, input.getQuantity(), "SUPPLIER_RETURN", receipt.getId(), reqVO.getRemark());
            }
            insertReceiptItem(receipt.getId(), item, input, balanceId, serials, actualExtFields);
            purchaseItemMapper.updateById(new EamPurchaseItemDO().setId(item.getId())
                    .setReturnedQuantity(value(item.getReturnedQuantity()) + input.getQuantity()));
            BigDecimal price = input.getUnitPrice() == null ? item.getUnitPrice() : input.getUnitPrice();
            if (price != null) amountDelta = amountDelta.add(price.multiply(BigDecimal.valueOf(input.getQuantity())));
        }
        purchaseMapper.updateById(new EamPurchaseDO().setId(purchaseId)
                .setActualAmount(value(purchase.getActualAmount()).subtract(amountDelta)));
        return receipt.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shortClose(Long purchaseId, EamShortCloseReqVO reqVO) {
        EamPurchaseDO purchase = validateReceivablePurchase(purchaseId);
        EamPurchaseItemDO item = purchaseItemMapper.selectByIdForUpdate(reqVO.getPurchaseItemId());
        validateReceiptItem(purchaseId, item, reqVO.getQuantity());
        purchaseItemMapper.updateById(new EamPurchaseItemDO().setId(item.getId())
                .setShortClosedQuantity(value(item.getShortClosedQuantity()) + reqVO.getQuantity())
                .setShortCloseRemark(reqVO.getReason()));
        int remaining = reqVO.getQuantity();
        for (EamPurchaseSourceDO source : sourceMapper.selectListByPurchaseItemId(item.getId())) {
            int closed = Math.min(remaining, sourceRemaining(source));
            if (closed <= 0) continue;
            sourceMapper.updateById(new EamPurchaseSourceDO().setId(source.getId())
                    .setClosedQuantity(value(source.getClosedQuantity()) + closed));
            if (source.getDemandItemId() != null) demandService.addClosedQuantity(source.getDemandItemId(), closed);
            remaining -= closed;
            if (remaining == 0) break;
        }
        purchaseMapper.updateById(new EamPurchaseDO().setId(purchaseId)
                .setStatus(isPurchaseResolved(purchaseId) ? STATUS_COMPLETED : STATUS_FULFILLING)
                .setRemark(StrUtil.blankToDefault(purchase.getRemark(), "") + "\n少到关闭：" + reqVO.getReason()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitExpense(Long purchaseId, EamExpenseSubmitReqVO reqVO) {
        EamPurchaseDO purchase = purchaseMapper.selectByIdForUpdate(purchaseId);
        if (purchase == null) throw exception(PURCHASE_NOT_EXISTS);
        if (!Objects.equals(purchase.getExpenseStatus(), EXPENSE_NOT_SUBMITTED)) throw exception(PURCHASE_STATUS_INVALID);
        String processId = approvalService.start(EXPENSE_PROCESS_KEY, String.valueOf(purchaseId),
                format("采购费用 {}", purchase.getNo()), Map.of(
                        "paymentMode", purchase.getPaymentMode(), "actualAmount", reqVO.getActualAmount()));
        purchaseMapper.updateById(new EamPurchaseDO().setId(purchaseId).setActualAmount(reqVO.getActualAmount())
                .setFileUrls(reqVO.getFileUrls()).setExpenseStatus(STATUS_APPROVING)
                .setExpenseProcessInstanceId(processId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePurchaseProcessResult(Long purchaseId, Integer bpmStatus, String reason) {
        EamPurchaseDO purchase = purchaseMapper.selectByIdForUpdate(purchaseId);
        if (purchase == null) throw exception(PURCHASE_NOT_EXISTS);
        if (!Objects.equals(purchase.getStatus(), STATUS_APPROVING)) return;
        if (APPROVE.getStatus().equals(bpmStatus)) purchaseMapper.updateById(new EamPurchaseDO().setId(purchaseId).setStatus(STATUS_APPROVED));
        else if (REJECT.getStatus().equals(bpmStatus)) {
            releaseDemandCommitments(purchaseId);
            purchaseMapper.updateById(new EamPurchaseDO().setId(purchaseId).setStatus(STATUS_REJECTED).setRemark(reason));
        } else if (CANCEL.getStatus().equals(bpmStatus)) {
            releaseDemandCommitments(purchaseId);
            purchaseMapper.updateById(new EamPurchaseDO().setId(purchaseId).setStatus(STATUS_CANCELLED).setRemark(reason));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleExpenseProcessResult(Long purchaseId, Integer bpmStatus, String reason) {
        EamPurchaseDO purchase = purchaseMapper.selectByIdForUpdate(purchaseId);
        if (purchase == null) throw exception(PURCHASE_NOT_EXISTS);
        if (!Objects.equals(purchase.getExpenseStatus(), STATUS_APPROVING)) return;
        Integer status = APPROVE.getStatus().equals(bpmStatus) ? STATUS_APPROVED
                : REJECT.getStatus().equals(bpmStatus) ? STATUS_REJECTED
                : CANCEL.getStatus().equals(bpmStatus) ? STATUS_CANCELLED : null;
        if (status != null) purchaseMapper.updateById(new EamPurchaseDO().setId(purchaseId)
                .setExpenseStatus(status).setRemark(reason == null ? purchase.getRemark() : reason));
    }

    private EamPurchaseDO validateReceivablePurchase(Long purchaseId) {
        EamPurchaseDO purchase = purchaseMapper.selectByIdForUpdate(purchaseId);
        if (purchase == null) throw exception(PURCHASE_NOT_EXISTS);
        if (!Objects.equals(purchase.getStatus(), STATUS_APPROVED)
                && !Objects.equals(purchase.getStatus(), STATUS_FULFILLING)) throw exception(PURCHASE_STATUS_INVALID);
        return purchase;
    }

    private void releaseDemandCommitments(Long purchaseId) {
        List<Long> itemIds = purchaseItemMapper.selectListByPurchaseId(purchaseId).stream()
                .map(EamPurchaseItemDO::getId).toList();
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (EamPurchaseSourceDO source : sourceMapper.selectListByPurchaseItemIds(itemIds)) {
            if (source.getDemandItemId() != null) {
                quantities.merge(source.getDemandItemId(), source.getQuantity(), Integer::sum);
            }
        }
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            EamDemandItemDO demandItem = demandItemMapper.selectByIdForUpdate(entry.getKey());
            if (demandItem == null || value(demandItem.getPurchasedQuantity()) < entry.getValue()) {
                throw exception(PURCHASE_QUANTITY_INVALID);
            }
            demandItemMapper.updateById(new EamDemandItemDO().setId(demandItem.getId())
                    .setPurchasedQuantity(value(demandItem.getPurchasedQuantity()) - entry.getValue()));
        }
    }

    private void validateReceiptItem(Long purchaseId, EamPurchaseItemDO item, int quantity) {
        if (item == null || !Objects.equals(item.getPurchaseId(), purchaseId)
                || quantity > item.getQuantity() - value(item.getReceivedQuantity()) - value(item.getShortClosedQuantity())) {
            throw exception(PURCHASE_QUANTITY_INVALID);
        }
    }

    private EamReceiptDO createReceipt(Long purchaseId, int type, EamReceiptCreateReqVO reqVO) {
        EamReceiptDO receipt = new EamReceiptDO();
        receipt.setNo((type == RECEIPT_INBOUND ? "IN-" : "RT-") + System.currentTimeMillis());
        receipt.setPurchaseId(purchaseId);
        receipt.setType(type);
        receipt.setOperatorUserId(SecurityFrameworkUtils.getLoginUserId());
        receipt.setOperateTime(LocalDateTime.now());
        receipt.setFileUrls(reqVO.getFileUrls());
        receipt.setRemark(reqVO.getRemark());
        receiptMapper.insert(receipt);
        return receipt;
    }

    private void insertReceiptItem(Long receiptId, EamPurchaseItemDO item, EamReceiptItemReqVO input,
                                   Long balanceId, List<String> serials,
                                   EamCategoryFieldService.NormalizedExtFields actualExtFields) {
        EamReceiptItemDO record = new EamReceiptItemDO();
        record.setReceiptId(receiptId);
        record.setPurchaseItemId(item.getId());
        record.setStockBalanceId(balanceId);
        record.setQuantity(input.getQuantity());
        record.setUnitPrice(input.getUnitPrice() == null ? item.getUnitPrice() : input.getUnitPrice());
        record.setSerialNumbers(serials);
        record.setActualExtFields(actualExtFields.values());
        record.setActualExtFieldLabels(actualExtFields.labels());
        record.setActualExtFieldDictTypes(actualExtFields.dictTypes());
        receiptItemMapper.insert(record);
    }

    private EamCategoryFieldService.NormalizedExtFields normalizeActualExtFields(
            EamPurchaseItemDO item, EamReceiptItemReqVO input) {
        Map<String, Object> actual = input.getActualExtFields() == null
                ? item.getExtFields() : input.getActualExtFields();
        return categoryFieldService.validateAndNormalizeExtFieldsWithSnapshots(item.getCategoryId(), actual);
    }

    private boolean isPurchaseResolved(Long purchaseId) {
        return purchaseItemMapper.selectListByPurchaseId(purchaseId).stream().allMatch(item ->
                value(item.getReceivedQuantity()) + value(item.getShortClosedQuantity()) >= item.getQuantity());
    }

    private EamPurchaseSourceDO nextOpenSource(List<EamPurchaseSourceDO> sources) {
        return sources.stream().filter(source -> sourceRemaining(source) > 0).findFirst().orElse(null);
    }

    private int sourceRemaining(EamPurchaseSourceDO source) {
        return source.getQuantity() - value(source.getFulfilledQuantity()) - value(source.getClosedQuantity());
    }

    private List<String> normalizeSerials(EamPurchaseItemDO item, EamReceiptItemReqVO input) {
        List<String> serials = input.getSerialNumbers() == null ? List.of()
                : input.getSerialNumbers().stream().map(String::trim).filter(StrUtil::isNotBlank).toList();
        if (new HashSet<>(serials).size() != serials.size()
                || !serials.isEmpty() && serials.size() != input.getQuantity()) {
            throw exception(PURCHASE_SERIAL_NUMBER_INVALID);
        }
        return serials;
    }

    private List<String> normalizeReturnIdentities(EamReceiptItemReqVO input) {
        List<String> identities = input.getSerialNumbers() == null ? List.of()
                : input.getSerialNumbers().stream().map(String::trim).filter(StrUtil::isNotBlank).toList();
        if (identities.size() != input.getQuantity() || new HashSet<>(identities).size() != identities.size()) {
            throw exception(PURCHASE_SERIAL_NUMBER_INVALID);
        }
        return identities;
    }

    private void validateReturnIdentities(Long purchaseId, Long purchaseItemId, List<String> identities) {
        Map<Long, Integer> receiptTypes = receiptMapper.selectListByPurchaseId(purchaseId).stream()
                .collect(java.util.stream.Collectors.toMap(EamReceiptDO::getId, EamReceiptDO::getType));
        Map<String, Integer> available = new HashMap<>();
        for (EamReceiptItemDO record : receiptItemMapper.selectListByPurchaseItemId(purchaseItemId)) {
            int direction = Objects.equals(receiptTypes.get(record.getReceiptId()), RECEIPT_INBOUND) ? 1
                    : Objects.equals(receiptTypes.get(record.getReceiptId()), RECEIPT_RETURN) ? -1 : 0;
            for (String identity : record.getSerialNumbers() == null ? List.<String>of() : record.getSerialNumbers()) {
                available.merge(identity, direction, Integer::sum);
            }
        }
        if (identities.stream().anyMatch(identity -> available.getOrDefault(identity, 0) <= 0)) {
            throw exception(PURCHASE_RETURN_SOURCE_INVALID);
        }
    }

    private EamStockBalanceDO validateReturnBalance(Long purchaseId, EamPurchaseItemDO item,
                                                     EamReceiptItemReqVO input) {
        if (input.getStockBalanceId() == null) throw exception(PURCHASE_RETURN_SOURCE_INVALID);
        EamStockBalanceDO balance = stockService.getBalance(input.getStockBalanceId());
        if (balance == null || !Objects.equals(balance.getCategoryId(), item.getCategoryId())
                || !Objects.equals(balance.getManagementMode(), item.getManagementMode())
                || !Objects.equals(balance.getDeliveryMode(), item.getDeliveryMode())
                || !Objects.equals(balance.getCustodyMode(), item.getCustodyMode())
                || !Objects.equals(balance.getUnit(), item.getUnit())) {
            throw exception(PURCHASE_RETURN_SOURCE_INVALID);
        }
        Map<Long, Integer> receiptTypes = receiptMapper.selectListByPurchaseId(purchaseId).stream()
                .collect(java.util.stream.Collectors.toMap(EamReceiptDO::getId, EamReceiptDO::getType));
        int available = receiptItemMapper.selectListByPurchaseItemId(item.getId()).stream()
                .filter(record -> Objects.equals(record.getStockBalanceId(), balance.getId()))
                .mapToInt(record -> Objects.equals(receiptTypes.get(record.getReceiptId()), RECEIPT_INBOUND)
                        ? record.getQuantity() : Objects.equals(receiptTypes.get(record.getReceiptId()), RECEIPT_RETURN)
                        ? -record.getQuantity() : 0)
                .sum();
        if (input.getQuantity() > available) throw exception(PURCHASE_RETURN_SOURCE_INVALID);
        return balance;
    }

    private String resolvePaymentModeLabel(Integer mode) {
        String value = String.valueOf(mode);
        try {
            dictDataApi.validateDictDataList(PAYMENT_MODE_DICT, List.of(value));
            return dictDataApi.getDictDataList(PAYMENT_MODE_DICT).stream()
                    .filter(item -> value.equals(item.getValue())).map(DictDataRespDTO::getLabel)
                    .findFirst().orElseThrow();
        } catch (Exception ex) {
            throw exception(PURCHASE_PAYMENT_MODE_INVALID);
        }
    }

    private String deliveryLabel(Integer mode) {
        return EamDeliveryModeEnum.PHYSICAL.getMode().equals(mode) ? "实物入库" : "数字交付";
    }

    private String custodyLabel(Integer mode) {
        return EamCustodyModeEnum.CONSUMABLE.getMode().equals(mode) ? "消耗型" : "需归还型";
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
