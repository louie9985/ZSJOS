package cn.iocoder.yudao.module.system.dal.mysql.notify;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyRulePageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface NotifyRuleMapper extends BaseMapperX<NotifyRuleDO> {

    default PageResult<NotifyRuleDO> selectPage(NotifyRulePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<NotifyRuleDO>()
                .likeIfPresent(NotifyRuleDO::getName, reqVO.getName())
                .eqIfPresent(NotifyRuleDO::getSceneCode, reqVO.getSceneCode())
                .eqIfPresent(NotifyRuleDO::getStatus, reqVO.getStatus())
                .orderByDesc(NotifyRuleDO::getId));
    }

    default List<NotifyRuleDO> selectEnabledListBySceneCode(String sceneCode, Integer status) {
        return selectList(new LambdaQueryWrapperX<NotifyRuleDO>()
                .eq(NotifyRuleDO::getSceneCode, sceneCode)
                .eq(NotifyRuleDO::getStatus, status)
                .orderByAsc(NotifyRuleDO::getId));
    }
}
