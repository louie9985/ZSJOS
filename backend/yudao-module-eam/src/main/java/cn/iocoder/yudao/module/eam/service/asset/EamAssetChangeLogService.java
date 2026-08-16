package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetChangeLogDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;

import java.util.List;

/**
 * EAM 资产变更记录 Service 接口
 */
public interface EamAssetChangeLogService {

    /**
     * 记录一次资产变更
     *
     * 由调用方在改动资产的同一事务内调用，保证台账与流水一致。
     *
     * @param before     变更前资产快照，创建场景传 null
     * @param after      变更后资产
     * @param changeType 变更类型，见 EamChangeTypeEnum
     * @param bizId      关联单据编号，可空
     * @param content    变更描述
     */
    void record(EamAssetDO before, EamAssetDO after, Integer changeType, Long bizId, String content);

    /**
     * 获得资产的变更时间线
     */
    List<EamAssetChangeLogDO> getChangeLogListByAssetId(Long assetId);

}
