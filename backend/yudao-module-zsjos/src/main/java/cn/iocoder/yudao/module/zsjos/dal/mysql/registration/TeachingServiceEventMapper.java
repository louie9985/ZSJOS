package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.TeachingServiceEventDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface TeachingServiceEventMapper extends BaseMapperX<TeachingServiceEventDO> {
    default TeachingServiceEventDO byKey(String key) { return selectOne(TeachingServiceEventDO::getIdempotencyKey,key); }
    default List<TeachingServiceEventDO> history(Long relationId) {
        return selectList(new LambdaQueryWrapperX<TeachingServiceEventDO>()
            .eq(TeachingServiceEventDO::getServiceRelationId,relationId).orderByDesc(TeachingServiceEventDO::getId));
    }
}
