package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyRulePageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyRuleSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;

import java.util.List;

public interface NotifyRuleService {

    Long createNotifyRule(NotifyRuleSaveReqVO reqVO);
    void updateNotifyRule(NotifyRuleSaveReqVO reqVO);
    void deleteNotifyRule(Long id);
    void updateNotifyRuleStatus(Long id, Integer status);
    NotifyRuleDO getNotifyRule(Long id);
    PageResult<NotifyRuleDO> getNotifyRulePage(NotifyRulePageReqVO reqVO);
    List<NotifyRuleDO> getEnabledRules(String sceneCode);
}
