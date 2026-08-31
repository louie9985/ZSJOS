package cn.iocoder.yudao.module.zsjos.dal.mysql.director;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DirectorConfigMapper extends BaseMapperX<DirectorConfigDO> {
    default DirectorConfigDO selectCurrent() { return selectOne(DirectorConfigDO::getDeleted, false); }
}
