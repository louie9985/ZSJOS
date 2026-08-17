package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ServiceRelationMapper extends BaseMapperX<ServiceRelationDO> {
    default List<ServiceRelationDO> selectByOwnerUserId(Long ownerUserId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getStatus, "active")
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }

    default List<ServiceRelationDO> selectByOwnerAndPerson(Long ownerUserId, Long personId) {
        return selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOwnerUserId, ownerUserId)
                .eq(ServiceRelationDO::getPersonId, personId)
                .orderByDesc(ServiceRelationDO::getActivatedAt));
    }
}
