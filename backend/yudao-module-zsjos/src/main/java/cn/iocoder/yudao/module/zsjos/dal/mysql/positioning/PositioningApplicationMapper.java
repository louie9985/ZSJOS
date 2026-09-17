package cn.iocoder.yudao.module.zsjos.dal.mysql.positioning;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningApplicationDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface PositioningApplicationMapper extends BaseMapperX<PositioningApplicationDO> {
    default PositioningApplicationDO find(Long id) { return selectOne(PositioningApplicationDO::getAccountId, id); }
}
