package cn.iocoder.yudao.module.system.dal.mysql.notify;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageCategory;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface NotifyMessageMapper extends BaseMapperX<NotifyMessageDO> {
    // Select only proof of persistence, never message text or business variables.
    @TenantIgnore
    @org.apache.ibatis.annotations.Select("""
            <script>
            SELECT id,user_id,user_type,notify_rule_id,scene_code,source_event_key,create_time
            FROM system_notify_message
            WHERE tenant_id=#{tenantId} AND scene_code=#{scene} AND source_event_key=#{eventKey}
              AND notify_rule_id=#{ruleId} AND deleted=0 AND
            <foreach collection="recipients" item="recipient" open="(" separator=" OR " close=")">
              (user_id=#{recipient.userId} AND user_type=#{recipient.userType})
            </foreach>
            </script>
            """)
    List<NotifyMessageDO> selectDeliveryEvidence(@Param("tenantId") Long tenantId,
            @Param("scene") String scene, @Param("eventKey") String eventKey, @Param("ruleId") Long ruleId,
            @Param("recipients") List<cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO> recipients);

    @TenantIgnore
    @Delete("DELETE FROM system_notify_message WHERE create_time < #{before}")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);

    default PageResult<NotifyMessageDO> selectPage(NotifyMessagePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<NotifyMessageDO>()
                .eqIfPresent(NotifyMessageDO::getUserId, reqVO.getUserId())
                .eqIfPresent(NotifyMessageDO::getUserType, reqVO.getUserType())
                .likeIfPresent(NotifyMessageDO::getTemplateCode, reqVO.getTemplateCode())
                .eqIfPresent(NotifyMessageDO::getTemplateType, reqVO.getTemplateType())
                .betweenIfPresent(NotifyMessageDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(NotifyMessageDO::getCreateTime)
                .orderByDesc(NotifyMessageDO::getId));
    }

    default PageResult<NotifyMessageDO> selectPage(NotifyMessageMyPageReqVO reqVO, Long userId, Integer userType) {
        return selectPage(reqVO, new LambdaQueryWrapperX<NotifyMessageDO>()
                .eqIfPresent(NotifyMessageDO::getReadStatus, reqVO.getReadStatus())
                .eqIfPresent(NotifyMessageDO::getBizType, reqVO.getBizType())
                .betweenIfPresent(NotifyMessageDO::getCreateTime, reqVO.getCreateTime())
                .and(reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank(), w -> w
                        .like(NotifyMessageDO::getTemplateTitle, reqVO.getKeyword())
                        .or().like(NotifyMessageDO::getTemplateSummary, reqVO.getKeyword())
                        .or().like(NotifyMessageDO::getTemplateContent, reqVO.getKeyword())
                        .or().like(NotifyMessageDO::getTemplateNickname, reqVO.getKeyword()))
                .apply(NotifyMessageCategory.condition(reqVO.getCategory()))
                .eq(NotifyMessageDO::getUserId, userId)
                .eq(NotifyMessageDO::getUserType, userType)
                .orderByDesc(NotifyMessageDO::getCreateTime)
                .orderByDesc(NotifyMessageDO::getId));
    }

    default List<NotifyMessageDO> selectCursorList(Long userId, Integer userType, Boolean readStatus,
                                                    LocalDateTime[] createTime, String keyword, String category,
                                                    String bizType,
                                                    LocalDateTime cursorCreateTime,
                                                    Long cursorId, int limit) {
        LambdaQueryWrapperX<NotifyMessageDO> query = new LambdaQueryWrapperX<NotifyMessageDO>()
                .eqIfPresent(NotifyMessageDO::getReadStatus, readStatus)
                .betweenIfPresent(NotifyMessageDO::getCreateTime, createTime)
                .eq(NotifyMessageDO::getUserId, userId)
                .eq(NotifyMessageDO::getUserType, userType);
        query.eqIfPresent(NotifyMessageDO::getBizType, bizType);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(NotifyMessageDO::getTemplateTitle, keyword)
                    .or().like(NotifyMessageDO::getTemplateSummary, keyword)
                    .or().like(NotifyMessageDO::getTemplateContent, keyword)
                    .or().like(NotifyMessageDO::getTemplateNickname, keyword));
        }
        if (category != null && !category.isBlank()) {
            query.apply(NotifyMessageCategory.condition(category));
        }
        if (cursorCreateTime != null && cursorId != null) {
            query.and(wrapper -> wrapper.lt(NotifyMessageDO::getCreateTime, cursorCreateTime)
                    .or(nested -> nested.eq(NotifyMessageDO::getCreateTime, cursorCreateTime)
                            .lt(NotifyMessageDO::getId, cursorId)));
        }
        return selectList(query.orderByDesc(NotifyMessageDO::getCreateTime)
                .orderByDesc(NotifyMessageDO::getId).last("LIMIT " + limit));
    }

    default int updateListRead(Collection<Long> ids, Long userId, Integer userType) {
        return update(new NotifyMessageDO().setReadStatus(true).setReadTime(LocalDateTime.now()),
                new LambdaQueryWrapperX<NotifyMessageDO>()
                        .in(NotifyMessageDO::getId, ids)
                        .eq(NotifyMessageDO::getUserId, userId)
                        .eq(NotifyMessageDO::getUserType, userType)
                        .eq(NotifyMessageDO::getReadStatus, false));
    }

    default int updateListRead(Long userId, Integer userType) {
        return update(new NotifyMessageDO().setReadStatus(true).setReadTime(LocalDateTime.now()),
                new LambdaQueryWrapperX<NotifyMessageDO>()
                        .eq(NotifyMessageDO::getUserId, userId)
                        .eq(NotifyMessageDO::getUserType, userType)
                        .eq(NotifyMessageDO::getReadStatus, false));
    }

    default List<NotifyMessageDO> selectUnreadListByUserIdAndUserType(Long userId, Integer userType, Integer size) {
        return selectList(new QueryWrapperX<NotifyMessageDO>() // 由于要使用 limitN 语句，所以只能用 QueryWrapperX
                .eq("user_id", userId)
                .eq("user_type", userType)
                .eq("read_status", false)
                .orderByDesc("create_time").orderByDesc("id").limitN(size));
    }

    default Long selectUnreadCountByUserIdAndUserType(Long userId, Integer userType) {
        return selectCount(new LambdaQueryWrapperX<NotifyMessageDO>()
                .eq(NotifyMessageDO::getReadStatus, false)
                .eq(NotifyMessageDO::getUserId, userId)
                .eq(NotifyMessageDO::getUserType, userType));
    }

    default NotifyMessageDO selectByRuleUserAndEvent(Long ruleId, Long userId, Integer userType, String sourceEventKey) {
        return selectOne(new LambdaQueryWrapperX<NotifyMessageDO>()
                .eq(NotifyMessageDO::getNotifyRuleId, ruleId)
                .eq(NotifyMessageDO::getUserId, userId).eq(NotifyMessageDO::getUserType, userType)
                .eq(NotifyMessageDO::getSourceEventKey, sourceEventKey));
    }

}
