package cn.iocoder.yudao.module.zsjos.dal.mysql.config;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.config.MediaConfigVersionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaConfigVersionMapper extends BaseMapperX<MediaConfigVersionDO> {
    default MediaConfigVersionDO selectPublished() {
        return selectOne(new LambdaQueryWrapperX<MediaConfigVersionDO>()
                .eq(MediaConfigVersionDO::getStatus, "published").orderByDesc(MediaConfigVersionDO::getVersionNo)
                .last("LIMIT 1"));
    }
}
