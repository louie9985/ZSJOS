package cn.iocoder.yudao.module.zsjos.dal.mysql.director;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DirectorFormTemplateMapper extends BaseMapperX<DirectorFormTemplateDO> {
    default List<DirectorFormTemplateDO> selectByScene(String scene) {
        return selectList(new LambdaQueryWrapperX<DirectorFormTemplateDO>().eq(DirectorFormTemplateDO::getScene, scene)
                .orderByDesc(DirectorFormTemplateDO::getDefaultTemplate).orderByAsc(DirectorFormTemplateDO::getId));
    }
    default DirectorFormTemplateDO selectDefault(String scene) {
        return selectOne(new LambdaQueryWrapperX<DirectorFormTemplateDO>().eq(DirectorFormTemplateDO::getScene, scene)
                .eq(DirectorFormTemplateDO::getDefaultTemplate, true).eq(DirectorFormTemplateDO::getStatus, "enabled")
                .last("LIMIT 1"));
    }
}
