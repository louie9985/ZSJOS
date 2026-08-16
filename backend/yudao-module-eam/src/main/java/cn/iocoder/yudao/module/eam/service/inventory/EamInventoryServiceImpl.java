package cn.iocoder.yudao.module.eam.service.inventory;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryCheckReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.inventory.vo.EamInventoryPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.inventory.EamInventoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.inventory.EamInventoryDetailDO;
import cn.iocoder.yudao.module.eam.dal.mysql.inventory.EamInventoryDetailMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.inventory.EamInventoryMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.enums.inventory.EamInventoryResultEnum;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.*;

/**
 * EAM 盘点 Service 实现类
 */
@Service
@Validated
public class EamInventoryServiceImpl implements EamInventoryService {

    private static final Integer STATUS_IN_PROGRESS = 0;
    private static final Integer STATUS_FINISHED = 1;

    @Resource
    private EamInventoryMapper inventoryMapper;
    @Resource
    private EamInventoryDetailMapper detailMapper;
    @Resource
    private EamAssetService assetService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInventory(EamInventoryCreateReqVO reqVO) {
        List<EamAssetDO> assets =
                assetService.getAssetListByScope(reqVO.getScopeType(), reqVO.getScopeValue());
        if (assets.isEmpty()) {
            throw exception(INVENTORY_SCOPE_EMPTY);
        }

        EamInventoryDO inventory = EamInventoryDO.builder()
                .no(StrUtil.format("IV-{}", System.currentTimeMillis()))
                .name(reqVO.getName())
                .scopeType(reqVO.getScopeType())
                .scopeValue(reqVO.getScopeValue())
                .status(STATUS_IN_PROGRESS)
                .totalCount(assets.size())
                .checkedCount(0)
                .normalCount(0)
                .abnormalCount(0)
                .startTime(LocalDateTime.now())
                .remark(reqVO.getRemark())
                .build();
        inventoryMapper.insert(inventory);

        // 固化账面归属作为比对基准
        List<EamInventoryDetailDO> details = assets.stream()
                .map(asset -> EamInventoryDetailDO.builder()
                        .inventoryId(inventory.getId())
                        .assetId(asset.getId())
                        .expectUserId(asset.getUseUserId())
                        .expectDeptId(asset.getUseDeptId())
                        .expectLocation(asset.getLocation())
                        .result(EamInventoryResultEnum.UNCHECKED.getResult())
                        .build())
                .toList();
        detailMapper.insertBatch(details);
        return inventory.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkDetail(EamInventoryCheckReqVO reqVO) {
        EamInventoryDetailDO detail = detailMapper.selectById(reqVO.getDetailId());
        if (detail == null) {
            throw exception(INVENTORY_DETAIL_NOT_EXISTS);
        }
        EamInventoryDO inventory = validateInventoryExists(detail.getInventoryId());
        if (Objects.equals(inventory.getStatus(), STATUS_FINISHED)) {
            throw exception(INVENTORY_FINISHED);
        }

        detailMapper.updateById(EamInventoryDetailDO.builder()
                .id(reqVO.getDetailId())
                .result(reqVO.getResult())
                .actualUserId(reqVO.getActualUserId())
                .actualDeptId(reqVO.getActualDeptId())
                .actualLocation(reqVO.getActualLocation())
                .remark(reqVO.getRemark())
                .checkUserId(SecurityFrameworkUtils.getLoginUserId())
                .checkTime(LocalDateTime.now())
                .build());

        refreshStatistics(detail.getInventoryId());
    }

    @Override
    public void finishInventory(Long id) {
        EamInventoryDO inventory = validateInventoryExists(id);
        if (Objects.equals(inventory.getStatus(), STATUS_FINISHED)) {
            throw exception(INVENTORY_FINISHED);
        }
        inventoryMapper.updateById(EamInventoryDO.builder()
                .id(id)
                .status(STATUS_FINISHED)
                .endTime(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInventory(Long id) {
        validateInventoryExists(id);
        detailMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EamInventoryDetailDO>()
                .eq(EamInventoryDetailDO::getInventoryId, id));
        inventoryMapper.deleteById(id);
    }

    @Override
    public EamInventoryDO getInventory(Long id) {
        return inventoryMapper.selectById(id);
    }

    @Override
    public PageResult<EamInventoryDO> getInventoryPage(EamInventoryPageReqVO reqVO) {
        return inventoryMapper.selectPage(reqVO);
    }

    @Override
    public List<EamInventoryDetailDO> getDetailListByInventoryId(Long inventoryId) {
        return detailMapper.selectListByInventoryId(inventoryId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncDetailToAsset(Long detailId) {
        EamInventoryDetailDO detail = detailMapper.selectById(detailId);
        if (detail == null) {
            throw exception(INVENTORY_DETAIL_NOT_EXISTS);
        }
        // 只同步归属，状态不因盘点而改变
        assetService.applyChange(detail.getAssetId(), null,
                detail.getActualUserId(), detail.getActualDeptId(),
                EamChangeTypeEnum.INVENTORY.getType(), detail.getInventoryId(),
                StrUtil.format("盘点同步归属，存放地点 {}",
                        StrUtil.blankToDefault(detail.getActualLocation(), "未填写")));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDetailAssetLost(Long detailId) {
        EamInventoryDetailDO detail = detailMapper.selectById(detailId);
        if (detail == null) {
            throw exception(INVENTORY_DETAIL_NOT_EXISTS);
        }
        assetService.markLost(detail.getAssetId(), detail.getInventoryId(),
                StrUtil.blankToDefault(detail.getRemark(), "盘点未找到，标记丢失"));
    }

    /**
     * 依据明细重算盘点单的汇总计数
     */
    private void refreshStatistics(Long inventoryId) {
        long unchecked = detailMapper.selectCountByInventoryIdAndResult(
                inventoryId, EamInventoryResultEnum.UNCHECKED.getResult());
        long normal = detailMapper.selectCountByInventoryIdAndResult(
                inventoryId, EamInventoryResultEnum.NORMAL.getResult());
        long mismatch = detailMapper.selectCountByInventoryIdAndResult(
                inventoryId, EamInventoryResultEnum.LOCATION_MISMATCH.getResult());
        long notFound = detailMapper.selectCountByInventoryIdAndResult(
                inventoryId, EamInventoryResultEnum.NOT_FOUND.getResult());

        inventoryMapper.updateById(EamInventoryDO.builder()
                .id(inventoryId)
                .checkedCount((int) (normal + mismatch + notFound))
                .normalCount((int) normal)
                .abnormalCount((int) (mismatch + notFound))
                .build());
    }

    private EamInventoryDO validateInventoryExists(Long id) {
        EamInventoryDO inventory = inventoryMapper.selectById(id);
        if (inventory == null) {
            throw exception(INVENTORY_NOT_EXISTS);
        }
        return inventory;
    }

}
