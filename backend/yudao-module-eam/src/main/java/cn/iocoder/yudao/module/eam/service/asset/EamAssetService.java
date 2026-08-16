package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetImportExcelVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetImportRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * EAM 资产 Service 接口
 */
public interface EamAssetService {

    Long createAsset(@Valid EamAssetSaveReqVO reqVO);

    void updateAsset(@Valid EamAssetSaveReqVO reqVO);

    void deleteAsset(Long id);

    EamAssetDO getAsset(Long id);

    List<EamAssetDO> getAssetList(Collection<Long> ids);

    PageResult<EamAssetDO> getAssetPage(EamAssetPageReqVO reqVO);

    EamAssetDO validateAssetExists(Long id);

    /**
     * 校验资产当前状态是否在允许集合内，不在则抛业务异常
     *
     * @param asset          资产
     * @param allowedStatuses 允许的起始状态
     */
    void validateStatusTransition(EamAssetDO asset, Set<Integer> allowedStatuses);

    /**
     * 应用一次状态与归属变更，并写入变更记录
     *
     * 供流转、维修、报废、盘点等场景复用；调用方需自带事务。
     *
     * @param assetId    资产编号
     * @param newStatus  新状态，null 表示不变
     * @param newUserId  新使用人，null 表示不变
     * @param newDeptId  新使用部门，null 表示不变
     * @param changeType 变更类型
     * @param bizId      关联单据编号
     * @param content    变更描述
     */
    void applyChange(Long assetId, Integer newStatus, Long newUserId, Long newDeptId,
                     Integer changeType, Long bizId, String content);

    /**
     * 标记资产丢失（盘点未找到时使用）
     */
    void markLost(Long assetId, Long inventoryId, String remark);

    /**
     * 冻结 / 解冻
     */
    void freeze(Long assetId, String reason);

    void unfreeze(Long assetId, String reason);

    /**
     * 按盘点范围查询资产编号列表
     *
     * @param scopeType  1 全部 / 2 按部门 / 3 按分类 / 4 按存放地点
     * @param scopeValue 范围值
     */
    List<EamAssetDO> getAssetListByScope(Integer scopeType, String scopeValue);

    /**
     * 生成资产二维码内容（承载资产业务编号，扫码端据此查询）
     */
    String buildQrContent(Long assetId);

    /**
     * 批量导入资产
     *
     * 逐行独立处理：单行失败只记录原因并继续，不回滚已成功的行，
     * 避免一条脏数据让整批导入白做。
     *
     * @param list 导入行
     * @return 成功编号与失败原因
     */
    EamAssetImportRespVO importAssetList(List<EamAssetImportExcelVO> list);

}
