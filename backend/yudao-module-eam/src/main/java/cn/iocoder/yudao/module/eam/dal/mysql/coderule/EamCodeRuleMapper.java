package cn.iocoder.yudao.module.eam.dal.mysql.coderule;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.eam.dal.dataobject.coderule.EamCodeRuleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EamCodeRuleMapper extends BaseMapperX<EamCodeRuleDO> {

    /**
     * 根据分类 ID 获取编号规则；若该分类无规则则取全局规则（category_id IS NULL）
     */
    default EamCodeRuleDO selectByCategoryId(Long categoryId) {
        EamCodeRuleDO rule = selectOne(new LambdaQueryWrapperX<EamCodeRuleDO>()
                .eq(EamCodeRuleDO::getCategoryId, categoryId));
        if (rule != null) {
            return rule;
        }
        return selectOne(new LambdaQueryWrapperX<EamCodeRuleDO>()
                .isNull(EamCodeRuleDO::getCategoryId));
    }

    /**
     * 悲观锁获取并自增流水号（同事务内使用）
     */
    @Select("SELECT current_serial FROM eam_code_rule WHERE id = #{id} FOR UPDATE")
    Long selectCurrentSerialForUpdate(@Param("id") Long id);

    @Update("UPDATE eam_code_rule SET current_serial = #{serial} WHERE id = #{id}")
    int updateCurrentSerial(@Param("id") Long id, @Param("serial") Long serial);

}
