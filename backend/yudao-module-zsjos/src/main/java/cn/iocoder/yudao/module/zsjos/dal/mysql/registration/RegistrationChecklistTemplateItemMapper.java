package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistTemplateItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RegistrationChecklistTemplateItemMapper extends BaseMapperX<RegistrationChecklistTemplateItemDO> {
    default List<RegistrationChecklistTemplateItemDO> selectByVersionId(Long versionId) {
        return selectList(new LambdaQueryWrapperX<RegistrationChecklistTemplateItemDO>()
                .eq(RegistrationChecklistTemplateItemDO::getVersionId, versionId)
                .orderByAsc(RegistrationChecklistTemplateItemDO::getSort)
                .orderByAsc(RegistrationChecklistTemplateItemDO::getId));
    }

    default void deleteByVersionId(Long versionId) {
        delete(new LambdaQueryWrapperX<RegistrationChecklistTemplateItemDO>()
                .eq(RegistrationChecklistTemplateItemDO::getVersionId, versionId));
    }
}
