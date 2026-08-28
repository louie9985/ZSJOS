package cn.iocoder.yudao.module.eam.service.stock;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockReserveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandItemDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockBalanceDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockHoldingDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockReservationDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockReminderDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.*;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamCustodyModeEnum;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.STOCK_CANDIDATE_INVALID;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.STOCK_INSUFFICIENT;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.RESERVATION_ACTIVE;
import static cn.iocoder.yudao.module.eam.enums.procurement.EamProcurementConstants.EXPIRY_FIELD_KEY;
import static cn.iocoder.yudao.module.eam.enums.category.EamDeliveryModeEnum.DIGITAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EamStockServiceImplTest {

    @InjectMocks
    private EamStockServiceImpl stockService;
    @Mock
    private EamStockBalanceMapper balanceMapper;
    @Mock
    private EamStockMovementMapper movementMapper;
    @Mock
    private EamStockReservationMapper reservationMapper;
    @Mock
    private EamAssetMapper assetMapper;
    @Mock
    private EamStockHoldingMapper holdingMapper;
    @Mock
    private EamAssetService assetService;
    @Mock
    private EamStockReminderMapper reminderMapper;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reserveBatch_shouldFailWhenAtomicAvailabilityUpdateLosesRace() {
        EamDemandItemDO item = batchItem();
        EamStockBalanceDO balance = matchingBalance(item);
        balance.setId(20L);
        balance.setAttributeSignature(DigestUtil.sha256Hex("{}"));
        when(balanceMapper.selectByIdForUpdate(20L)).thenReturn(balance);
        when(balanceMapper.reserve(20L, 3)).thenReturn(0);
        EamStockReserveReqVO request = new EamStockReserveReqVO();
        request.setDemandItemId(item.getId());
        request.setStockBalanceId(20L);
        request.setQuantity(3);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> stockService.reserve(item, 30L, request));

        assertEquals(STOCK_INSUFFICIENT.getCode(), exception.getCode());
        verify(reservationMapper, never()).insert(any(EamStockReservationDO.class));
    }

    @Test
    void getCandidates_batchShouldOnlyQueryStockBalances() {
        EamDemandItemDO item = batchItem();
        when(balanceMapper.selectAvailableCandidates(anyLong(), anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());

        stockService.getCandidates(item);

        verify(balanceMapper).selectAvailableCandidates(anyLong(), anyString(), anyString(), anyInt(), anyInt(), anyInt());
        verify(assetMapper, never()).selectIdleListByCategoryId(anyLong());
    }

    @Test
    void getCandidates_serializedShouldOnlyQueryIdleAssets() {
        EamDemandItemDO item = batchItem();
        item.setManagementMode(EamManagementModeEnum.SERIALIZED.getMode());
        when(assetMapper.selectIdleListByCategoryId(item.getCategoryId())).thenReturn(List.of());

        stockService.getCandidates(item);

        verify(assetMapper).selectIdleListByCategoryId(item.getCategoryId());
        verify(balanceMapper, never()).selectAvailableCandidates(anyLong(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt());
    }

    @Test
    void reserveBatch_shouldRejectBalanceWithDifferentPolicySnapshot() {
        EamDemandItemDO item = batchItem();
        EamStockBalanceDO balance = matchingBalance(item);
        balance.setId(20L);
        balance.setCustodyMode(EamCustodyModeEnum.RETURNABLE.getMode());
        balance.setAttributeSignature(DigestUtil.sha256Hex("{}"));
        when(balanceMapper.selectByIdForUpdate(20L)).thenReturn(balance);
        EamStockReserveReqVO request = new EamStockReserveReqVO();
        request.setDemandItemId(item.getId());
        request.setStockBalanceId(20L);
        request.setQuantity(1);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> stockService.reserve(item, 30L, request));

        assertEquals(STOCK_CANDIDATE_INVALID.getCode(), exception.getCode());
        verify(balanceMapper, never()).reserve(anyLong(), anyInt());
    }

    @Test
    void reserveSerialized_shouldRejectAssetWithOpenHolding() {
        EamDemandItemDO item = batchItem();
        item.setManagementMode(EamManagementModeEnum.SERIALIZED.getMode());
        EamAssetDO asset = new EamAssetDO();
        asset.setId(50L);
        asset.setCategoryId(item.getCategoryId());
        asset.setStatus(EamAssetStatusEnum.IDLE.getStatus());
        asset.setExtFields(Map.of());
        when(assetMapper.selectByIdForUpdate(50L)).thenReturn(asset);
        when(holdingMapper.selectOpenByAssetId(50L)).thenReturn(new EamStockHoldingDO());
        EamStockReserveReqVO request = new EamStockReserveReqVO();
        request.setDemandItemId(item.getId());
        request.setAssetId(50L);
        request.setQuantity(1);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> stockService.reserve(item, 30L, request));

        assertEquals(STOCK_CANDIDATE_INVALID.getCode(), exception.getCode());
        verify(reservationMapper, never()).insert(any(EamStockReservationDO.class));
    }

    @Test
    void allocateSerializedReturnable_shouldCreateHoldingAndRemoveAssetFromIdleStock() {
        EamDemandItemDO item = batchItem();
        item.setManagementMode(EamManagementModeEnum.SERIALIZED.getMode());
        item.setCustodyMode(EamCustodyModeEnum.RETURNABLE.getMode());
        EamStockReservationDO reservation = new EamStockReservationDO();
        reservation.setId(60L);
        reservation.setDemandItemId(item.getId());
        reservation.setAssetId(50L);
        reservation.setTargetEmployeeId(30L);
        reservation.setQuantity(1);
        reservation.setStatus(RESERVATION_ACTIVE);
        EamAssetDO asset = new EamAssetDO();
        asset.setId(50L);
        asset.setStatus(EamAssetStatusEnum.IDLE.getStatus());
        when(reservationMapper.selectByIdForUpdate(60L)).thenReturn(reservation);
        when(assetMapper.selectByIdForUpdate(50L)).thenReturn(asset);

        stockService.allocateReservation(60L, item, 70L);

        ArgumentCaptor<EamStockHoldingDO> holdingCaptor = ArgumentCaptor.forClass(EamStockHoldingDO.class);
        verify(holdingMapper).insert(holdingCaptor.capture());
        assertEquals(50L, holdingCaptor.getValue().getAssetId());
        assertEquals(30L, holdingCaptor.getValue().getEmployeeId());
        verify(assetService).applyChange(eq(50L), eq(EamAssetStatusEnum.IN_USE.getStatus()),
                eq(30L), eq(70L), anyInt(), eq(60L), anyString());
    }

    @Test
    void getOrCreateBalance_shouldKeepPolicySnapshotAndProjectDigitalExpiry() {
        LocalDate expiry = LocalDate.now().plusMonths(1);
        EamDemandItemDO item = batchItem();
        item.setDeliveryMode(DIGITAL.getMode());
        item.setExtFields(Map.of(EXPIRY_FIELD_KEY, expiry.toString()));
        item.setExtFieldLabels(Map.of());
        item.setExtFieldDictTypes(Map.of(EXPIRY_FIELD_KEY, "eam_subscription_expiry"));

        EamStockBalanceDO result = stockService.getOrCreateBalance(item);

        assertEquals(item.getManagementMode(), result.getManagementMode());
        assertEquals(item.getCustodyMode(), result.getCustodyMode());
        assertEquals(DIGITAL.getMode(), result.getDeliveryMode());
        assertEquals(expiry, result.getNextExpiryDate());
        assertEquals(item.getExtFieldDictTypes(), result.getExtFieldDictTypes());
        verify(balanceMapper).selectBySignature(eq(item.getCategoryId()), eq(item.getUnit()), anyString(),
                eq(item.getManagementMode()), eq(item.getDeliveryMode()), eq(item.getCustodyMode()));
        verify(balanceMapper).insert(any(EamStockBalanceDO.class));
    }

    @Test
    void createReminderProjections_shouldSeparateAssetAndBalanceWithSameBusinessId() {
        LocalDate expiry = LocalDate.now().plusDays(10);
        EamStockBalanceDO balance = new EamStockBalanceDO();
        balance.setId(90L);
        balance.setName("共享席位");
        balance.setNextExpiryDate(expiry);
        EamAssetDO asset = new EamAssetDO();
        asset.setId(90L);
        asset.setName("数字账号");
        asset.setExtFields(Map.of(EXPIRY_FIELD_KEY, expiry.toString()));
        when(balanceMapper.selectExpiringList(any())).thenReturn(List.of(balance));
        when(assetMapper.selectExpiringList(any())).thenReturn(List.of(asset));

        int created = stockService.createReminderProjections(30);

        assertEquals(2, created);
        verify(reminderMapper).selectByKey("DIGITAL_EXPIRY", "STOCK_BALANCE", 90L, LocalDate.now());
        verify(reminderMapper).selectByKey("DIGITAL_EXPIRY", "ASSET", 90L, LocalDate.now());
        ArgumentCaptor<EamStockReminderDO> captor = ArgumentCaptor.forClass(EamStockReminderDO.class);
        verify(reminderMapper, times(2)).insert(captor.capture());
        assertEquals(List.of("STOCK_BALANCE", "ASSET"),
                captor.getAllValues().stream().map(EamStockReminderDO::getBusinessType).toList());
    }

    private EamDemandItemDO batchItem() {
        EamDemandItemDO item = new EamDemandItemDO();
        item.setId(10L);
        item.setName("办公设备");
        item.setCategoryId(11L);
        item.setManagementMode(EamManagementModeEnum.BATCH.getMode());
        item.setCustodyMode(EamCustodyModeEnum.CONSUMABLE.getMode());
        item.setDeliveryMode(cn.iocoder.yudao.module.eam.enums.category.EamDeliveryModeEnum.PHYSICAL.getMode());
        item.setUnit("个");
        item.setExtFields(Map.of());
        item.setExtFieldLabels(Map.of());
        item.setExtFieldDictTypes(Map.of());
        return item;
    }

    private EamStockBalanceDO matchingBalance(EamDemandItemDO item) {
        EamStockBalanceDO balance = new EamStockBalanceDO();
        balance.setCategoryId(item.getCategoryId());
        balance.setUnit(item.getUnit());
        balance.setManagementMode(item.getManagementMode());
        balance.setDeliveryMode(item.getDeliveryMode());
        balance.setCustodyMode(item.getCustodyMode());
        return balance;
    }
}
