package cn.iocoder.yudao.module.zsjos.dal.mysql.positioning;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningServiceCardDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface PositioningServiceCardMapper extends BaseMapperX<PositioningServiceCardDO> {
    default PositioningServiceCardDO find(Long id) { return selectOne(PositioningServiceCardDO::getServiceRelationId, id); }
}
