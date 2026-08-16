package cn.iocoder.yudao.module.eam.service.repair;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairFinishReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.repair.EamRepairDO;
import cn.iocoder.yudao.module.eam.dal.mysql.repair.EamRepairMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.REPAIR_FINISHED;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.REPAIR_NOT_EXISTS;

/**
 * EAM 维修记录 Service 实现类
 */
@Service
@Validated
public class EamRepairServiceImpl implements EamRepairService {

    @Resource
    private EamRepairMapper repairMapper;
    @Resource
    private EamAssetService assetService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRepair(EamRepairCreateReqVO reqVO) {
        EamAssetDO asset = assetService.validateAssetExists(reqVO.getAssetId());
        assetService.validateStatusTransition(asset, EamAssetStatusEnum.ALLOW_REPAIR);

        EamRepairDO repair = BeanUtils.toBean(reqVO, EamRepairDO.class);
        if (repair.getStartTime() == null) {
            repair.setStartTime(LocalDateTime.now());
        }
        repairMapper.insert(repair);

        // 置为维修中；applyChange 内部会把送修前状态记入 previousStatus 供完成时恢复
        assetService.applyChange(reqVO.getAssetId(), EamAssetStatusEnum.REPAIRING.getStatus(),
                null, null, EamChangeTypeEnum.REPAIR.getType(), repair.getId(),
                StrUtil.format("送修：{}", reqVO.getFaultDesc()));
        return repair.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishRepair(EamRepairFinishReqVO reqVO) {
        EamRepairDO repair = validateRepairExists(reqVO.getId());
        if (repair.getEndTime() != null) {
            throw exception(REPAIR_FINISHED);
        }
        EamAssetDO asset = assetService.validateAssetExists(repair.getAssetId());
        assetService.validateStatusTransition(asset, EamAssetStatusEnum.ALLOW_REPAIR_DONE);

        repairMapper.updateById(new EamRepairDO()
                .setId(reqVO.getId())
                .setEndTime(reqVO.getEndTime() != null ? reqVO.getEndTime() : LocalDateTime.now())
                .setCost(reqVO.getCost())
                .setResult(reqVO.getResult()));

        // 恢复送修前状态；缺失记录时退回闲置，避免资产卡在维修中
        Integer restored = asset.getPreviousStatus() != null
                ? asset.getPreviousStatus() : EamAssetStatusEnum.IDLE.getStatus();
        assetService.applyChange(repair.getAssetId(), restored, null, null,
                EamChangeTypeEnum.REPAIR_DONE.getType(), repair.getId(),
                StrUtil.format("维修完成：{}", StrUtil.blankToDefault(reqVO.getResult(), "已修复")));
    }

    @Override
    public void deleteRepair(Long id) {
        validateRepairExists(id);
        repairMapper.deleteById(id);
    }

    @Override
    public EamRepairDO getRepair(Long id) {
        return repairMapper.selectById(id);
    }

    @Override
    public List<EamRepairDO> getRepairListByAssetId(Long assetId) {
        return repairMapper.selectListByAssetId(assetId);
    }

    @Override
    public PageResult<EamRepairDO> getRepairPage(EamRepairPageReqVO reqVO) {
        return repairMapper.selectPage(reqVO);
    }

    private EamRepairDO validateRepairExists(Long id) {
        EamRepairDO repair = repairMapper.selectById(id);
        if (repair == null) {
            throw exception(REPAIR_NOT_EXISTS);
        }
        return repair;
    }

}
