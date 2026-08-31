package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseRouteDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RegistrationCaseRouteMapper extends BaseMapperX<RegistrationCaseRouteDO> {
    default List<RegistrationCaseRouteDO> selectByCaseId(Long caseId) {
        return selectList(new LambdaQueryWrapperX<RegistrationCaseRouteDO>()
                .eq(RegistrationCaseRouteDO::getRegistrationCaseId, caseId)
                .orderByAsc(RegistrationCaseRouteDO::getSort).orderByAsc(RegistrationCaseRouteDO::getId));
    }

    default List<RegistrationCaseRouteDO> selectSelectedByAssignee(Long userId) {
        return selectList(new LambdaQueryWrapperX<RegistrationCaseRouteDO>()
                .eq(RegistrationCaseRouteDO::getSelected, true)
                .eq(RegistrationCaseRouteDO::getAssigneeUserId, userId));
    }

    default List<RegistrationCaseRouteDO> selectSelectedByCaseIds(Collection<Long> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<RegistrationCaseRouteDO>()
                .in(RegistrationCaseRouteDO::getRegistrationCaseId, caseIds)
                .eq(RegistrationCaseRouteDO::getSelected, true));
    }
}
