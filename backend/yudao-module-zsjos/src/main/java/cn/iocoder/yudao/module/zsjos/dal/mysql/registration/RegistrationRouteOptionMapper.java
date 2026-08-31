package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationRouteOptionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RegistrationRouteOptionMapper extends BaseMapperX<RegistrationRouteOptionDO> {
    default List<RegistrationRouteOptionDO> selectByVersionId(Long versionId) {
        return selectList(new LambdaQueryWrapperX<RegistrationRouteOptionDO>()
                .eq(RegistrationRouteOptionDO::getVersionId, versionId)
                .orderByAsc(RegistrationRouteOptionDO::getSort).orderByAsc(RegistrationRouteOptionDO::getId));
    }

    default void deleteByVersionId(Long versionId) {
        delete(new LambdaQueryWrapperX<RegistrationRouteOptionDO>().eq(RegistrationRouteOptionDO::getVersionId, versionId));
    }
}
