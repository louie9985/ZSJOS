package cn.iocoder.yudao.module.eam.dal.mysql.category;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamCategoryMapper extends BaseMapperX<EamCategoryDO> {

    default List<EamCategoryDO> selectList() {
        return selectList(new LambdaQueryWrapperX<EamCategoryDO>()
                .orderByAsc(EamCategoryDO::getSort)
                .orderByAsc(EamCategoryDO::getId));
    }

    default List<EamCategoryDO> selectListByParentId(Long parentId) {
        return selectList(new LambdaQueryWrapperX<EamCategoryDO>()
                .eq(EamCategoryDO::getParentId, parentId));
    }

    default EamCategoryDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapperX<EamCategoryDO>()
                .eq(EamCategoryDO::getCode, code));
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCount(new LambdaQueryWrapperX<EamCategoryDO>()
                .eq(EamCategoryDO::getParentId, parentId));
    }

}
