package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageMyCursorReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

/**
 * 站内信 Service 实现类
 *
 * @author xrcoder
 */
@Service
@Validated
public class NotifyMessageServiceImpl implements NotifyMessageService {

    @Resource
    private NotifyMessageMapper notifyMessageMapper;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public Long createNotifyMessage(Long userId, Integer userType,
                                    NotifyTemplateDO template, String templateContent, Map<String, Object> templateParams) {
        return createNotifyMessage(NotifyMessageCreateReqDTO.builder()
                .userId(userId).userType(userType).template(template)
                .title(notifyTitle(template)).summary(notifySummary(template, templateContent))
                .content(templateContent).templateParams(templateParams).build());
    }

    @Override
    public Long createNotifyMessage(NotifyMessageCreateReqDTO reqDTO) {
        NotifyTemplateDO template = reqDTO.getTemplate();
        NotifyMessageDO message = new NotifyMessageDO().setUserId(reqDTO.getUserId()).setUserType(reqDTO.getUserType())
                .setTemplateId(template.getId()).setTemplateCode(template.getCode())
                .setTemplateType(template.getType()).setTemplateNickname(template.getNickname())
                .setTemplateTitle(reqDTO.getTitle()).setTemplateSummary(reqDTO.getSummary())
                .setTemplateContent(reqDTO.getContent()).setTemplateParams(reqDTO.getTemplateParams())
                .setNotifyRuleId(reqDTO.getNotifyRuleId()).setSceneCode(reqDTO.getSceneCode())
                .setSourceEventKey(reqDTO.getSourceEventKey()).setActionType(reqDTO.getActionType())
                .setBizType(reqDTO.getBizType()).setBizId(reqDTO.getBizId()).setReadStatus(false);
        notifyMessageMapper.insert(message);
        applicationEventPublisher.publishEvent(new NotifyMessageCreatedEvent(message.getId(), reqDTO.getUserId(), reqDTO.getUserType()));
        return message.getId();
    }

    private String notifyTitle(NotifyTemplateDO template) {
        return template.getTitle() != null ? template.getTitle() : template.getName();
    }

    private String notifySummary(NotifyTemplateDO template, String content) {
        if (template.getSummary() != null) {
            return template.getSummary();
        }
        return content == null || content.length() <= 200 ? content : content.substring(0, 200);
    }

    @Override
    public PageResult<NotifyMessageDO> getNotifyMessagePage(NotifyMessagePageReqVO pageReqVO) {
        return notifyMessageMapper.selectPage(pageReqVO);
    }

    @Override
    public PageResult<NotifyMessageDO> getMyMyNotifyMessagePage(NotifyMessageMyPageReqVO pageReqVO, Long userId, Integer userType) {
        return notifyMessageMapper.selectPage(pageReqVO, userId, userType);
    }

    @Override
    public CursorPageResult<NotifyMessageDO> getMyNotifyMessageCursor(NotifyMessageMyCursorReqVO reqVO,
                                                                      Long userId, Integer userType) {
        NotifyCursor cursor = decodeCursor(reqVO.getCursor(), userId, userType, reqVO.getReadStatus());
        int limit = reqVO.getLimit() == null ? 20 : reqVO.getLimit();
        List<NotifyMessageDO> rows = notifyMessageMapper.selectCursorList(userId, userType, reqVO.getReadStatus(),
                reqVO.getCreateTime(), cursor == null ? null : cursor.createTime(),
                cursor == null ? null : cursor.id(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<NotifyMessageDO> list = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        String nextCursor = hasMore && !list.isEmpty()
                ? encodeCursor(list.get(list.size() - 1), userId, userType, reqVO.getReadStatus()) : null;
        return new CursorPageResult<>(list, nextCursor, hasMore);
    }

    private String encodeCursor(NotifyMessageDO message, Long userId, Integer userType, Boolean readStatus) {
        String value = message.getCreateTime() + "|" + message.getId() + "|" + userId + "|" + userType
                + "|" + Objects.toString(readStatus, "null");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private NotifyCursor decodeCursor(String value, Long userId, Integer userType, Boolean readStatus) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 5 || !Objects.equals(Long.valueOf(parts[2]), userId)
                    || !Objects.equals(Integer.valueOf(parts[3]), userType)
                    || !Objects.equals(parts[4], Objects.toString(readStatus, "null"))) {
                throw new IllegalArgumentException("cursor context mismatch");
            }
            return new NotifyCursor(LocalDateTime.parse(parts[0]), Long.valueOf(parts[1]));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid notify message cursor", ex);
        }
    }

    private record NotifyCursor(LocalDateTime createTime, Long id) {}

    @Override
    public NotifyMessageDO getNotifyMessage(Long id) {
        return notifyMessageMapper.selectById(id);
    }

    @Override
    public NotifyMessageDO getMyNotifyMessage(Long id, Long userId, Integer userType) {
        return notifyMessageMapper.selectOne(NotifyMessageDO::getId, id,
                NotifyMessageDO::getUserId, userId, NotifyMessageDO::getUserType, userType);
    }

    @Override
    public boolean existsByRuleUserAndEvent(Long ruleId, Long userId, Integer userType, String sourceEventKey) {
        return notifyMessageMapper.selectByRuleUserAndEvent(ruleId, userId, userType, sourceEventKey) != null;
    }

    @Override
    public List<NotifyMessageDO> getUnreadNotifyMessageList(Long userId, Integer userType, Integer size) {
        return notifyMessageMapper.selectUnreadListByUserIdAndUserType(userId, userType, size);
    }

    @Override
    public Long getUnreadNotifyMessageCount(Long userId, Integer userType) {
        return notifyMessageMapper.selectUnreadCountByUserIdAndUserType(userId, userType);
    }

    @Override
    public int updateNotifyMessageRead(Collection<Long> ids, Long userId, Integer userType) {
        return notifyMessageMapper.updateListRead(ids, userId, userType);
    }

    @Override
    public int updateAllNotifyMessageRead(Long userId, Integer userType) {
        return notifyMessageMapper.updateListRead(userId, userType);
    }

}
