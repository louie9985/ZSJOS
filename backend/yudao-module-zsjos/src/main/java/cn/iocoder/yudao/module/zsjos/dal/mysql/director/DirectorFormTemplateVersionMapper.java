package cn.iocoder.yudao.module.zsjos.dal.mysql.director;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DirectorFormTemplateVersionMapper extends BaseMapperX<DirectorFormTemplateVersionDO> {
    default DirectorFormTemplateVersionDO selectDraft(Long templateId) {
        return selectOne(new LambdaQueryWrapperX<DirectorFormTemplateVersionDO>()
                .eq(DirectorFormTemplateVersionDO::getTemplateId, templateId)
                .eq(DirectorFormTemplateVersionDO::getStatus, "draft").last("LIMIT 1"));
    }
    default List<DirectorFormTemplateVersionDO> selectByTemplate(Long templateId) {
        return selectList(new LambdaQueryWrapperX<DirectorFormTemplateVersionDO>()
                .eq(DirectorFormTemplateVersionDO::getTemplateId, templateId)
                .orderByDesc(DirectorFormTemplateVersionDO::getVersionNo));
    }
}
