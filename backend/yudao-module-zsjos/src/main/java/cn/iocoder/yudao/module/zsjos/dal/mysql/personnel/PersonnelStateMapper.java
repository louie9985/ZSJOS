package cn.iocoder.yudao.module.zsjos.dal.mysql.personnel;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PersonnelStateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PersonnelStateMapper extends BaseMapperX<PersonnelStateDO> {
    default PersonnelStateDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<PersonnelStateDO>()
                .eq(PersonnelStateDO::getSystemUserId, userId).last("LIMIT 1"));
    }
}
