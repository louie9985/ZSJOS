package cn.iocoder.yudao.module.zsjos.dal.mysql.advancedfilter;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.advancedfilter.AdvancedFilterTemplateDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AdvancedFilterTemplateMapper extends BaseMapperX<AdvancedFilterTemplateDO> {

    default List<AdvancedFilterTemplateDO> selectVisibleList(String scene, String pageKey, Long userId) {
        return selectList(new LambdaQueryWrapperX<AdvancedFilterTemplateDO>()
                .eq(AdvancedFilterTemplateDO::getScene, scene)
                .eq(AdvancedFilterTemplateDO::getPageKey, pageKey)
                .eq(AdvancedFilterTemplateDO::getEnabled, true)
                .and(q -> q.eq(AdvancedFilterTemplateDO::getScope, "system")
                        .or(personal -> personal.eq(AdvancedFilterTemplateDO::getScope, "personal")
                                .eq(AdvancedFilterTemplateDO::getOwnerUserId, userId)))
                .orderByDesc(AdvancedFilterTemplateDO::getScope)
                .orderByDesc(AdvancedFilterTemplateDO::getDefaultTemplate)
                .orderByAsc(AdvancedFilterTemplateDO::getSort)
                .orderByAsc(AdvancedFilterTemplateDO::getId));
    }

    default List<AdvancedFilterTemplateDO> selectSystemList(String scene, String pageKey) {
        return selectList(new LambdaQueryWrapperX<AdvancedFilterTemplateDO>()
                .eq(AdvancedFilterTemplateDO::getScene, scene)
                .eq(AdvancedFilterTemplateDO::getPageKey, pageKey)
                .eq(AdvancedFilterTemplateDO::getScope, "system")
                .orderByAsc(AdvancedFilterTemplateDO::getSort)
                .orderByAsc(AdvancedFilterTemplateDO::getId));
    }

    default int clearDefault(String scene, String pageKey, String scope, Long ownerUserId, Long exceptId) {
        return update(null, new LambdaUpdateWrapper<AdvancedFilterTemplateDO>()
                .eq(AdvancedFilterTemplateDO::getScene, scene)
                .eq(AdvancedFilterTemplateDO::getPageKey, pageKey)
                .eq(AdvancedFilterTemplateDO::getScope, scope)
                .eq(ownerUserId != null, AdvancedFilterTemplateDO::getOwnerUserId, ownerUserId)
                .ne(exceptId != null, AdvancedFilterTemplateDO::getId, exceptId)
                .set(AdvancedFilterTemplateDO::getDefaultTemplate, false));
    }
}
