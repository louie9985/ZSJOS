package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockHoldingDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockHoldingMapper;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_PUBLIC_CLEAR_USAGE_HOLDING_ACTIVE;
import static cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum.IDLE;
import static cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum.IN_USE;
import static cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum.REPAIRING;
import static cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum.RETURN;
import static cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum.EDIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EamAssetServiceImplTest {

    @InjectMocks
    private EamAssetServiceImpl service;
    @Mock
    private EamAssetMapper assetMapper;
    @Mock
    private EamStockHoldingMapper stockHoldingMapper;
    @Mock
    private EamAssetChangeLogService changeLogService;
    @Mock
    private EamCategoryService categoryService;
    @Mock
    private EamCategoryFieldService categoryFieldService;
    @Mock
    private HrmEmployeeApi employeeApi;
    @Mock
    private DictDataApi dictDataApi;

    @Test
    void publicUpdateShouldUseAtomicVersionAndExplicitOperator() {
        EamAssetDO before = asset(10L, IN_USE.getStatus(), 3);
        EamAssetDO after = asset(10L, IN_USE.getStatus(), 4).setName("新名称");
        EamCategoryDO category = new EamCategoryDO().setId(20L);
        EamAssetSaveReqVO request = new EamAssetSaveReqVO();
        request.setId(10L);
        request.setVersion(3);
        request.setName("新名称");
        request.setCategoryId(20L);
        when(assetMapper.selectById(10L)).thenReturn(before, after);
        when(categoryService.validateCategoryExists(20L)).thenReturn(category);
        when(categoryFieldService.validateAndNormalizeExtFieldsWithSnapshots(
                eq(20L), any(), any(), any(), any()))
                .thenReturn(new EamCategoryFieldService.NormalizedExtFields(
                        java.util.Map.of(), java.util.Map.of(), java.util.Map.of()));
        when(assetMapper.updateByIdAndVersion(any(EamAssetDO.class), eq(3))).thenReturn(1);

        service.updateAsset(request, 99L);

        verify(assetMapper).updateByIdAndVersion(argThat(update -> "新名称".equals(update.getName())
                && Integer.valueOf(4).equals(update.getVersion()) && "99".equals(update.getUpdater())), eq(3));
        verify(changeLogService).record(before, after, EDIT.getType(), null,
                "公开页面编辑资产信息", 99L);
    }

    @Test
    void clearUsageAndSetIdleShouldClearAssignmentAndRecordExplicitOperator() {
        EamAssetDO before = asset(10L, IN_USE.getStatus(), 3);
        EamAssetDO after = asset(10L, IDLE.getStatus(), 4)
                .setUseEmployeeId(null).setUseDeptId(null).setUseEmployeeNameSnapshot(null);
        when(assetMapper.selectById(10L)).thenReturn(before, after);
        when(assetMapper.clearUsageAndSetIdle(10L, 3, 4, "99")).thenReturn(1);

        service.clearUsageAndSetIdle(10L, 3, 99L);

        verify(assetMapper).clearUsageAndSetIdle(10L, 3, 4, "99");
        verify(changeLogService).record(before, after, RETURN.getType(), null,
                "公开页面清除使用归属并置为闲置", 99L);
    }

    @Test
    void clearUsageAndSetIdleShouldRejectOpenHolding() {
        EamAssetDO before = asset(10L, IN_USE.getStatus(), 3);
        when(assetMapper.selectById(10L)).thenReturn(before);
        when(stockHoldingMapper.selectOpenByAssetId(10L)).thenReturn(new EamStockHoldingDO().setId(20L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.clearUsageAndSetIdle(10L, 3, 99L));

        assertEquals(ASSET_PUBLIC_CLEAR_USAGE_HOLDING_ACTIVE.getCode(), error.getCode());
        verify(assetMapper, never()).clearUsageAndSetIdle(anyLong(), any(), anyInt(), anyString());
        verifyNoInteractions(changeLogService);
    }

    @Test
    void clearUsageAndSetIdleShouldRejectDisallowedStatus() {
        EamAssetDO before = asset(10L, REPAIRING.getStatus(), 3);
        when(assetMapper.selectById(10L)).thenReturn(before);

        assertThrows(ServiceException.class, () -> service.clearUsageAndSetIdle(10L, 3, 99L));

        verifyNoInteractions(stockHoldingMapper, changeLogService);
        verify(assetMapper, never()).clearUsageAndSetIdle(anyLong(), any(), anyInt(), anyString());
    }

    private EamAssetDO asset(Long id, Integer status, Integer version) {
        return new EamAssetDO().setId(id).setAssetCode("EAM-001").setStatus(status).setVersion(version)
                .setUseEmployeeId(30L).setUseDeptId(40L).setUseEmployeeNameSnapshot("员工甲");
    }

}
