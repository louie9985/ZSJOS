package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegistrationChecklistTemplateMapper extends BaseMapperX<RegistrationChecklistTemplateDO> {
    default RegistrationChecklistTemplateDO selectCurrent() {
        return selectOne(new LambdaQueryWrapperX<RegistrationChecklistTemplateDO>().orderByAsc(RegistrationChecklistTemplateDO::getId).last("LIMIT 1"));
    }

    @Select("SELECT * FROM zsjos_registration_checklist_template WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    RegistrationChecklistTemplateDO selectByIdForUpdate(Long id, Long tenantId);
}
