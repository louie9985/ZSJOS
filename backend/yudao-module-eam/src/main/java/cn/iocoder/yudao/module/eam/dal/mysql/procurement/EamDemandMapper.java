package cn.iocoder.yudao.module.eam.dal.mysql.procurement;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamDemandMapper extends BaseMapperX<EamDemandDO> {
    default List<EamDemandDO> selectListByApplicantUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<EamDemandDO>()
                .eq(EamDemandDO::getApplicantUserId, userId).orderByDesc(EamDemandDO::getId));
    }
    default EamDemandDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<EamDemandDO>().eq(EamDemandDO::getId, id).last("FOR UPDATE"));
    }
    default List<EamDemandDO> selectListOrderByIdDesc() {
        return selectList(new LambdaQueryWrapperX<EamDemandDO>().orderByDesc(EamDemandDO::getId));
    }
}
