package cn.iocoder.yudao.module.eam.dal.mysql.asset;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamPublicEditCodeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EamPublicEditCodeMapper extends BaseMapperX<EamPublicEditCodeDO> {
    default EamPublicEditCodeDO selectByEmployeeId(Long employeeId) {
        return selectOne(new LambdaQueryWrapperX<EamPublicEditCodeDO>().eq(EamPublicEditCodeDO::getEmployeeId, employeeId));
    }

    default EamPublicEditCodeDO selectByCodeHmac(String codeHmac) {
        return selectOne(new LambdaQueryWrapperX<EamPublicEditCodeDO>()
                .eq(EamPublicEditCodeDO::getCodeHmac, codeHmac)
                .eq(EamPublicEditCodeDO::getStatus, 1));
    }
}
