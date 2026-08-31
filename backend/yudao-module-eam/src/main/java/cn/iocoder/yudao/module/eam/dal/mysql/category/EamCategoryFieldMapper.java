package cn.iocoder.yudao.module.eam.dal.mysql.category;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EamCategoryFieldMapper extends BaseMapperX<EamCategoryFieldDO> {

    default List<EamCategoryFieldDO> selectListByCategoryId(Long categoryId) {
        return selectList(new LambdaQueryWrapperX<EamCategoryFieldDO>()
                .eq(EamCategoryFieldDO::getCategoryId, categoryId)
                .orderByAsc(EamCategoryFieldDO::getSort)
                .orderByAsc(EamCategoryFieldDO::getId));
    }

    default EamCategoryFieldDO selectByCategoryIdAndFieldKey(Long categoryId, String fieldKey) {
        return selectOne(new LambdaQueryWrapperX<EamCategoryFieldDO>()
                .eq(EamCategoryFieldDO::getCategoryId, categoryId)
                .eq(EamCategoryFieldDO::getFieldKey, fieldKey));
    }

    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(new LambdaQueryWrapperX<EamCategoryFieldDO>()
                .eq(EamCategoryFieldDO::getCategoryId, categoryId));
    }

}
