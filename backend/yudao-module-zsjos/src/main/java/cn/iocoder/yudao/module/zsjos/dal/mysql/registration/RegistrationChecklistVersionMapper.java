package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistVersionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegistrationChecklistVersionMapper extends BaseMapperX<RegistrationChecklistVersionDO> {
    default RegistrationChecklistVersionDO selectLatest(Long templateId) {
        return selectOne(new LambdaQueryWrapperX<RegistrationChecklistVersionDO>()
                .eq(RegistrationChecklistVersionDO::getTemplateId, templateId)
                .orderByDesc(RegistrationChecklistVersionDO::getVersionNo).last("LIMIT 1"));
    }
}
