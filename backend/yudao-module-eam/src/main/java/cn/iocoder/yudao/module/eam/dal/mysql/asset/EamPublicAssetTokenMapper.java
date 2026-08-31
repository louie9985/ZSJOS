package cn.iocoder.yudao.module.eam.dal.mysql.asset;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamPublicAssetTokenDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EamPublicAssetTokenMapper extends BaseMapperX<EamPublicAssetTokenDO> {
    default EamPublicAssetTokenDO selectByTokenHash(String hash) {
        return selectOne(new LambdaQueryWrapperX<EamPublicAssetTokenDO>().eq(EamPublicAssetTokenDO::getTokenHash, hash)
                .eq(EamPublicAssetTokenDO::getStatus, 1));
    }
    default EamPublicAssetTokenDO selectByAssetId(Long assetId) {
        return selectOne(new LambdaQueryWrapperX<EamPublicAssetTokenDO>()
                .eq(EamPublicAssetTokenDO::getAssetId, assetId)
                .orderByDesc(EamPublicAssetTokenDO::getId)
                .last("LIMIT 1 FOR UPDATE"));
    }
}
