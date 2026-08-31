package cn.iocoder.yudao.module.zsjos.dal.mysql.personnel;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PersonnelStateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PersonnelStateMapper extends BaseMapperX<PersonnelStateDO> {
    default PersonnelStateDO selectByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapperX<PersonnelStateDO>()
                .eq(PersonnelStateDO::getSystemUserId, userId).last("LIMIT 1"));
    }

    default List<PersonnelStateDO> selectByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<PersonnelStateDO>()
                .in(PersonnelStateDO::getSystemUserId, userIds));
    }
}
