package cn.iocoder.yudao.module.eam.dal.mysql.inventory;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.inventory.EamInventoryDetailDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamInventoryDetailMapper extends BaseMapperX<EamInventoryDetailDO> {

    default List<EamInventoryDetailDO> selectListByInventoryId(Long inventoryId) {
        return selectList(new LambdaQueryWrapperX<EamInventoryDetailDO>()
                .eq(EamInventoryDetailDO::getInventoryId, inventoryId)
                .orderByAsc(EamInventoryDetailDO::getId));
    }

    default Long selectCountByInventoryIdAndResult(Long inventoryId, Integer result) {
        return selectCount(new LambdaQueryWrapperX<EamInventoryDetailDO>()
                .eq(EamInventoryDetailDO::getInventoryId, inventoryId)
                .eq(EamInventoryDetailDO::getResult, result));
    }

}
