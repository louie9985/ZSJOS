package cn.iocoder.yudao.module.eam.dal.mysql.asset;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetChangeLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamAssetChangeLogMapper extends BaseMapperX<EamAssetChangeLogDO> {

    default List<EamAssetChangeLogDO> selectListByAssetId(Long assetId) {
        return selectList(new LambdaQueryWrapperX<EamAssetChangeLogDO>()
                .eq(EamAssetChangeLogDO::getAssetId, assetId)
                .orderByDesc(EamAssetChangeLogDO::getOperateTime)
                .orderByDesc(EamAssetChangeLogDO::getId));
    }

}
