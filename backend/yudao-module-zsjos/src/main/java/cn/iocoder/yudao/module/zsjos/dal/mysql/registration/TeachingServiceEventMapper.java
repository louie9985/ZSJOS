package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.TeachingServiceEventDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface TeachingServiceEventMapper extends BaseMapperX<TeachingServiceEventDO> {
    default List<TeachingServiceEventDO> latestClosures(List<Long> relationIds) {
        if (relationIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<TeachingServiceEventDO>()
            .select(TeachingServiceEventDO::getServiceRelationId, TeachingServiceEventDO::getReason)
            .in(TeachingServiceEventDO::getServiceRelationId, relationIds)
            .eq(TeachingServiceEventDO::getAction, "close")
            .apply("NOT EXISTS (SELECT 1 FROM zsjos_teaching_service_event newer WHERE newer.service_relation_id=zsjos_teaching_service_event.service_relation_id AND newer.tenant_id=zsjos_teaching_service_event.tenant_id AND newer.deleted=0 AND newer.action='close' AND newer.id>zsjos_teaching_service_event.id)"));
    }
    default TeachingServiceEventDO byKey(String key) { return selectOne(TeachingServiceEventDO::getIdempotencyKey,key); }
    default List<TeachingServiceEventDO> history(Long relationId) {
        return selectList(new LambdaQueryWrapperX<TeachingServiceEventDO>()
            .eq(TeachingServiceEventDO::getServiceRelationId,relationId).orderByDesc(TeachingServiceEventDO::getId));
    }
}
