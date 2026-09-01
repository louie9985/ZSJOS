package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.template.NotifyTemplatePageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.template.NotifyTemplateSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyTemplateMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.LinkedHashSet;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.NOTIFY_TEMPLATE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.NOTIFY_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.NOTIFY_TEMPLATE_PARAM_INVALID;
import cn.iocoder.yudao.module.system.api.notify.NotifyChannelType;

/**
 * 站内信模版 Service 实现类
 *
 * @author xrcoder
 */
@Service
@Validated
@Slf4j
public class NotifyTemplateServiceImpl implements NotifyTemplateService {

    /**
     * 正则表达式，匹配 {} 中的变量
     */
    private static final Pattern DOUBLE_BRACE_PARAMS = Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)}}");
    private static final Pattern LEGACY_PARAMS = Pattern.compile("(?<!\\{)\\{([a-zA-Z0-9_.-]+)}(?!})");

    @Resource
    private NotifyTemplateMapper notifyTemplateMapper;

    @Resource
    private NotifySceneRegistry notifySceneRegistry;

    @Override
    public Long createNotifyTemplate(NotifyTemplateSaveReqVO createReqVO) {
        // 校验站内信编码是否重复
        validateNotifyTemplateCodeDuplicate(null, createReqVO.getCode());

        // 插入
        NotifyTemplateDO notifyTemplate = BeanUtils.toBean(createReqVO, NotifyTemplateDO.class);
        normalizeChannel(notifyTemplate);
        notifyTemplate.setParams(parseTemplateParams(notifyTemplate.getTitle(), notifyTemplate.getSummary(),
                notifyTemplate.getContent()));
        validateSceneParams(notifyTemplate.getSceneCode(), notifyTemplate.getParams());
        notifyTemplateMapper.insert(notifyTemplate);
        return notifyTemplate.getId();
    }

    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.NOTIFY_TEMPLATE,
            allEntries = true) // allEntries 清空所有缓存，因为可能修改到 code 字段，不好清理
    public void updateNotifyTemplate(NotifyTemplateSaveReqVO updateReqVO) {
        // 校验存在
        validateNotifyTemplateExists(updateReqVO.getId());
        // 校验站内信编码是否重复
        validateNotifyTemplateCodeDuplicate(updateReqVO.getId(), updateReqVO.getCode());

        // 更新
        NotifyTemplateDO updateObj = BeanUtils.toBean(updateReqVO, NotifyTemplateDO.class);
        normalizeChannel(updateObj);
        updateObj.setParams(parseTemplateParams(updateObj.getTitle(), updateObj.getSummary(), updateObj.getContent()));
        validateSceneParams(updateObj.getSceneCode(), updateObj.getParams());
        notifyTemplateMapper.updateById(updateObj);
    }

    @VisibleForTesting
    public List<String> parseTemplateContentParams(String content) {
        return parseTemplateParams(content);
    }

    private void normalizeChannel(NotifyTemplateDO template) {
        if (template.getChannelCode() == null || template.getChannelCode().isBlank()) {
            template.setChannelCode(NotifyChannelType.IN_APP);
        }
        if (!NotifyChannelType.ALL.contains(template.getChannelCode())) {
            throw exception(NOTIFY_TEMPLATE_PARAM_INVALID, template.getChannelCode());
        }
        if (NotifyChannelType.SMS.equals(template.getChannelCode())
                && (template.getSmsTemplateId() == null || template.getSmsTemplateId().isBlank())) {
            throw exception(NOTIFY_TEMPLATE_PARAM_INVALID, "smsTemplateId");
        }
        if (NotifyChannelType.WECOM.equals(template.getChannelCode())
                && (template.getContent() == null || template.getContent().isBlank())) {
            throw exception(NOTIFY_TEMPLATE_PARAM_INVALID, "content");
        }
    }

    public List<String> parseTemplateParams(String... contents) {
        LinkedHashSet<String> params = new LinkedHashSet<>();
        for (String content : contents) {
            if (content == null) {
                continue;
            }
            collectParams(DOUBLE_BRACE_PARAMS, content, params);
            collectParams(LEGACY_PARAMS, content, params);
        }
        return List.copyOf(params);
    }

    private void collectParams(Pattern pattern, String content, LinkedHashSet<String> params) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
    }

    private void validateSceneParams(String sceneCode, List<String> params) {
        if (sceneCode == null || sceneCode.isBlank()) {
            return;
        }
        var scene = notifySceneRegistry.getScene(sceneCode);
        if (scene == null) {
            throw exception(cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.NOTIFY_SCENE_NOT_EXISTS, sceneCode);
        }
        for (String param : notifySceneRegistry.findInvalidTemplateParams(sceneCode, params)) {
                throw exception(cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.NOTIFY_TEMPLATE_PARAM_INVALID, param);
        }
    }

    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.NOTIFY_TEMPLATE,
            allEntries = true) // allEntries 清空所有缓存，因为 id 不是直接的缓存 code，不好清理
    public void deleteNotifyTemplate(Long id) {
        // 校验存在
        validateNotifyTemplateExists(id);
        // 删除
        notifyTemplateMapper.deleteById(id);
    }

    @Override
    @CacheEvict(cacheNames = RedisKeyConstants.NOTIFY_TEMPLATE,
            allEntries = true) // allEntries 清空所有缓存，因为 id 不是直接的缓存 code，不好清理
    public void deleteNotifyTemplateList(List<Long> ids) {
        notifyTemplateMapper.deleteByIds(ids);
    }

    private void validateNotifyTemplateExists(Long id) {
        if (notifyTemplateMapper.selectById(id) == null) {
            throw exception(NOTIFY_TEMPLATE_NOT_EXISTS);
        }
    }

    @Override
    public NotifyTemplateDO getNotifyTemplate(Long id) {
        return notifyTemplateMapper.selectById(id);
    }

    @Override
    @Cacheable(cacheNames = RedisKeyConstants.NOTIFY_TEMPLATE, key = "#code",
            unless = "#result == null")
    public NotifyTemplateDO getNotifyTemplateByCodeFromCache(String code) {
        return notifyTemplateMapper.selectByCode(code);
    }

    @Override
    public PageResult<NotifyTemplateDO> getNotifyTemplatePage(NotifyTemplatePageReqVO pageReqVO) {
        return notifyTemplateMapper.selectPage(pageReqVO);
    }

    @Override
    public List<NotifyTemplateDO> getNotifyTemplateListByStatus(Integer status) {
        return notifyTemplateMapper.selectListByStatus(status);
    }

    @VisibleForTesting
    void validateNotifyTemplateCodeDuplicate(Long id, String code) {
        NotifyTemplateDO template = notifyTemplateMapper.selectByCode(code);
        if (template == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的字典类型
        if (id == null) {
            throw exception(NOTIFY_TEMPLATE_CODE_DUPLICATE, code);
        }
        if (!template.getId().equals(id)) {
            throw exception(NOTIFY_TEMPLATE_CODE_DUPLICATE, code);
        }
    }

    /**
     * 格式化站内信内容
     *
     * @param content 站内信模板的内容
     * @param params  站内信内容的参数
     * @return 格式化后的内容
     */
    @Override
    public String formatNotifyTemplateContent(String content, Map<String, Object> params) {
        String rendered = replaceParams(content, params, DOUBLE_BRACE_PARAMS);
        return replaceParams(rendered, params, LEGACY_PARAMS);
    }

    private String replaceParams(String content, Map<String, Object> params, Pattern pattern) {
        if (content == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = params.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

}
