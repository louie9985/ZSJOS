package cn.iocoder.yudao.module.eam.dal.mysql.stock;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.stock.EamStockMovementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EamStockMovementMapper extends BaseMapperX<EamStockMovementDO> {
}
