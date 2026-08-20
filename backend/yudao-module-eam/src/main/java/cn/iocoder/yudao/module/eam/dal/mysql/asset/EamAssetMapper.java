package cn.iocoder.yudao.module.eam.dal.mysql.asset;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EamAssetMapper extends BaseMapperX<EamAssetDO> {

    default PageResult<EamAssetDO> selectPage(EamAssetPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EamAssetDO>()
                .likeIfPresent(EamAssetDO::getName, reqVO.getName())
                .likeIfPresent(EamAssetDO::getAssetCode, reqVO.getAssetCode())
                .eqIfPresent(EamAssetDO::getCategoryId, reqVO.getCategoryId())
                .eqIfPresent(EamAssetDO::getStatus, reqVO.getStatus())
                .eqIfPresent(EamAssetDO::getUseDeptId, reqVO.getUseDeptId())
                .eqIfPresent(EamAssetDO::getUseUserId, reqVO.getUseUserId())
                .orderByDesc(EamAssetDO::getId));
    }

    default EamAssetDO selectByAssetCode(String assetCode) {
        return selectOne(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getAssetCode, assetCode));
    }

    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(new LambdaQueryWrapperX<EamAssetDO>()
                .eq(EamAssetDO::getCategoryId, categoryId));
    }

    default List<EamAssetDO> selectListByAssetCodes(Collection<String> assetCodes) {
        if (assetCodes == null || assetCodes.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<EamAssetDO>()
                .in(EamAssetDO::getAssetCode, assetCodes));
    }

}
