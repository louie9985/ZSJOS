package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationCaseChecklistItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RegistrationCaseChecklistItemMapper extends BaseMapperX<RegistrationCaseChecklistItemDO> {
    default List<RegistrationCaseChecklistItemDO> selectByCaseId(Long caseId) {
        return selectList(new LambdaQueryWrapperX<RegistrationCaseChecklistItemDO>()
                .eq(RegistrationCaseChecklistItemDO::getRegistrationCaseId, caseId)
                .orderByAsc(RegistrationCaseChecklistItemDO::getSort)
                .orderByAsc(RegistrationCaseChecklistItemDO::getId));
    }
}
