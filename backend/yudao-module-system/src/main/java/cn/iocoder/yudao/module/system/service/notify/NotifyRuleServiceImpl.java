package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyRulePageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule.NotifyRuleSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyRuleMapper;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;

@Service
@Validated
public class NotifyRuleServiceImpl implements NotifyRuleService {

    @Resource private NotifyRuleMapper notifyRuleMapper;
    @Resource private NotifyTemplateService notifyTemplateService;
    @Resource private NotifySceneRegistry sceneRegistry;
    @Resource private AdminUserService adminUserService;

    @Override
    public Long createNotifyRule(NotifyRuleSaveReqVO reqVO) {
        validateRule(reqVO);
        NotifyRuleDO rule = BeanUtils.toBean(reqVO, NotifyRuleDO.class);
        normalizeRecipients(rule);
        notifyRuleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    public void updateNotifyRule(NotifyRuleSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateRule(reqVO);
        NotifyRuleDO rule = BeanUtils.toBean(reqVO, NotifyRuleDO.class);
        normalizeRecipients(rule);
        notifyRuleMapper.updateById(rule);
    }

    @Override
    public void deleteNotifyRule(Long id) {
        validateExists(id);
        notifyRuleMapper.deleteById(id);
    }

    @Override
    public void updateNotifyRuleStatus(Long id, Integer status) {
        NotifyRuleDO existing = validateExists(id);
        if (CommonStatusEnum.ENABLE.getStatus().equals(status)) {
            validateRule(BeanUtils.toBean(existing, NotifyRuleSaveReqVO.class));
        }
        notifyRuleMapper.updateById(new NotifyRuleDO().setId(id).setStatus(status));
    }

    @Override
    public NotifyRuleDO getNotifyRule(Long id) {
        return notifyRuleMapper.selectById(id);
    }

    @Override
    public PageResult<NotifyRuleDO> getNotifyRulePage(NotifyRulePageReqVO reqVO) {
        return notifyRuleMapper.selectPage(reqVO);
    }

    @Override
    public List<NotifyRuleDO> getEnabledRules(String sceneCode) {
        return notifyRuleMapper.selectEnabledListBySceneCode(sceneCode, CommonStatusEnum.ENABLE.getStatus());
    }

    @Override
    public void initializeDefaultRules(List<NotifyDefaultRuleReqDTO> rules) {
        if (rules == null) return;
        for (NotifyDefaultRuleReqDTO seed : rules) {
            NotifyTemplateDO template = notifyTemplateService.getNotifyTemplateByCodeFromCache(seed.getTemplateCode());
            if (template == null) continue;
            boolean exists = notifyRuleMapper.selectCount(new LambdaQueryWrapper<NotifyRuleDO>()
                    .eq(NotifyRuleDO::getSceneCode, seed.getSceneCode())
                    .eq(NotifyRuleDO::getTemplateId, template.getId())
                    .eq(seed.getTimingStage() != null, NotifyRuleDO::getTimingStage, seed.getTimingStage())
                    .eq(seed.getTimingOffsetMinutes() != null, NotifyRuleDO::getTimingOffsetMinutes,
                            seed.getTimingOffsetMinutes())) > 0;
            if (exists) continue;
            NotifyRuleSaveReqVO reqVO = new NotifyRuleSaveReqVO();
            reqVO.setName(seed.getName()); reqVO.setSceneCode(seed.getSceneCode());
            reqVO.setChannelCode(NotifyChannelType.IN_APP); reqVO.setTemplateId(template.getId());
            reqVO.setRecipientRoles(seed.getRecipientRoles()); reqVO.setSpecifiedUserIds(List.of());
            reqVO.setActionType(seed.getActionType()); reqVO.setTimingStage(seed.getTimingStage());
            reqVO.setTimingOffsetMinutes(seed.getTimingOffsetMinutes());
            reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
            createNotifyRule(reqVO);
        }
    }

    private void validateRule(NotifyRuleSaveReqVO reqVO) {
        String channelCode = reqVO.getChannelCode();
        if (channelCode == null || channelCode.isBlank()) {
            reqVO.setChannelCode(NotifyChannelType.IN_APP);
            channelCode = NotifyChannelType.IN_APP;
        }
        if (!NotifyChannelType.ALL.contains(channelCode)) {
            throw exception(NOTIFY_RULE_ACTION_INVALID);
        }
        NotifySceneRespDTO scene = sceneRegistry.getScene(reqVO.getSceneCode());
        if (scene == null) {
            throw exception(NOTIFY_SCENE_NOT_EXISTS, reqVO.getSceneCode());
        }
        NotifyTemplateDO template = notifyTemplateService.getNotifyTemplate(reqVO.getTemplateId());
        if (template == null) {
            throw exception(NOTIFY_TEMPLATE_NOT_EXISTS);
        }
        if (!reqVO.getSceneCode().equals(template.getSceneCode())) {
            throw exception(NOTIFY_RULE_TEMPLATE_SCENE_MISMATCH);
        }
        boolean websocketUsesInAppTemplate = NotifyChannelType.WEBSOCKET.equals(channelCode)
                && NotifyChannelType.IN_APP.equals(template.getChannelCode());
        if (template.getChannelCode() != null && !channelCode.equals(template.getChannelCode())
                && !websocketUsesInAppTemplate) {
            throw exception(NOTIFY_RULE_TEMPLATE_SCENE_MISMATCH);
        }
        Set<String> allowedRoles = new LinkedHashSet<>();
        scene.getRecipientRoles().stream().map(NotifySceneRoleRespDTO::getCode).forEach(allowedRoles::add);
        if (reqVO.getRecipientRoles() != null && !allowedRoles.containsAll(reqVO.getRecipientRoles())) {
            throw exception(NOTIFY_RULE_RECIPIENT_ROLE_INVALID);
        }
        if (!NotifyActionType.ALL.contains(reqVO.getActionType())
                || !scene.getAllowedActions().contains(reqVO.getActionType())) {
            throw exception(NOTIFY_RULE_ACTION_INVALID);
        }
        if ((reqVO.getRecipientRoles() == null || reqVO.getRecipientRoles().isEmpty())
                && (reqVO.getSpecifiedUserIds() == null || reqVO.getSpecifiedUserIds().isEmpty())) {
            throw exception(NOTIFY_RULE_RECIPIENT_EMPTY);
        }
        if (reqVO.getSpecifiedUserIds() != null) {
            adminUserService.validateUserList(reqVO.getSpecifiedUserIds());
        }
        validateTiming(reqVO, scene);
    }

    private void validateTiming(NotifyRuleSaveReqVO reqVO, NotifySceneRespDTO scene) {
        if (!Boolean.TRUE.equals(scene.getTimed())) {
            reqVO.setTimingStage(null);
            reqVO.setTimingOffsetMinutes(null);
            return;
        }
        if (!Set.of("advance", "due", "overdue").contains(reqVO.getTimingStage())) {
            throw exception(NOTIFY_RULE_ACTION_INVALID);
        }
        int offset = reqVO.getTimingOffsetMinutes() == null ? 0 : reqVO.getTimingOffsetMinutes();
        if (offset < 0 || offset > 10080 || ("due".equals(reqVO.getTimingStage()) && offset != 0)) {
            throw exception(NOTIFY_RULE_ACTION_INVALID);
        }
        reqVO.setTimingOffsetMinutes(offset);
    }

    private void normalizeRecipients(NotifyRuleDO rule) {
        rule.setRecipientRoles(rule.getRecipientRoles() == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(rule.getRecipientRoles())));
        rule.setSpecifiedUserIds(rule.getSpecifiedUserIds() == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(rule.getSpecifiedUserIds())));
    }

    private NotifyRuleDO validateExists(Long id) {
        NotifyRuleDO rule = notifyRuleMapper.selectById(id);
        if (rule == null) {
            throw exception(NOTIFY_RULE_NOT_EXISTS);
        }
        return rule;
    }
}
