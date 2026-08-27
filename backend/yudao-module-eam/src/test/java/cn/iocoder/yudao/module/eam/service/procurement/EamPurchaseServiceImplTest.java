package cn.iocoder.yudao.module.eam.service.procurement;

import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamReceiptCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamReceiptItemReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamShortCloseReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandItemDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamPurchaseDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamPurchaseItemDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamPurchaseSourceDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamReceiptDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamReceiptItemDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockBalanceDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamDemandItemMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamDemandMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamPurchaseItemMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamPurchaseMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamPurchaseSourceMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamReceiptItemMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamReceiptMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockHoldingMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockReservationMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamCustodyModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamDeliveryModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.employeeasset.EamEmployeeAssetService;
import cn.iocoder.yudao.module.eam.service.stock.EamStockService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.REJECT;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.RECEIPT_INBOUND;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.STATUS_APPROVING;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.STATUS_COMPLETED;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.STATUS_FULFILLING;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.STATUS_REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EamPurchaseServiceImplTest {

    @InjectMocks
    private EamPurchaseServiceImpl service;
    @Mock private EamPurchaseMapper purchaseMapper;
    @Mock private EamPurchaseItemMapper purchaseItemMapper;
    @Mock private EamPurchaseSourceMapper sourceMapper;
    @Mock private EamReceiptMapper receiptMapper;
    @Mock private EamReceiptItemMapper receiptItemMapper;
    @Mock private EamDemandMapper demandMapper;
    @Mock private EamDemandItemMapper demandItemMapper;
    @Mock private EamAssetMapper assetMapper;
    @Mock private EamStockHoldingMapper holdingMapper;
    @Mock private EamStockReservationMapper reservationMapper;
    @Mock private EamCategoryService categoryService;
    @Mock private EamCategoryFieldService categoryFieldService;
    @Mock private EamStockService stockService;
    @Mock private EamAssetService assetService;
    @Mock private EamEmployeeAssetService employeeAssetService;
    @Mock private EamDemandService demandService;
    @Mock private HrmEmployeeApi employeeApi;
    @Mock private DictDataApi dictDataApi;
    @Mock private EamApprovalService approvalService;

    @BeforeEach
    void setUpExtFieldNormalization() {
        lenient().when(categoryFieldService.validateAndNormalizeExtFieldsWithSnapshots(anyLong(), any()))
                .thenAnswer(invocation -> new EamCategoryFieldService.NormalizedExtFields(
                        invocation.getArgument(1), Map.of(), Map.of()));
    }

    @Test
    void rejectedPurchase_shouldReleaseDemandCommitmentOnlyOnce() {
        EamPurchaseDO approving = purchase(STATUS_APPROVING);
        EamPurchaseDO rejected = purchase(STATUS_REJECTED);
        EamPurchaseItemDO purchaseItem = item(EamManagementModeEnum.BATCH.getMode(), 3);
        EamPurchaseSourceDO source = source(20L, 3, 0);
        EamDemandItemDO demandItem = new EamDemandItemDO();
        demandItem.setId(20L);
        demandItem.setPurchasedQuantity(5);
        when(purchaseMapper.selectByIdForUpdate(1L)).thenReturn(approving, rejected);
        when(purchaseItemMapper.selectListByPurchaseId(1L)).thenReturn(List.of(purchaseItem));
        when(sourceMapper.selectListByPurchaseItemIds(List.of(10L))).thenReturn(List.of(source));
        when(demandItemMapper.selectByIdForUpdate(20L)).thenReturn(demandItem);

        service.handlePurchaseProcessResult(1L, REJECT.getStatus(), "预算未通过");
        service.handlePurchaseProcessResult(1L, REJECT.getStatus(), "重复事件");

        ArgumentCaptor<EamDemandItemDO> demandCaptor = ArgumentCaptor.forClass(EamDemandItemDO.class);
        verify(demandItemMapper).updateById(demandCaptor.capture());
        assertEquals(2, demandCaptor.getValue().getPurchasedQuantity());
        ArgumentCaptor<EamPurchaseDO> purchaseCaptor = ArgumentCaptor.forClass(EamPurchaseDO.class);
        verify(purchaseMapper).updateById(purchaseCaptor.capture());
        assertEquals(STATUS_REJECTED, purchaseCaptor.getValue().getStatus());
    }

    @Test
    void receiveBatch_shouldPartiallyAllocateAndKeepRemainderInStock() {
        EamPurchaseDO purchase = purchase(2);
        EamPurchaseItemDO item = item(EamManagementModeEnum.BATCH.getMode(), 10);
        item.setCustodyMode(EamCustodyModeEnum.RETURNABLE.getMode());
        item.setUnitPrice(new BigDecimal("100"));
        EamPurchaseSourceDO source = source(20L, 3, 0);
        source.setTargetEmployeeId(30L);
        EamStockBalanceDO balance = new EamStockBalanceDO();
        balance.setId(50L);
        when(purchaseMapper.selectByIdForUpdate(1L)).thenReturn(purchase);
        when(purchaseItemMapper.selectByIdForUpdate(10L)).thenReturn(item);
        when(categoryFieldService.validateAndNormalizeExtFieldsWithSnapshots(item.getCategoryId(), Map.of()))
                .thenReturn(new EamCategoryFieldService.NormalizedExtFields(Map.of(), Map.of(), Map.of()));
        when(stockService.getOrCreateBalance(anyLong(), anyString(), anyString(), anyInt(), anyInt(),
                anyInt(), any(), any(), any())).thenReturn(balance);
        when(sourceMapper.selectListByPurchaseItemId(10L)).thenReturn(List.of(source));
        when(purchaseItemMapper.selectListByPurchaseId(1L)).thenReturn(List.of(item));

        service.receive(1L, receiptRequest(10L, 4, null, null));

        verify(stockService).inbound(eq(50L), eq(4), eq("PURCHASE_RECEIPT"), any(), any());
        verify(stockService).outbound(eq(50L), eq(3), eq("PURCHASE_ALLOCATION"), any(), anyString());
        verify(employeeAssetService).createHolding(30L, null, 50L, item.getName(), item.getUnit(),
                3, EamCustodyModeEnum.RETURNABLE.getMode());
        verify(demandService).addFulfilledQuantity(20L, 3);
        ArgumentCaptor<EamReceiptItemDO> receiptItemCaptor = ArgumentCaptor.forClass(EamReceiptItemDO.class);
        verify(receiptItemMapper).insert(receiptItemCaptor.capture());
        assertEquals(Map.of(), receiptItemCaptor.getValue().getActualExtFields());
        assertEquals(Map.of(), receiptItemCaptor.getValue().getActualExtFieldLabels());
        assertEquals(Map.of(), receiptItemCaptor.getValue().getActualExtFieldDictTypes());
    }

    @Test
    void receiveSerialized_shouldAllowMissingSerialsAndPersistNormalizedActualSnapshots() {
        EamPurchaseDO purchase = purchase(2);
        EamPurchaseItemDO item = item(EamManagementModeEnum.SERIALIZED.getMode(), 1);
        item.setExtFields(Map.of("color", "black"));
        item.setExtFieldLabels(Map.of("color", "黑色"));
        item.setExtFieldDictTypes(Map.of("color", "eam_asset_color"));
        EamCategoryFieldService.NormalizedExtFields normalized =
                new EamCategoryFieldService.NormalizedExtFields(item.getExtFields(), item.getExtFieldLabels(),
                        item.getExtFieldDictTypes());
        EamAssetDO asset = new EamAssetDO();
        asset.setId(60L);
        asset.setAssetCode("EAM-0001");
        when(purchaseMapper.selectByIdForUpdate(1L)).thenReturn(purchase);
        when(purchaseItemMapper.selectByIdForUpdate(10L)).thenReturn(item);
        when(categoryFieldService.validateAndNormalizeExtFieldsWithSnapshots(item.getCategoryId(), item.getExtFields()))
                .thenReturn(normalized);
        when(assetService.createAsset(any(EamAssetSaveReqVO.class))).thenReturn(60L);
        when(assetMapper.selectById(60L)).thenReturn(asset);
        when(sourceMapper.selectListByPurchaseItemId(10L)).thenReturn(List.of());
        when(purchaseItemMapper.selectListByPurchaseId(1L)).thenReturn(List.of(item));

        service.receive(1L, receiptRequest(10L, 1, null, null));

        ArgumentCaptor<EamAssetSaveReqVO> assetCaptor = ArgumentCaptor.forClass(EamAssetSaveReqVO.class);
        verify(assetService).createAsset(assetCaptor.capture());
        assertEquals(null, assetCaptor.getValue().getSn());
        assertEquals(item.getExtFields(), assetCaptor.getValue().getExtFields());
        ArgumentCaptor<EamReceiptItemDO> receiptCaptor = ArgumentCaptor.forClass(EamReceiptItemDO.class);
        verify(receiptItemMapper).insert(receiptCaptor.capture());
        assertEquals(List.of("EAM-0001"), receiptCaptor.getValue().getSerialNumbers());
        assertEquals(item.getExtFieldLabels(), receiptCaptor.getValue().getActualExtFieldLabels());
        assertEquals(item.getExtFieldDictTypes(), receiptCaptor.getValue().getActualExtFieldDictTypes());
    }

    @Test
    void shortClose_shouldCloseSourceGapAndCompletePurchase() {
        EamPurchaseDO purchase = purchase(STATUS_FULFILLING);
        EamPurchaseItemDO item = item(EamManagementModeEnum.BATCH.getMode(), 10);
        item.setReceivedQuantity(6);
        EamPurchaseItemDO resolved = item(EamManagementModeEnum.BATCH.getMode(), 10);
        resolved.setReceivedQuantity(6);
        resolved.setShortClosedQuantity(4);
        EamPurchaseSourceDO source = source(20L, 10, 6);
        when(purchaseMapper.selectByIdForUpdate(1L)).thenReturn(purchase);
        when(purchaseItemMapper.selectByIdForUpdate(10L)).thenReturn(item);
        when(sourceMapper.selectListByPurchaseItemId(10L)).thenReturn(List.of(source));
        when(purchaseItemMapper.selectListByPurchaseId(1L)).thenReturn(List.of(resolved));
        EamShortCloseReqVO request = new EamShortCloseReqVO();
        request.setPurchaseItemId(10L);
        request.setQuantity(4);
        request.setReason("供应商缺货");

        service.shortClose(1L, request);

        verify(demandService).addClosedQuantity(20L, 4);
        verify(purchaseMapper).updateById(argThat((EamPurchaseDO update) ->
                STATUS_COMPLETED == update.getStatus()));
    }

    @Test
    void supplierReturnBatch_shouldRequireOriginalReceiptBalance() {
        EamPurchaseDO purchase = purchase(STATUS_COMPLETED);
        purchase.setActualAmount(new BigDecimal("500"));
        EamPurchaseItemDO item = item(EamManagementModeEnum.BATCH.getMode(), 5);
        item.setReceivedQuantity(5);
        item.setUnitPrice(new BigDecimal("100"));
        EamStockBalanceDO balance = matchingBalance(item, 50L);
        EamReceiptDO inbound = new EamReceiptDO();
        inbound.setId(90L);
        inbound.setType(RECEIPT_INBOUND);
        EamReceiptItemDO inboundItem = new EamReceiptItemDO();
        inboundItem.setReceiptId(90L);
        inboundItem.setStockBalanceId(50L);
        inboundItem.setQuantity(5);
        when(purchaseMapper.selectByIdForUpdate(1L)).thenReturn(purchase);
        when(purchaseItemMapper.selectByIdForUpdate(10L)).thenReturn(item);
        when(stockService.getBalance(50L)).thenReturn(balance);
        when(receiptMapper.selectListByPurchaseId(1L)).thenReturn(List.of(inbound));
        when(receiptItemMapper.selectListByPurchaseItemId(10L)).thenReturn(List.of(inboundItem));

        service.returnToSupplier(1L, receiptRequest(10L, 2, 50L, null));

        verify(stockService).outbound(eq(50L), eq(2), eq("SUPPLIER_RETURN"), any(), any());
    }

    @Test
    void supplierReturnSerialized_shouldTraceIdentityAndRetireIdleAsset() {
        EamPurchaseDO purchase = purchase(STATUS_COMPLETED);
        EamPurchaseItemDO item = item(EamManagementModeEnum.SERIALIZED.getMode(), 1);
        item.setReceivedQuantity(1);
        EamReceiptDO inbound = new EamReceiptDO();
        inbound.setId(90L);
        inbound.setType(RECEIPT_INBOUND);
        EamReceiptItemDO inboundItem = new EamReceiptItemDO();
        inboundItem.setReceiptId(90L);
        inboundItem.setSerialNumbers(List.of("SN-1"));
        EamAssetDO asset = new EamAssetDO();
        asset.setId(60L);
        asset.setStatus(EamAssetStatusEnum.IDLE.getStatus());
        when(purchaseMapper.selectByIdForUpdate(1L)).thenReturn(purchase);
        when(purchaseItemMapper.selectByIdForUpdate(10L)).thenReturn(item);
        when(receiptMapper.selectListByPurchaseId(1L)).thenReturn(List.of(inbound));
        when(receiptItemMapper.selectListByPurchaseItemId(10L)).thenReturn(List.of(inboundItem));
        when(assetMapper.selectByIdentityAndCategoryId("SN-1", item.getCategoryId())).thenReturn(asset);

        service.returnToSupplier(1L, receiptRequest(10L, 1, null, List.of("SN-1")));

        verify(assetService).applyChange(eq(60L), eq(EamAssetStatusEnum.RETURNED_TO_SUPPLIER.getStatus()),
                eq(null), eq(null), anyInt(), any(), eq("供应商退货"));
    }

    private EamPurchaseDO purchase(int status) {
        EamPurchaseDO purchase = new EamPurchaseDO();
        purchase.setId(1L);
        purchase.setStatus(status);
        return purchase;
    }

    private EamPurchaseItemDO item(int managementMode, int quantity) {
        EamPurchaseItemDO item = new EamPurchaseItemDO();
        item.setId(10L);
        item.setPurchaseId(1L);
        item.setName("办公设备");
        item.setCategoryId(11L);
        item.setManagementMode(managementMode);
        item.setDeliveryMode(EamDeliveryModeEnum.PHYSICAL.getMode());
        item.setCustodyMode(EamCustodyModeEnum.CONSUMABLE.getMode());
        item.setUnit("个");
        item.setQuantity(quantity);
        item.setReceivedQuantity(0);
        item.setReturnedQuantity(0);
        item.setShortClosedQuantity(0);
        item.setExtFields(Map.of());
        item.setExtFieldLabels(Map.of());
        item.setExtFieldDictTypes(Map.of());
        return item;
    }

    private EamPurchaseSourceDO source(Long demandItemId, int quantity, int fulfilled) {
        EamPurchaseSourceDO source = new EamPurchaseSourceDO();
        source.setId(12L);
        source.setPurchaseItemId(10L);
        source.setDemandItemId(demandItemId);
        source.setQuantity(quantity);
        source.setFulfilledQuantity(fulfilled);
        source.setClosedQuantity(0);
        return source;
    }

    private EamStockBalanceDO matchingBalance(EamPurchaseItemDO item, Long id) {
        EamStockBalanceDO balance = new EamStockBalanceDO();
        balance.setId(id);
        balance.setCategoryId(item.getCategoryId());
        balance.setManagementMode(item.getManagementMode());
        balance.setDeliveryMode(item.getDeliveryMode());
        balance.setCustodyMode(item.getCustodyMode());
        balance.setUnit(item.getUnit());
        return balance;
    }

    private EamReceiptCreateReqVO receiptRequest(Long purchaseItemId, int quantity, Long balanceId,
                                                 List<String> serials) {
        EamReceiptItemReqVO item = new EamReceiptItemReqVO();
        item.setPurchaseItemId(purchaseItemId);
        item.setQuantity(quantity);
        item.setStockBalanceId(balanceId);
        item.setSerialNumbers(serials);
        EamReceiptCreateReqVO request = new EamReceiptCreateReqVO();
        request.setItems(List.of(item));
        return request;
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }

}
