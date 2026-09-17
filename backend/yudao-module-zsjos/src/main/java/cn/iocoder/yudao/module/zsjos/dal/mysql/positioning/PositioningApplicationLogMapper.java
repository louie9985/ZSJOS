package cn.iocoder.yudao.module.zsjos.dal.mysql.positioning;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningApplicationLogDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface PositioningApplicationLogMapper extends BaseMapperX<PositioningApplicationLogDO> {
    default PositioningApplicationLogDO find(String key) { return selectOne(PositioningApplicationLogDO::getIdempotencyKey, key); }
}
