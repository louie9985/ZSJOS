package cn.iocoder.yudao.module.eam.dal.mysql.procurement;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EamDemandItemMapper extends BaseMapperX<EamDemandItemDO> {
    default List<EamDemandItemDO> selectListByDemandId(Long demandId) {
        return selectList(new LambdaQueryWrapperX<EamDemandItemDO>()
                .eq(EamDemandItemDO::getDemandId, demandId).orderByAsc(EamDemandItemDO::getId));
    }
    default List<EamDemandItemDO> selectListByIds(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : selectByIds(ids);
    }
    default EamDemandItemDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamDemandItemDO>()
                .eq(EamDemandItemDO::getId, id).last("FOR UPDATE"));
    }
}
