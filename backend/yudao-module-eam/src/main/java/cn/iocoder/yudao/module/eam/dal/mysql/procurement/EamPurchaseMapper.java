package cn.iocoder.yudao.module.eam.dal.mysql.procurement;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamPurchaseDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamPurchaseMapper extends BaseMapperX<EamPurchaseDO> {
    default List<EamPurchaseDO> selectListOrderByIdDesc() {
        return selectList(new LambdaQueryWrapperX<EamPurchaseDO>().orderByDesc(EamPurchaseDO::getId));
    }
    default EamPurchaseDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamPurchaseDO>()
                .eq(EamPurchaseDO::getId, id).last("FOR UPDATE"));
    }
}
