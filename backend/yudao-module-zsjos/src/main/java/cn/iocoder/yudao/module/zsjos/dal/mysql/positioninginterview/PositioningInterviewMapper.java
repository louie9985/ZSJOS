package cn.iocoder.yudao.module.zsjos.dal.mysql.positioninginterview;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioninginterview.PositioningInterviewDO;
import org.apache.ibatis.annotations.*;
@Mapper public interface PositioningInterviewMapper extends BaseMapperX<PositioningInterviewDO> {
 @Select("SELECT id FROM zsjos_person WHERE id = #{personId} AND tenant_id = #{tenantId} AND deleted = 0 FOR UPDATE")
 Long lockStudent(@Param("personId") Long personId,@Param("tenantId") Long tenantId);
 default boolean existsCompleted(Long relationId, Long personId) {
  return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PositioningInterviewDO>()
   .eq(PositioningInterviewDO::getServiceRelationId,relationId)
   .eq(PositioningInterviewDO::getStudentPersonId,personId).eq(PositioningInterviewDO::getStatus,"completed")) > 0;
 }
}
